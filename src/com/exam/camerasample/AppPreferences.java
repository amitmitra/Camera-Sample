package com.exam.camerasample;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPreferences {

	private SharedPreferences mSettings;
	private final String ISO ="ISO";
	private final String AE ="AE";
	private final String WB ="WB";
	private final String FOCUS ="FOCUS";
	private final String PREFS_NAME = "AppData";

	public AppPreferences(Context activityContext) {
		mSettings = activityContext.getSharedPreferences(PREFS_NAME, 0);
	}

	public void saveISO(int iso) {
		putInt(ISO, iso);
	}

	public void saveAE(int ae) {
		putInt(AE, ae);
	}
	
	public void saveWBMode(int wbmode) {
		putInt(WB, wbmode);
	}
	
	public void saveFocus(int focus) {
		putInt(FOCUS, focus);
	}
	
	public int getISO() {
		return mSettings.getInt(ISO, 6);
	}

	public int getAE() {
		return mSettings.getInt(AE, 6);
	}
	
	public int getWBMode() {
		return mSettings.getInt(WB, 1);
	}
	
	public int getFocus() {
		return mSettings.getInt(FOCUS, 3);
	}

	private void putInt(String key, int value) {
		SharedPreferences.Editor editor = getEditor();
		editor.putInt(key, value);
		editor.commit();
	}

	private SharedPreferences.Editor getEditor() {
		return mSettings.edit();
	}
}
