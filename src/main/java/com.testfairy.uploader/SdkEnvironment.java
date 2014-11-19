package com.testfairy.uploader;

import java.io.File;
import java.lang.String;
import java.util.Iterator;
import java.util.Collection;

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

	private boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
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

		// find any zipfiles under the sdk/build-tools
		Collection<File> files = FileUtils.listFiles(new File(sdkDirectory + "/build-tools"), new NameFileFilter("zipalign" + ext), TrueFileFilter.INSTANCE);
		if (files.size() > 0) {
			// found at least one zipalign file, look up the highest version
			Iterator<File> it = files.iterator();
			zipalign = it.next();
			while (it.hasNext()) {
				File f = it.next();
				if (zipalign.compareTo(f) < 0) {
					// f is newer
					zipalign = f;
				}
			}

			return zipalign.getAbsolutePath();
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
		String java_home = System.getProperty("java.home");

		String ext = isWindows() ? ".exe" : "";
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