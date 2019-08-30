package com.testfairy.plugins.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant

//import com.testfairy.uploader.*

import org.gradle.api.Plugin
import org.gradle.api.Project

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
				task.dependsOn variant.assembleProvider
			}
		}
	}
}

