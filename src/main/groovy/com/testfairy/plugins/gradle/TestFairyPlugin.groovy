package com.testfairy.plugins.gradle

import com.testfairy.uploader.*

import org.gradle.api.*
import java.util.zip.*
import org.apache.http.*
import org.apache.http.auth.*
import org.apache.http.impl.client.*
import org.apache.http.client.methods.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import org.apache.http.util.EntityUtils
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.commons.io.IOUtils
import org.apache.commons.io.FilenameUtils
import groovy.json.JsonSlurper

class TestFairyPlugin implements Plugin<Project> {

	private String apiKey

	/// path to Java's jarsigner
	private String jarSignerPath

	/// path to zipalign
	private String zipAlignPath

	/// path to aapt
	private String zipPath

	private boolean isWindows() {
		return System.properties['os.name'].toLowerCase().contains('windows')
	}

	private void configureJavaTools(Project project) {

		String sdkDirectory = getSdkDirectory()
		SdkEnvironment env = new SdkEnvironment(sdkDirectory)

		jarSignerPath = env.locateJarsigner()
		if (jarSignerPath == null) {
			throw new GradleException("Could not locate jarsigner, please update java.home property")
		}

		zipAlignPath = env.locateZipalign()
		if (zipAlignPath == null) {
			throw new GradleException("Could not locate zipalign, please validate 'buildToolsVersion' settings")
		}

		zipPath = locateZip(project)
	}

	/**
	 * Locates zip tool on disk.
	 *
	 * @param project
	 * @return
	 */
	private String locateZip(Project project) {
		try {
			def command = ["zip", "-h"]
			def proc = command.execute()
			proc.consumeProcessOutput()
			proc.waitFor()
			if (proc.exitValue() == 0) {
				project.logger.debug("zip was found in path")
				return "zip"
			}
		} catch (IOException ignored) {
			// zip not in path
		}

		throw new GradleException("Could not find 'zip' in path, please configure and run again")
	}

	private String getSdkDirectory(Project project) {
		def sdkDir

		Properties properties = new Properties()
		File localProps = project.rootProject.file('local.properties')
		if (localProps.exists()) {
			properties.load(localProps.newDataInputStream())
			sdkDir = properties.getProperty('sdk.dir')
		} else {
			sdkDir = System.getenv('ANDROID_HOME')
		}

		if (!sdkDir) {
			throw new ProjectConfigurationException("Cannot find android sdk. Make sure sdk.dir is defined in local.properties or the environment variable ANDROID_HOME is set.", null)
		}

		return sdkDir.toString()
	}

	@Override
	void apply(Project project) {

		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		// configure java tools before even starting
		configureJavaTools(project)
		project.logger.debug("Located zipalign at ${zipAlignPath}")
		project.logger.debug("Located jarsigner at ${jarSignerPath}")
		project.logger.debug("Located zip at ${zipPath}")

		project.configure(project) {
			if (it.hasProperty("android")) {

				tasks.whenTaskAdded { task ->

					project.("android").applicationVariants.all { variant ->

						// locate packageRelease and packageDebug tasks
						def expectingTask = "package${variant.name.capitalize()}".toString()
						if (expectingTask.equals(task.name)) {

							def variantName = variant.name

							// create new task with name such as testfairyRelease and testfairyDebug
							def newTaskName = "testfairy${variantName.capitalize()}"

							project.task(newTaskName) << {

								assertValidApiKey(extension)

								String apiKey = extension.getApiKey()
								String serverEndpoint = extension.getServerEndpoint()

								// use outputFile from packageApp task
								String apkFilename = task.outputFile.toString()
								project.logger.info("Instrumenting ${apkFilename} using apiKey ${apiKey} and server ${serverEndpoint}")

								def tempDir = task.temporaryDir.getAbsolutePath()
								project.logger.debug("Saving temporary files to ${tempDir}")

								String proguardMappingFilename = null
								if (isMinifyEnabledCompat(variant.buildType) && extension.uploadProguardMapping) {
									// proguard-mapping.txt upload is enabled

									if (variant.metaClass.respondsTo(variant, "getMappingFile")) {
										// getMappingFile was added in Android Plugin 0.13
										proguardMappingFilename = variant.getMappingFile().toString()
									} else {
										// fallback to getProcessResources
										proguardMappingFilename = new File(variant.getProcessResources().getProguardOutputFile().parent, 'mapping.txt').absolutePath.toString()
									}

									project.logger.debug("Using proguard mapping file at ${proguardMappingFilename}")
								}

								def json = uploadApk(project, extension, apkFilename, proguardMappingFilename)
								if (variant.isSigningReady() && isApkSigned(apkFilename)) {
									// apk was previously signed, so we will sign it again
									project.logger.debug("Signing is ready, and APK was previously signed")

									// first, we need to download the instrumented apk
									String instrumentedUrl = json.instrumented_url.toString()
									project.logger.info("Downloading instrumented APK from ${instrumentedUrl}")

									// add API_KEY to download url, needed only in case of Strict Mode
									instrumentedUrl = instrumentedUrl + "?api_key=" + apiKey
									project.logger.debug("Added api_key to download url, and is now ${instrumentedUrl}")

									String baseName = FilenameUtils.getBaseName(apkFilename)
									String tempFilename = FilenameUtils.normalize("${tempDir}/testfairy-${baseName}.apk".toString());
									project.logger.debug("Downloading instrumented APK onto ${tempFilename}")
									downloadFile(instrumentedUrl, tempFilename)

									// resign using gradle build settings
									resignApk(tempFilename, variant.signingConfig)

									// upload the signed apk file back to testfairy
									json = uploadSignedApk(extension, tempFilename)
									(new File(tempFilename)).delete()
								}

								println ""
								println "Successfully uploaded to TestFairy, build is available at:"
								println json.build_url
							}

							project.(newTaskName.toString()).dependsOn(expectingTask)
							project.(newTaskName.toString()).group = "TestFairy"
							project.(newTaskName.toString()).description = "Uploads the ${variantName.capitalize()} build to TestFairy"
						}
					}
				}
			}
		}
	}

	/**
	 * Make sure ApiKey is configured and not empty.
	 *
	 * @param extension
	 */
	private void assertValidApiKey(extension) {
		if (extension.getApiKey() == null || extension.getApiKey().equals("")) {
			throw new GradleException("Please configure your TestFairy apiKey before building")
		}
	}

	/**
	 * Returns true if code minification is enabled for this build type.
	 * Added to work around runProguard property being renamed to isMinifyEnabled in Android Gradle Plugin 0.14.0
	 */
	private boolean isMinifyEnabledCompat(buildType) {
		if (buildType.respondsTo("isMinifyEnabled")) {
			return buildType.isMinifyEnabled()
		} else {
			return buildType.runProguard
		}
	}

	/**
	 * Get a list of all files inside this APK file.
	 *
	 * @param apkFilename
	 * @return List<String>
	 */
	private List<String> getApkFiles(String apkFilename) {
		List<String> files = new ArrayList<String>()

		ZipFile zf = new ZipFile(apkFilename)
		Enumeration<? extends ZipEntry> e = zf.entries()
		while (e.hasMoreElements()) {
			ZipEntry entry = e.nextElement()
			String entryName = entry.getName()
			files.add(entryName)
		}

		zf.close()
		return files
	}

	/**
	 * Returns only the files under META-INF from APK.
	 *
	 * @param apkFilename
	 * @return List<String>
	 */
	private List<String> getApkMetaFiles(String apkFilename) {
		List<String> allFiles = getApkFiles(apkFilename)
		List<String> metaFiles = new ArrayList<String>()
		for (String filename: allFiles) {
			if (filename.startsWith("META-INF/")) {
				metaFiles.add(filename)
			}
		}

		return metaFiles
	}

	/**
	 * Checks if the given APK is signed
	 *
	 * @param apkFilename
	 * @return boolean
	 */
	private boolean isApkSigned(String apkFilename) {

		List<String> filenames = getApkFiles(apkFilename)
		for (String f: filenames) {
			if (f.startsWith("META-INF/") && f.endsWith("SF")) {
				// found a signature file, this APK is signed
				return true
			}
		}

		return false
	}

	private DefaultHttpClient buildHttpClient() {
		DefaultHttpClient httpClient = new DefaultHttpClient()

		// configure proxy (patched by timothy-volvo, https://github.com/timothy-volvo/testfairy-gradle-plugin)
		def proxyHost = System.getProperty("http.proxyHost")
		if (proxyHost != null) {
			def proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"))
			HttpHost proxy = new HttpHost(proxyHost, proxyPort)
			def proxyUser = System.getProperty("http.proxyUser")
			if (proxyUser != null) {
				AuthScope authScope = new AuthScope(proxyUser, proxyPort)
				Credentials credentials = new UsernamePasswordCredentials(proxyUser, System.getProperty("http.proxyPassword"))
				httpClient.getCredentialsProvider().setCredentials(authScope, credentials)
			}

			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		}

		return httpClient;
	}

	private Object post(String url, MultipartEntity entity) {
		DefaultHttpClient httpClient = new DefaultHttpClient()
		HttpPost post = new HttpPost(url)
		post.addHeader("User-Agent", "TestFairy Gradle Plugin")
		post.setEntity(entity)
		HttpResponse response = httpClient.execute(post)

		String json = EntityUtils.toString(response.getEntity())
		def parser = new JsonSlurper()
		def parsed = parser.parseText(json)
		if (!parsed.status.equals("ok")) {
			throw new GradleException("Failed with json: " + json)
		}

		return parsed
	}

	/**
	 * Downloads the entire page at a remote location, onto a local file.
	 *
	 * @param url
	 * @param localFilename
	 */
	private void downloadFile(String url, String localFilename) {
		DefaultHttpClient httpClient = new DefaultHttpClient()
		HttpGet httpget = new HttpGet(url)
		HttpResponse response = httpClient.execute(httpget)
		HttpEntity entity = response.getEntity()

		FileOutputStream fis = new FileOutputStream(localFilename);
		IOUtils.copy(entity.getContent(), fis)
		fis.close();
	}

	/**
	 * Upload an APK using /api/upload REST service.
	 *
	 * @param project
	 * @param extension
	 * @param apkFilename
	 * @return Object parsed json
	 */
	private Object uploadApk(Project project, TestFairyExtension extension, String apkFilename, String mappingFilename) {
		String serverEndpoint = extension.getServerEndpoint()
		String url = "${serverEndpoint}/api/upload"
		MultipartEntity entity = buildEntity(extension, apkFilename, mappingFilename)

		if (project.hasProperty("testfairyChangelog")) {
			// optional: testfairyChangelog, as passed through -P
			String changelog = project.property("testfairyChangelog")
			entity.addPart('changelog', new StringBody(changelog))
		}

		return post(url, entity)
	}

	/**
	 * Upload a signed APK using /api/upload-signed REST service.
	 *
	 * @param extension
	 * @param apkFilename
	 * @return Object parsed json
	 */
	private Object uploadSignedApk(TestFairyExtension extension, String apkFilename) {
		String serverEndpoint = extension.getServerEndpoint()
		String url = "${serverEndpoint}/api/upload-signed"

		MultipartEntity entity = new MultipartEntity()
		entity.addPart('api_key', new StringBody(extension.getApiKey()))
		entity.addPart('apk_file', new FileBody(new File(apkFilename)))

		if (extension.getTestersGroups()) {
			// if omitted, no emails will be sent to testers
			entity.addPart('testers-groups', new StringBody(extension.getTestersGroups()))
		}

		// add notify "on" or "off"
		entity.addPart('notify', new StringBody(extension.getNotify() ? "on" : "off"))

		// add auto-update "on" or "off"
		entity.addPart('auto-update', new StringBody(extension.getAutoUpdate() ? "on" : "off"))

		return post(url, entity)
	}

	/**
	 * Build MultipartEntity for API parameters on Upload of an APK
	 *
	 * @param extension
	 * @return MultipartEntity
	 */
	private MultipartEntity buildEntity(TestFairyExtension extension, String apkFilename, String mappingFilename) {
		String apiKey = extension.getApiKey()

		MultipartEntity entity = new MultipartEntity()
		entity.addPart('api_key', new StringBody(apiKey))
		entity.addPart('apk_file', new FileBody(new File(apkFilename)))

		if (mappingFilename != null) {
			entity.addPart('symbols_file', new FileBody(new File(mappingFilename)))
		}

		if (extension.getIconWatermark()) {
			// if omitted, default value is "off"
			entity.addPart('icon-watermark', new StringBody("on"))
		}

		if (extension.getVideo()) {
			// if omitted, default value is "on"
			entity.addPart('video', new StringBody(extension.getVideo()))
		}

		if (extension.getVideoQuality()) {
			// if omitted, default value is "high"
			entity.addPart('video-quality', new StringBody(extension.getVideoQuality()))
		}

		if (extension.getVideoRate()) {
			// if omitted, default is 1 frame per second (videoRate = 1.0)
			entity.addPart('video-rate', new StringBody(extension.getVideoRate()))
		}

		if (extension.getMetrics()) {
			// if omitted, by default will record as much as possible
			entity.addPart('metrics', new StringBody(extension.getMetrics()))
		}

		if (extension.getMaxDuration()) {
			// override default value
			entity.addPart('max-duration', new StringBody(extension.getMaxDuration()))
		}

		if (extension.getRecordOnBackground()) {
			// enable record on background option
			entity.addPart('record-on-background', new StringBody("on"));
		}

		return entity
	}

	/**
	 * Remove all signature files from archive, turning it back to unsigned.
	 *
	 * @param apkFilename
	 */
	void removeSignature(String apkFilename) {
		def metaFilenames = getApkMetaFiles(apkFilename)
		def command = ([zipPath, "-qd", apkFilename] << metaFilenames).flatten()
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
	}

	/**
	 * Remove previous signature and sign archive again.
	 *
	 * @param apkFilename
	 * @param sc
	 */
	void resignApk(String apkFilename, sc) {
		removeSignature(apkFilename)
		signApkFile(apkFilename, sc)
		validateApkSignature(apkFilename)
	}

	/**
	 * Sign an APK file with the given signingConfig settings.
	 *
	 * @param apkFilename
	 * @param sc
	 */
	void signApkFile(String apkFilename, sc) {
		def command = [jarSignerPath, "-keystore", sc.storeFile, "-storepass", sc.storePassword, "-keypass", sc.keyPassword, "-digestalg", "SHA1", "-sigalg", "MD5withRSA", apkFilename, sc.keyAlias]
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
		if (proc.exitValue()) {
			throw new GradleException("Could not jarsign ${apkFilename}, used this command:\n${command}")
		}

	}

	/**
	 * Zipaligns input APK file onto outFilename.
	 *
	 * @param inFilename
	 * @param outFilename
	 */
	void zipAlignFile(String inFilename, String outFilename) {
		def command = [zipAlignPath, "-f", "4", inFilename, outFilename];
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
		if (proc.exitValue()) {
			throw new GradleException("Could not zipalign ${inFilename} onto ${outFilename}")
		}
	}

	/**
	 * Verifies that APK is signed properly. Will throw an exception
	 * if not.
	 *
	 * @param apkFilename
	 */
	void validateApkSignature(String apkFilename) {
		def command = [jarSignerPath, "-verify", apkFilename]
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
		if (proc.exitValue()) {
			throw new GradleException("Could not jarsign ${apkFilename}, used this command:\n${command}")
		}
	}
}

