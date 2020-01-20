package com.testfairy.plugins.gradle

import com.android.utils.FileUtils
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class TestFairySymbolTask extends TestFairyTask {

	@TaskAction
	def upload() throws IOException {
		assertValidApiKey(extension)

		String serverEndpoint = extension.getServerEndpoint()

		project.logger.info("Uploading symbols to TestFairy on server ${serverEndpoint}")

		uploadSymbols(project, extension)

		project.logger.info("Symbols successfully uploaded to TestFairy")
	}

	/**
	 * Upload an APK using /api/upload REST service.
	 *
	 * @param project
	 * @param extension
	 * @param apkFilename
	 * @return Object parsed json
	 */
	private def uploadSymbols(Project project, TestFairyExtension extension) {
		String serverEndpoint = extension.getServerEndpoint()
		String url = "${serverEndpoint}/api/upload"

		def objFolders = getFoldersContainingSymbols(project)
		def zippableFolders = copyObjFoldersToTemp(project, objFolders)
		def zips = Zip.createZips(zippableFolders)

		for (File zip : zips) {
			def entity = buildEntity(extension, zip)
			String via = ""

			if (project.hasProperty("testfairyUploadedBy")) {
				via = " via " + project.property("testfairyUploadedBy")
			}

			debugLog "Zippable folders: " + zippableFolders.toString()

			def json = post(url, entity, via)
			project.logger.info("Upload " + zip.name + " : " + json.status)
		}
	}

	/**
	 * Build MultipartEntity for API parameters on Upload of an APK
	 *
	 * @param extension
	 * @return MultipartEntity
	 */
	private static def buildEntity(TestFairyExtension extension, File zipFile) {
		String apiKey = extension.getApiKey()

		MultipartEntityBuilder entity = MultipartEntityBuilder.create()
		entity.addPart('api_key', new StringBody(apiKey))
		entity.addPart('file', new FileBody(zipFile))

		return entity
	}

	/**
	 * Returns symbol folders for each sub project in the root project.
	 * @param project
	 * @return
	 */
	private def getFoldersContainingSymbols(Project project) {
		List<Project> projects = new ArrayList<>()
		List<File> symbolDirs = new ArrayList<>()

		project.rootProject.allprojects { Project p ->
			projects.add(p)
		}

		for (Project p : projects) {
			debugLog "Searching in Project: " + p.name
			def symbolDir = findSoFiles(p.buildDir, null)
			debugLog "Found: " + (symbolDir != null ? symbolDir.toString() : "none")

			if (symbolDir != null) {
				for (File f : symbolDir.keySet()) {
					symbolDirs.add(f)
				}
			}
		}

		return (File[]) symbolDirs.toArray()
	}

	/**
	 * Returns a map for each root folder containing SO files. Starts searching from the given file/folder
	 * and explores recursively in depth first search manner.
	 *
	 * The mapping structure will look like this:
	 *
	 * {
	 *     objFolder1: {
	 *         arch1: [lib1.so, lib2.so, ...],
	 *         arch2: [lib1.so, lib2.so, ...],
	 *         ...
	 *     },
	 *     objFolder2: {
	 *         arch1: [lib1.so, lib2.so, ...],
	 *         arch2: [lib1.so, lib2.so, ...],
	 *         ...
	 *     }
	 *     ...
	 * }
	 *
	 * @param buildDir
	 * @param outArchSoMapping
	 * @return
	 */
	private def findSoFiles(File currentDirOrFile, Map<File, Map<String, Set<File>>> outArchSoMapping) {
		String[] archs = Architectures.stringValues()

		if (outArchSoMapping == null) {
			outArchSoMapping = new HashMap<>()
		}

		if (currentDirOrFile.isDirectory() && !SYMBOL_FOLDERS_BLACKLIST.contains(currentDirOrFile.name)) {
			for(File f : currentDirOrFile.listFiles()) {
				debugLog "Searching in " + f.name

				if (!SYMBOL_FOLDERS_BLACKLIST.contains(f.name)) {
					outArchSoMapping = findSoFiles(f, outArchSoMapping)
				}
			}
		} else if (!currentDirOrFile.isDirectory() && currentDirOrFile.name.startsWith("lib") && currentDirOrFile.name.endsWith(".so")) {
			debugLog "File: " + currentDirOrFile.name
			debugLog "Parent File: " + currentDirOrFile.parentFile.name
			debugLog "Parent Parent File: " + currentDirOrFile.parentFile.parentFile.name

			if (currentDirOrFile.parentFile.parentFile.parentFile.name == applicationVariant.dirName &&
					archs.contains(currentDirOrFile.parentFile.name) && (
					currentDirOrFile.parentFile.parentFile.name == "obj" ||
							currentDirOrFile.parentFile.parentFile.name == "local"
			)
			) {
				def soFile = currentDirOrFile
				def archFolder = currentDirOrFile.parentFile
				def objFolder = currentDirOrFile.parentFile.parentFile

				debugLog "Processing " + soFile.name + " - " + archFolder.name  + " - " + objFolder.name

				if (!outArchSoMapping.containsKey(objFolder)) {
					outArchSoMapping.put(objFolder, new HashMap<String, Set<File>>())
				}

				def archSoMapping = outArchSoMapping.get(objFolder)

				if (!archSoMapping.containsKey(archFolder.name)) {
					archSoMapping.put(archFolder.name, new HashSet<File>())
				}

				def soSetForArch = archSoMapping.get(archFolder.name)

				soSetForArch.add(soFile)
				for (File so : soSetForArch) {
					if (so.name == soFile.name && so != soFile) {
						if (so.size() > soFile.size()) {
							soSetForArch.remove(soFile)
						} else {
							soSetForArch.remove(so)
						}
					}
				}
			}
		}

		return outArchSoMapping
	}

	/**
	 * Copies given symbol folders to a temporary, safe location for zipping.
	 * @param project
	 * @param objFolders
	 * @return
	 */
	private def copyObjFoldersToTemp(Project project, File[] objFolders) {
		File testfairyFolder = new File(project.rootProject.buildDir.path + "/intermediates/testfairy/symbols")

		if (testfairyFolder.exists()) {
			deleteFolder(testfairyFolder)
		}

		testfairyFolder.mkdirs()

		List<File> zippableFolders = new ArrayList<>()
		for (int i = 0; i < objFolders.length; i++) {
			File tempFolder = new File(testfairyFolder.path + "/" + i + "/obj")
			tempFolder.mkdir()

			FileUtils.copyDirectory(objFolders[i], tempFolder)
			zippableFolders.add(tempFolder)
		}

		return (File[]) zippableFolders.toArray()
	}

	/**
	 * Deletes given folder recursively like `rm -rf`.
	 * @param folderToBeDeleted
	 * @return
	 */
	private def deleteFolder(File folderToBeDeleted) {
		File[] allContents = folderToBeDeleted.listFiles()
		if (allContents != null) {
			for (File file : allContents) {
				deleteFolder(file)
			}
		}
		return folderToBeDeleted.delete()
	}

	private def debugLog(String msg) {
//		println msg
	}

	private static final enum Architectures {
		ARM64V8A("arm64-v8a"),
		ARMABIV7A("armeabi-v7a"),
		X86("x86"),
		x86_64("x86_64");

		private final String folderName

		Architectures(String folderName) {
			this.folderName = folderName
		}

		static String[] stringValues() {
			List<String> strings = new ArrayList<>()

			for (Architectures a : values()) {
				strings.add(a.toString())
			}

			return strings
		}

		@Override
		String toString() {
			return folderName
		}
	}

	private static final String[] SYMBOL_FOLDERS_BLACKLIST = [
			"merged_jni_libs",
			"merged_native_libs",
			"merged_shaders",
			"stripped_native_libs",
			"intermediate-jars"
	]
}

