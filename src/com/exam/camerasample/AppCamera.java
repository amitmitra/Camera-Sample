package com.exam.camerasample;

import android.util.Range;
import android.view.TextureView;
import android.widget.ListView;

public interface AppCamera {

	void start(TextureView textureView);
	void takePicture(ListView imageList, long delay, int minAE, int maxAE, int aeStep, String wb, String iso);
	void closeCamera();
	void takeSinglePicture(ListView imageList, long delay);
	void setWB(String wb);
	void setISO(String ISO);
	void setEV(double ev);
	int[] getAWBModes();
	int toFocalToProgeres(float focal);
	void takePicture();
	float calculateFocal(int progress);
	void changeFocal(float focal);
	void setISO(int iso);
	void setAE(double progress);
	void setWhiteBalaceMode(int mode);
	Range<Integer> getISORange();
	Range<Long> getAERange();
}
