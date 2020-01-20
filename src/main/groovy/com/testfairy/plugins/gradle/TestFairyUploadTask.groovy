package com.testfairy.plugins.gradle

import com.android.build.gradle.api.ApkVariantOutput
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.gradle.api.Project

import org.gradle.api.tasks.TaskAction

class TestFairyUploadTask extends TestFairyTask {

	@TaskAction
	def upload() throws IOException {
		assertValidApiKey(extension)

		String serverEndpoint = extension.getServerEndpoint()

		String apkFilename = null
		File apkFile = null

		applicationVariant.outputs.each {
			if (it instanceof ApkVariantOutput) {
				ApkVariantOutput apkVariantOutput = (ApkVariantOutput)it

				// Check default path
				apkFilename = applicationVariant.packageApplicationProvider.get().outputDirectory.toString() + "/" + apkVariantOutput.outputFileName
				apkFile = new File(apkFilename)

				if (apkFile.exists()) {
					return
				}

				// Check most common path
				apkFilename = project.buildDir.path + "/" + apkVariantOutput.dirName + "outputs/apk/" + apkVariantOutput.name + "/" + apkVariantOutput.outputFileName
				apkFile = new File(apkFilename)

				if (apkFile.exists()) {
					return
				}

				// Fallback to deprecated API (apkVariantOutput.outputFile), hopefully will never happen
				apkFilename = apkVariantOutput.outputFile.toString()
				apkFile = new File(apkFilename)
			}
		}

		if (apkFile != null && apkFile.exists()) {
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
		} else {
			println "Can't find any APK file to upload."
			throw new IOException("Apk not found.");
		}
	}

	/**
	 * Upload an APK using /api/upload REST service.
	 *
	 * @param project
	 * @param extension
	 * @param apkFilename
	 * @return Object parsed json
	 */
	private def uploadApk(Project project, TestFairyExtension extension, String apkFilename, String mappingFilename) {
		String serverEndpoint = extension.getServerEndpoint()
		String url = "${serverEndpoint}/api/upload"
		MultipartEntityBuilder entity = buildEntity(extension, apkFilename, mappingFilename)
		String via = ""
		String tags = ""

		if (project.hasProperty("testfairyChangelog")) {
			// optional: testfairyChangelog, as passed through -P
			String changelog = project.property("testfairyChangelog")
			entity.addPart('changelog', new StringBody(changelog))
		}

		if (project.hasProperty("testfairyUploadedBy")) {
			via = " via " + project.property("testfairyUploadedBy")
		}

		if (project.hasProperty("testfairyTags")) {
			if (extension.getTags()) {
				// enable record on background option
				tags = extension.getTags() + "," + project.property("testfairyTags")
			} else {
				tags = project.property("testfairyTags").toString()
			}

			entity.addPart('tags', new StringBody(tags))
		} else if (extension.getTags()) {
			// enable record on background option
			tags = extension.getTags()
			entity.addPart('tags', new StringBody(tags))
		}

		// since testfairy gradle plugin 2.0, we no longer support instrumentation
		entity.addPart('instrumentation', new StringBody("off"))

		return post(url, entity, via)
	}

	/**
	 * Build MultipartEntity for API parameters on Upload of an APK
	 *
	 * @param extension
	 * @return MultipartEntity
	 */
	private static def buildEntity(TestFairyExtension extension, String apkFilename, String mappingFilename) {
		String apiKey = extension.getApiKey()

		MultipartEntityBuilder entity = MultipartEntityBuilder.create()
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

		if (extension.getCustom()) {
			// enable record on background option
			entity.addPart('custom', new StringBody(extension.getCustom()))
		}

		if (extension.getTestersGroups()) {
			// send to testers groups, as defined
			entity.addPart('testers-groups', new StringBody(extension.getTestersGroups()))
		}

		// add notify "on" or "off"
		entity.addPart('notify', new StringBody(extension.getNotify() ? "on" : "off"))

		// add auto-update "on" or "off"
		entity.addPart('auto-update', new StringBody(extension.getAutoUpdate() ? "on" : "off"))

		return entity
	}
}

