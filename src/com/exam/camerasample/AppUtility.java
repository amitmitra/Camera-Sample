package com.exam.camerasample;

import java.io.File;

import android.os.Environment;

public class AppUtility {
	private final static String FASTCAPTUREDFOLDER = "FastCaptured";
	private final static String DELAYCAPTUREDFOLDER = "DelayCaptured";
	private final static String TESTIMAGEDIR = "/Camera/SamplyticsLab/";

	public static void createTestImageFolders() {
		createFolderUnderTestFolder(FASTCAPTUREDFOLDER);
		createFolderUnderTestFolder(DELAYCAPTUREDFOLDER);
	}

	static void  createFolderUnderTestFolder(String foldername) {
		File file = new File(
				Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), 
				TESTIMAGEDIR + foldername);
		if (!file.exists()) {
			file.mkdirs();
		}
	}
	
	public static String getFastCapturedImageFolder() {
		return TESTIMAGEDIR + FASTCAPTUREDFOLDER;
	}
	
	public static String getDelayCapturedImageFolder() {
		return TESTIMAGEDIR + DELAYCAPTUREDFOLDER;
	}
}
