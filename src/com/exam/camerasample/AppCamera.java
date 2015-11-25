package com.exam.camerasample;

import android.view.TextureView;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ListView;

public interface AppCamera {

	void start(TextureView textureView);
	void takePicture(ListView imageList);
	void closeCamera();
}
