package com.testfairy.plugins.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant

import org.gradle.api.Plugin
import org.gradle.api.Project

class TestFairyPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {

		// create an extension where the apiKey and such settings reside
		def extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)

		if (project.plugins.hasPlugin(AppPlugin)) {

			AppExtension android = project.android
			android.applicationVariants.all { ApplicationVariant variant ->
				// Upload APK task
				project.tasks.register(
						"testfairy${variant.name.capitalize()}",
						TestFairyUploadTask
				) { uploadApkTask ->
					uploadApkTask.group = "TestFairy"
					uploadApkTask.description = "Upload '${variant.name}' to TestFairy"
					uploadApkTask.applicationVariant = variant
					uploadApkTask.extension = extension
					uploadApkTask.outputs.upToDateWhen { false }
					uploadApkTask.dependsOn variant.assembleProvider
				}

				// Upload NDK symbols task
				project.tasks.register(
						"testfairyNdk${variant.name.capitalize()}",
						TestFairySymbolTask
				) { uploadSymbolsTask ->
					uploadSymbolsTask.group = "TestFairy"
					uploadSymbolsTask.description = "Upload NDK symbols for '${variant.name}' to TestFairy"
					uploadSymbolsTask.applicationVariant = variant
					uploadSymbolsTask.extension = extension
					uploadSymbolsTask.outputs.upToDateWhen { false }
					uploadSymbolsTask.dependsOn variant.assembleProvider
				}
			}
		}
	}
}

