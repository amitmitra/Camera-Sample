package com.exam.camerasample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;

public class MainActivity extends Activity{

	private static final String TAG = MainActivity.class.getSimpleName();

	private TextureView mTextureView;
	private ListView mImageList;
	private AppCamera mCamera;
	private Button mCaptureButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		initialize();
	}

	public void initialize() {
		mTextureView = (TextureView) findViewById(R.id.camera_preview);
		mImageList = (ListView) findViewById(R.id.image_list);
		mCaptureButton = (Button) findViewById(R.id.button);

		/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Log.d(TAG, "camera2 selected");
			mCamera = new OffScreenCamera(this);
		} else {*/
			Log.d(TAG, "camera1 selected");
			mCamera = new AdvancedCamera(this);
			// }

			mCaptureButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					mCamera.takePicture(mImageList);
				}
			});
		//}
	}

	@Override
	protected void onPause() {
		mCamera.closeCamera();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCamera.start(mTextureView);
	}

}
