package com.testfairy.plugins.gradle

import com.android.build.gradle.api.ApplicationVariant
import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils

//import com.testfairy.uploader.*

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class TestFairyUploadTask extends DefaultTask {

	String apiKey

	ApplicationVariant applicationVariant

	TestFairyExtension extension

	@TaskAction
	def upload() throws IOException {

		assertValidApiKey(extension)

		String apiKey = extension.getApiKey()
		String serverEndpoint = extension.getServerEndpoint()

		// use outputFile from packageApp task
		String apkFilename = null
		applicationVariant.outputs.each {
			if (it.outputFile.exists()) {
				String filename = it.outputFile.toString()
				if (filename.endsWith(".apk")) {
					apkFilename = filename
				}
			}
		}

		project.logger.info("Uploading ${apkFilename} to TestFairy on server ${serverEndpoint}")

		String proguardMappingFilename = null
		if (extension.uploadProguardMapping && applicationVariant.getMappingFile()?.exists()) {
			// proguard-mapping.txt upload is enabled and mapping found

			proguardMappingFilename = applicationVariant.getMappingFile().toString()
			project.logger.debug("Using proguard mapping file at ${proguardMappingFilename}")
		}

		def json = uploadApk(project, extension, apkFilename, proguardMappingFilename)

		println ""
		println "Successfully uploaded to TestFairy, build is available at:"
		println json.build_url
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
		String userAgent = "TestFairy Gradle Plugin 2.0 " + via
		post.addHeader("User-Agent", userAgent)
		post.setEntity(entity)
		HttpResponse response = httpClient.execute(post)

		String json = EntityUtils.toString(response.getEntity())
		def parser = new JsonSlurper()
		def parsed = parser.parseText(json)
		if (parsed.status != "ok") {
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

		// send to testers groups, as defined
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

