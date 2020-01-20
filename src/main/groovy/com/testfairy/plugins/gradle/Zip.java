package com.testfairy.plugins.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class Zip {
	private List<String> fileList;
	private String sourceFolder;

	Zip(String sourceFolder) {
		this.sourceFolder = sourceFolder;
		fileList = new ArrayList<String>();
	}

	/**
	 * Zip it
	 *
	 * @param zipFile output ZIP file location
	 */
	void zipIt(String zipFile) {
		byte[] buffer = new byte[1024];

		try {
			generateFileList(new File(sourceFolder));

			FileOutputStream fos = new FileOutputStream(zipFile);
			ZipOutputStream zos = new ZipOutputStream(fos);

			for (String file : this.fileList) {
				ZipEntry ze = new ZipEntry(file);
				zos.putNextEntry(ze);

				FileInputStream fin =
						new FileInputStream(sourceFolder + File.separator + file);

				int len;
				while ((len = fin.read(buffer)) > 0) {
					zos.write(buffer, 0, len);
				}

				fin.close();
			}

			zos.closeEntry();
			zos.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Traverse a directory and get all files,
	 * and add the file into fileList
	 *
	 * @param node file or directory
	 */
	private void generateFileList(File node) {
		//add file only
		if (node.isFile()) {
			fileList.add(generateZipEntry(node.getAbsoluteFile().toString()));
		}

		if (node.isDirectory()) {
			String[] subNote = node.list();
			for (String filename : subNote) {
				generateFileList(new File(node, filename));
			}
		}

	}

	/**
	 * Format the file path for zip
	 *
	 * @param file file path
	 * @return Formatted file path
	 */
	private String generateZipEntry(String file) {
		return file.substring(sourceFolder.length() + 1);
	}

	public static File[] createZips(File[] zippableFolders) {
		List<File> zips = new ArrayList<>();

		for (int i = 0; i < zippableFolders.length; i++) {
			File zippable = zippableFolders[i].getParentFile();
			String zipFilePath = zippable.getPath() + "/" + i + ".zip";
			File zipFile = new File(zipFilePath);

			if (zipFile.exists()) {
				zipFile.delete();
			}

			Zip zip = new Zip(zippable.getAbsolutePath());
			zip.zipIt(zipFilePath);

			zips.add(zipFile);
		}

		return zips.toArray(new File[zips.size()]);
	}
}