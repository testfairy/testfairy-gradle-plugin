package com.testfairy.plugins.gradle

import com.android.build.gradle.api.ApkVariant
import com.android.builder.model.SigningConfig
import com.android.builder.model.Variant
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
import org.apache.commons.compress.archivers.zip.*
import groovy.json.JsonSlurper

class TestFairyPlugin implements Plugin<Project> {
	private String jarSignerPath

	private void configureJavaTools(Project project) {
		String sdkDirectory = getSdkDirectory(project)
		SdkEnvironment env = new SdkEnvironment(sdkDirectory)

		jarSignerPath = env.locateJarsigner()
		if (jarSignerPath == null) {
			throw new GradleException("Could not locate jarsigner, please update java.home property")
		}
	}

	private static String getSdkDirectory(Project project) {
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
		TestFairyExtension extension = project.extensions.create("testfairyConfig", TestFairyExtension, project)
		configureJavaTools(project)

		project.configure(project) {
			if (it.hasProperty("android")) {
				tasks.whenTaskAdded { task ->
					project.("android").applicationVariants.all { variant ->
						// locate packageRelease and packageDebug tasks
						def expectingTask = "package${variant.name.capitalize()}".toString()
						if (expectingTask.equals(task.name)) {
							// create new task with name such as testfairyRelease and testfairyDebug
							def newTaskName = "testfairy${variantName.capitalize()}"
							def variantName = variant.name
							project.task(newTaskName) << {
								ApkVariant apkVariant = variant
								String  apkFilename = task.outputFile.toString()
								String apiKey = project.hasProperty("testfairyApiKey") ?
										project.property("testfairyApiKey") : extension.getApiKey()

								project.logger.info("Instrumenting ${apkFilename} using apiKey ${apiKey}}")

								AndroidUploader.Builder android = new AndroidUploader.Builder(apiKey)
								applyProperties(android, jarSignerPath, project);
								applyOptions(android, extension, project)
								applyMetrics(android, extension, project)
								applyProguard(android, extension, apkVariant, project)
								applySigning(android, extension, apkVariant, project, apkFilename)
								applyProxy(android);
								android.setApkPath(task.outputFile.toString());

								AndroidUploader uploader = android.build();
								project.logger.debug(uploader.toString())
								uploader.upload(new Listener() {
									@Override
									void onUploadStarted() {
										println "Starting upload"
									}

									@Override
									void onUploadComplete(Build build) {
										println "Successfully uploaded to TestFairy, build is available at:"
										println build.buildUrl()
									}

									@Override
									void onUploadFailed(Throwable throwable) {
										println "Failed to upload to TestFairy: " + throwable.getMessage()
									}

									@Override
									void onProgress(float p) {

									}
								})
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

	private static void applyProperties(AndroidUploader.Builder android, String jarSignerPath, Project project) {
		project.logger.debug("Located jarsigner at ${jarSignerPath}")
		android.setJarSignerPath(jarSignerPath)
		String userAgent = "TestFairy Gradle Plugin";
		if(project.hasProperty("testfairyUploadedBy")){
			userAgent = userAgent + " via " + project.property("testfairyUploadedBy")
		}
		android.setHttpUserAgent(userAgent)
	}

	private static void applyProxy(AndroidUploader.Builder android) {
		String proxyHost = System.getProperty("http.proxyHost")
		if (proxyHost == null)
			return;

		int proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"));
		android.setProxyHost(proxyHost, proxyPort);

		String proxyUser = System.getProperty("http.proxyUser")
		if (proxyUser == null)
			return;

		String proxyPassword = System.getProperty("http.proxyPassword")
		android.setProxyCredentials(proxyUser, proxyPassword)
	}

	private static void applySigning(AndroidUploader.Builder android, TestFairyExtension extension, ApkVariant variant, Project project, String apkFilename) {
		boolean enableInstrumentation = variant.isSigningReady() && isApkSigned(apkFilename)
		android.enableInstrumentation(enableInstrumentation);

		if (enableInstrumentation) {
			SigningConfig signingConfig = variant.signingConfig;
			android.setKeystore(
				signingConfig.storeFile.getAbsolutePath(),
				signingConfig.keyAlias,
				signingConfig.storePassword,
				signingConfig.keyPassword
			)

			String digestalg = project.hasProperty("testfairyDigestalg") ?
				project.property("testfairyDigestalg") : extension.getDigestalg()
			android.setDigestAlgorithm(digestalg)

			String sigalg = project.hasProperty("testfairySigalg") ?
				project.property("testfairySigalg") : extension.getSigalg()
			android.setSignatureAlgorithm(sigalg)
		}
	}

	private static void applyProguard(AndroidUploader.Builder android, TestFairyExtension extension, ApkVariant variant, Project project) {
		if (isMinifyEnabledCompat(variant.buildType) && extension.uploadProguardMapping) {
			String proguardFile = getMappingFileCompat(variant)
			project.logger.debug("Using proguard mapping file at ${proguardFile}")
			android.setProguardMapPath(proguardFile)
		}
	}

	private static void applyMetrics(AndroidUploader.Builder android, TestFairyExtension extension, Project project) {
		String metrics = project.hasProperty("testfairyMetrics") ?
				project.property("testfairyMetrics") : extension.getMetrics()
		if (metrics != null) {
			android.setMetrics(new Metrics.Builder().addAll(metrics).build())
		}
	}

	private static void applyOptions(AndroidUploader.Builder android, TestFairyExtension extension, Project project) {
		Options.Builder options = new Options.Builder();

		Boolean watermark = project.hasProperty("testfairyWatermark") ?
			"true".equals(project.property("testfairyWatermark")) :
			extension.getIconWatermark();
		if (watermark != null) {
			options.setIconWatermark(watermark)
		}

		String video = project.hasProperty("testfairyVideo") ?
			project.property("testfairyVideo") : extension.getVideo()
		if (video != null) {
			if ("on".equals(video)) {
				options.setVideoRecordingOn()
			} else if ("off".equals(video)) {
				options.setVideoRecordingOff()
			} else if ("wifi".equals(video)) {
				options.setVideoRecordingWifi()
			}
		}

		String videoQuality = project.hasProperty("testfairyVideoQuality") ?
			project.property("testfairyVideoQuality") : extension.getVideo();
		if (videoQuality != null) {
			if ("high".equals(videoQuality)) {
				options.setVideoQualityHigh()
			} else if ("medium".equals(videoQuality)) {
				options.setVideoQualityMedium()
			} else if ("low".equals(videoQuality)) {
				options.setVideoQualityLow()
			}
		}

		String videoRate = project.hasProperty("testfairyVideoRate") ?
			project.property("testfairyVideoRate") : extension.getVideoRate();
		if (videoRate != null) {
			options.setVideoRecordingRate(Float.parseFloat(videoRate))
		}

		String maxDuration = project.hasProperty("testfairyMaxDuration") ?
				project.property("testfairyMaxDuration") : extension.getMaxDuration();
		if (maxDuration != null) {
			options.setMaxDuration(maxDuration)
		}

		Boolean notifyTesters = project.hasProperty("testfairyNotify") ?
			"true".equals(project.property("testfairyNotify")) :
			extension.getNotify();
		if (notifyTesters != null) {
			options.notifyTesters(notifyTesters)
		}

		Boolean autoUpdate = project.hasProperty("testfairyAutoUpdate") ?
			"true".equals(project.property("testfairyAutoUpdate")) :
			extension.getAutoUpdate();
		if (autoUpdate != null) {
			options.setAutoUpdate(autoUpdate)
		}

		Boolean recordInBackground = project.hasProperty("testfairyRecordOnBackground") ?
			"true".equals(project.property("testfairyRecordOnBackground")) :
			extension.getRecordOnBackground()
		if (recordInBackground != null && extension.getRecordOnBackground()) {
			options.setRecordInBackground()
		}

		String testers = project.hasProperty("testfairyTesterGroups") ?
			project.property("testfairyTesterGroups") : extension.getTestersGroups();
		if (testers != null) {
			for (String tester : testers.split(",")) {
				options.addTesterGroup(tester.trim());
			}
		}

		String comment = project.hasProperty("testfairyChangelog")?
			project.property("testfairyChangelog") : extension.getComment()
		if (comment != null) {
			options.setChangelog(comment)
		}

		android.setOptions(options.build())
	}

	/**
	 * Returns true if code minification is enabled for this build type.
	 * Added to work around runProguard property being renamed to isMinifyEnabled in Android Gradle Plugin 0.14.0
	 *
	 * @param buildType
	 * @return boolean
	 */
	private static boolean isMinifyEnabledCompat(buildType) {
		if (buildType.respondsTo("isMinifyEnabled")) {
			return buildType.isMinifyEnabled()
		} else {
			return buildType.runProguard
		}
	}

	private static String getMappingFileCompat(variant) {
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

	/**
	 * Checks if the given APK is signed
	 *
	 * @param apkFilename
	 * @return boolean
	 */
	private static boolean isApkSigned(String apkFilename) {
		List<String> filenames = getApkFiles(apkFilename)
		for (String f : filenames) {
			if (f.startsWith("META-INF/") && f.endsWith("SF")) {
				// found a signature file, this APK is signed
				return true
			}
		}

		return false
	}

	/**
	 * Get a list of all files inside this APK file.
	 *
	 * @param apkFilename
	 * @return List < String >
	 */
	private static List<String> getApkFiles(String apkFilename) {
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
}
