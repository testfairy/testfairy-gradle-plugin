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

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant

class TestFairyPlugin implements Plugin<Project> {

	private String apiKey

	@Override
	void apply(Project project) {

		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		if (project.plugins.hasPlugin(AppPlugin)) {

			AppExtension android = project.android
			android.applicationVariants.all { ApplicationVariant variant ->
				TestFairyUploadTask task = project.tasks.create("testfairy${variant.name.capitalize()}", TestFairyUploadTask)
				task.group = "TestFairy"
				task.description = "Upload '${variant.name}' to TestFairy"
				task.applicationVariant = variant
				task.extension = extension
				task.outputs.upToDateWhen { false }
				task.dependsOn variant.assemble
			}
		}
	}
}

