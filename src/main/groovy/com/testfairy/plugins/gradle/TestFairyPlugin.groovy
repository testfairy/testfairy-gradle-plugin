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
import org.apache.commons.compress.archivers.zip.*
import groovy.json.JsonSlurper

class TestFairyPlugin implements Plugin<Project> {

	private String apiKey

	@Override
	void apply(Project project) {

		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

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

									proguardMappingFilename = getMappingFileCompat(variant)
									project.logger.debug("Using proguard mapping file at ${proguardMappingFilename}")
								}

								def json = uploadApk(project, extension, apkFilename, proguardMappingFilename)

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
	 *
	 * @param buildType
	 * @return boolean
	 */
	private boolean isMinifyEnabledCompat(buildType) {
		if (buildType.respondsTo("isMinifyEnabled")) {
			return buildType.isMinifyEnabled()
		} else {
			return buildType.runProguard
		}
	}

	private String getMappingFileCompat(variant) {

		if (variant.metaClass.respondsTo(variant, "getMappingFile")) {
			// getMappingFile was added in Android Plugin 0.13
			return variant.getMappingFile().toString()
		}

		// fallback to getProcessResources
		File f = new File(variant.processResources.proguardOutputFile.parent, 'mapping.txt')
		if (f.exists()) {
			// found as mapping.txt using getProguardOutputFile
			return f.absolutePath.toString()
		}

		f = new File(variant.packageApplication.outputFile.parent)
		f = new File(f.parent, "proguard/${variant.name}/mapping.txt")
		if (f.exists()) {
			// found through getPackageApplication
			return f.absolutePath.toString()
		}

		// any other ways to find mapping file?
		return null
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

			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy)
		}

		return httpClient
	}

	private Object post(String url, MultipartEntity entity, String via) {
		DefaultHttpClient httpClient = buildHttpClient()
		HttpPost post = new HttpPost(url)
		String userAgent = "TestFairy Gradle Plugin" + via;
		post.addHeader("User-Agent", userAgent)
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
		String via = ""

		if (project.hasProperty("testfairyChangelog")) {
			// optional: testfairyChangelog, as passed through -P
			String changelog = project.property("testfairyChangelog")
			entity.addPart('changelog', new StringBody(changelog))
		}

		if (project.hasProperty("testfairyUploadedBy")){
			via = " via " + project.property("testfairyUploadedBy")
		}

		// since testfairy gradle plugin 2.0, we no longer support instrumentation
		entity.addPart('instrumentation', new StringBody("off"))

		// sent to testers groups, as defined
		if (extension.getTestersGroups()) {
			entity.addPart('testers-groups', new StringBody(extension.getTestersGroups()))
		}

		// add notify "on" or "off"
		entity.addPart('notify', new StringBody(extension.getNotify() ? "on" : "off"))

		// add auto-update "on" or "off"
		entity.addPart('auto-update', new StringBody(extension.getAutoUpdate() ? "on" : "off"))

		return post(url, entity, via)
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
			entity.addPart('record-on-background', new StringBody("on"))
		}

		return entity
	}
}

