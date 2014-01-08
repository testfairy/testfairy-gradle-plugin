package com.testfairy.plugins.gradle

import org.gradle.api.*
import org.gradle.api.tasks.*
import groovyx.net.http.*
import org.apache.http.*
import org.apache.http.impl.client.*
import org.apache.http.client.methods.*
import org.apache.http.entity.mime.*
import org.apache.http.entity.mime.content.*
import org.apache.http.util.EntityUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.io.FilenameUtils
import groovy.json.JsonSlurper

//import com.testfairy.plugins.common.

class TestFairyPlugin implements Plugin<Project> {

	static final String CONTENT_TYPE_MULTIPART = 'multipart/form-data'

	private String apiKey

	@Override
	void apply(Project project) {

		// where android sdk is located
		def sdkDirectory = project.plugins.getPlugin("android").getSdkDirectory()

		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		project.configure(project) {
			if (it.hasProperty("android")) {

				tasks.whenTaskAdded { task ->

					project.("android").buildTypes.all { build ->

						// locate packageRelease and packageDebug tasks
						def expectingTask = "package${build.name.capitalize()}".toString()
						if (expectingTask.equals(task.name)) {

							def buildName = build.name

							// create new task with name such as testfairyRelease and testfairyDebug
							def newTaskName = "testfairy${buildName.capitalize()}"

							project.task(newTaskName) << {

								assertValidApiKey(extension)

								String apiKey = extension.getApiKey()
								String serverEndpoint = extension.getServerEndpoint()

								// use outputFile from packageApp task
								String apkFilename = task.outputFile.toString()
								project.logger.info("Instrumenting ${apkFilename} using apiKey ${apiKey} and server ${serverEndpoint}")

								// check if there's a signingConfig for this type
								def isSigned = (android.hasProperty('signingConfigs') && android.signingConfigs.hasProperty(buildName))

								def json = uploadApk(serverEndpoint, apiKey, apkFilename)
								if (isSigned) {
									// apk was previously signed, so we will sign it again

									// first, we need to download the instrumented apk
									project.logger.info("Downloading instrumented APK from ${json.instrumented_url}")

									String baseName = FilenameUtils.getBaseName(apkFilename)
									def tempFilename = "/tmp/testfairy-${baseName}.apk"
									downloadFile(json.instrumented_url.toString(), tempFilename.toString())

									// resign using gradle build settings
									def sc = android.signingConfigs[buildName]
									resignApk(tempFilename, sc)

									// upload the signed apk file back to testfairy
									json = uploadSignedApk(serverEndpoint, apiKey, tempFilename)
									(new File(tempFilename)).delete()
								}

								println "Successfully uploaded to TestFairy, build is available at:"
								println json.build_url
							}

							project.(newTaskName.toString()).dependsOn(expectingTask)
							project.(newTaskName.toString()).group = "TestFairy"
						}
					}
				}
			}
		}
	}

	void assertValidApiKey(extension) {
		if (extension.getApiKey() == null || extension.getApiKey().equals("")) {
			throw new GradleException("Please configure your TestFairy apiKey before building")
		}
	}

	boolean isApkSigned(String apkFilename) {
		return true
	}

	Object post(String url, MultipartEntity entity) {
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
	void downloadFile(String url, String localFilename) {
		DefaultHttpClient httpClient = new DefaultHttpClient()
		HttpGet httpget = new HttpGet(url)
		HttpResponse response = httpClient.execute(httpget)
		HttpEntity entity = response.getEntity()
		IOUtils.copy(entity.getContent(), new FileOutputStream(localFilename))
	}

	/**
	 * Upload an APK using /api/upload REST service.
	 *
	 * @param serverEndpoint
	 * @param apiKey
	 * @param apkFilename
	 * @return Object parsed json
	 */
	Object uploadApk(String serverEndpoint, String apiKey, String apkFilename) {
		String url = "${serverEndpoint}/api/upload"
		MultipartEntity entity = new MultipartEntity()
		entity.addPart('api_key', new StringBody(apiKey))
		entity.addPart('apk_file', new FileBody(new File(apkFilename)))
		return post(url, entity)
	}

	/**
	 * Upload a signed APK using /api/upload-signed REST service.
	 *
	 * @param serverEndpoint
	 * @param apiKey
	 * @param apkFilename
	 * @return Object parsed json
	 */
	Object uploadSignedApk(String serverEndpoint, String apiKey, String apkFilename) {
		String url = "${serverEndpoint}/api/upload-signed"
		MultipartEntity entity = new MultipartEntity()
		entity.addPart('api_key', new StringBody(apiKey))
		entity.addPart('apk_file', new FileBody(new File(apkFilename)))
		return post(url, entity)
	}

	/**
	 * Remove all signature files from archive, turning it back to unsigned.
	 *
	 * @param apkFilename
	 */
	void removeSignature(String apkFilename) {
		def command = """zip -qd ${apkFilename} META-INF/\\*"""
		def proc = command.execute()
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
		def command = """jarsigner -keystore ${sc.storeFile} -storepass ${sc.storePassword} ${apkFilename} ${sc.keyAlias} -verbose"""
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
		if (proc.exitValue()) {
			throw new GradleException("Could not jarsign ${apkFilename}, used this command:\n${command}")
		}

	}

	void validateApkSignature(String apkFilename) {
		def command = """jarsigner -verify -verbose ${apkFilename}"""
		def proc = command.execute()
		proc.consumeProcessOutput()
		proc.waitFor()
		if (proc.exitValue()) {
			throw new GradleException("Could not jarsign ${apkFilename}, used this command:\n${command}")
		}
	}
}

