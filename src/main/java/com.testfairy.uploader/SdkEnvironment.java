package com.testfairy.uploader;

import java.lang.Override;
import java.lang.String;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

public class SdkEnvironment
{
	private String sdkDirectory;

	public SdkEnvironment(String sdkDirectory) {
		this.sdkDirectory = sdkDirectory;
	}

	/**
	 * Locates zipalign tool on disk.
	 *
	 * @return String
	 */
	public String locateZipalign() {

		String ext = isWindows() ? ".exe" : "";
		File zipalign = new File(FilenameUtils.normalize(sdkDirectory + "/tools/zipalign" + ext));
		if (zipalign.exists()) {
			return zipalign.getAbsolutePath();
		}

		FileUtils.listFiles(new File(sdkDirectory), new NameFileFilter("zipalign"), TrueFileFilter.INSTANCE)
		// try different versions of build-tools
		String[] versions = ["20.0.0", "19.1.0"];
		for (String version: versions) {
			File f = new File(FilenameUtils.normalize(sdkDirectory + "/build-tools/" + version + "/zipalign" + ext));
			if (f.exists()) {
				return f.getAbsolutePath();
			}
		}

		return null;
	}

	/**
	 * Locates jarsigner executable on disk. Jarsigner is required since we are
	 * re-signing the APK.
	 *
	 * @return String path
	 */
	public String locateJarsigner() {
		def java_home = System.properties.get("java.home");

		def ext = isWindows() ? ".exe" : "";
		String jarsigner = java_home + "/jarsigner" + ext;
		if (new File(jarsigner).exists()) {
			return jarsigner;
		}

		// try in java_home/bin
		jarsigner = FilenameUtils.normalize(java_home + "/bin/jarsigner" + ext);
		if (new File(jarsigner).exists()) {
			return jarsigner;
		}

		// try going up one directory and into bin, JDK7 on Mac is layed out this way
		jarsigner = FilenameUtils.normalize(java_home + "/../bin/jarsigner" + ext);
		if (new File(jarsigner).exists()) {
			return jarsigner;
		}

		return null;
	}
}