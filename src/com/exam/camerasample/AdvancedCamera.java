package com.exam.camerasample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class AdvancedCamera implements AppCamera {

	private static final String TAG = AdvancedCamera.class.getSimpleName();

	private Camera mCamera;
	private Context mContext;
	private TextureView mTextureView;
	private SurfaceTexture mSurfaceTexture;
	private static File mFile;
	private boolean safeToTakePicture = false;
	private ListView mImageList;
	private List<ImageItem> bitmapList;
	private int aeMinValue;
	private int aeMaxValue;

	public AdvancedCamera(Context appcontext) {
		mContext = appcontext;
	}

	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture,
				int width, int height) {
			mSurfaceTexture = texture;
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture,
				int width, int height) {

		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture) {
			safeToTakePicture = true;
		}
	};

	public void start(TextureView textureView) {
		if (textureView == null) {
			textureView = new TextureView(mContext);
		}
		mTextureView = textureView;
		WindowManager wm = (WindowManager) mContext
				.getSystemService(Context.WINDOW_SERVICE);
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
				WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
				PixelFormat.TRANSPARENT);
		params.height = 1;
		params.width = 1;
		// wm.addView(textureView, params);
		if (mTextureView.isAvailable()) {
			openCamera(mTextureView.getWidth(), mTextureView.getHeight());
		} else {
			// Log.d("KB",
			// "onSurfaceTextureAvailable setSurfaceTextureListener");
			mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
		}
	}

	private void openCamera(int width, int height) {
		if (mCamera == null) {
			try {
				mCamera = Camera.open();
			} catch (RuntimeException e) {
				Toast.makeText(
						mContext,
						"Some Other application is already using Camera! Close it and try again",
						Toast.LENGTH_SHORT).show();
			}
		}
		// Parameters cp = mCamera.getParameters();
		// cp.setPictureSize(width, height);

		mCamera.setDisplayOrientation(90);

		try {
			mCamera.setPreviewTexture(mSurfaceTexture);
			mCamera.startPreview();
		} catch (IOException ioe) {
			Log.e(TAG, "Exception : " + ioe.getMessage());
			ioe.printStackTrace();
		}
	}

	public void takePicture(ListView imageList) {
		if (null == mContext || null == mCamera) {
			return;
		}
		mImageList = imageList;
		// mCamera.startPreview();
		try {
			Parameters cp = mCamera.getParameters();
			cp.setFocusMode(Parameters.FOCUS_MODE_INFINITY);
			mCamera.setParameters(cp);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set Focus" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | Focus",
					Toast.LENGTH_SHORT).show();
		}

		try {
			Parameters cp = mCamera.getParameters();
			cp.setWhiteBalance(Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT);
			mCamera.setParameters(cp);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set White Balance" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | WB",
					Toast.LENGTH_SHORT).show();
		}

		try {
			Parameters cp = mCamera.getParameters();
			String supportedIsoValues = cp.get("iso-values");
			if (supportedIsoValues != null) {
				String[] isoValues = supportedIsoValues.split(",");
				long minValue = Long.MAX_VALUE;
				String isoStr = "";
				for (int i = 0; i < isoValues.length; i++) {
					try {
						isoStr = isoValues[i].substring(3);
						long isoValue = Long.parseLong(isoStr);
						if (isoValue < minValue)
							minValue = isoValue;
					} catch (NumberFormatException e) {
						Log.i(TAG, isoStr + " is not a convertible ISO Value");
					}
				}
				cp.set("iso", "ISO" + minValue);
				mCamera.setParameters(cp);
			} else {
				Log.i(TAG, "ISO Not supported");
			}
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set ISO" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | ISO",
					Toast.LENGTH_SHORT).show();
		}

		try {
			Parameters cp = mCamera.getParameters();
			int min = cp.getMinExposureCompensation();
			int max = cp.getMaxExposureCompensation();
			float step = cp.getExposureCompensationStep();
			Log.i("Amit", "Min Exp : " + min + " | Max Exp : " + max
					+ "  |  Step : " + step + " AutoExposure Lock Supported : "
					+ cp.isAutoExposureLockSupported());
			aeMinValue = Math.round(min * step);
			aeMaxValue = Math.round(max * step);
			cp.setAutoExposureLock(false);
			mCamera.setParameters(cp);
			bitmapList = new ArrayList<ImageItem>();
			mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));
			captureImage(aeMinValue, aeMaxValue);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set Exposure" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | AE",
					Toast.LENGTH_SHORT).show();
		}
	}

	private void captureImage(int value, int maxValue) throws RuntimeException {
		if (value > maxValue){
			mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));
			return;
		}
		Parameters cp = mCamera.getParameters();
		cp.setExposureCompensation(value);
		mCamera.setParameters(cp);
		String picname = String.format("pic%d.jpg", value);
		mFile = new File(mContext.getExternalFilesDir(null), picname);
		if (safeToTakePicture) {
			try {
				mCamera.takePicture(null, null, mCall);
				safeToTakePicture = false;
			} catch (RuntimeException e) {
				Log.i(TAG, "Not able to take picture");
			} finally {
			}
		}
	}

	public void closeCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	private static void setTestFinished(File mFile) {

	}

	Camera.PictureCallback mCall = new Camera.PictureCallback() {

		public void onPictureTaken(byte[] data, Camera camera) {
			// decode the data obtained by the camera into a Bitmap

			Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
			bm = Bitmap.createScaledBitmap(bm, bm.getWidth(), bm.getHeight(),
					true);
			createDirectoryAndSaveFile(bm, "Image_" + aeMinValue + ".jpeg");
			Parameters cp = mCamera.getParameters();
			ImageItem item = new ImageItem();
			item.imageBitMap = bm;
			item.focus = "Focus : " + cp.getFocusMode();
			item.wb = "White Balance : " + cp.getWhiteBalance();
			if (cp.get("iso") != null)
				item.iso = "ISO Supported | Value : " + cp.get("iso");
			else
				item.iso = "ISO Not Supported";
			item.aeSupported = "Min Exp : " + cp.getMinExposureCompensation()
					+ " | Max Exp : " + cp.getMaxExposureCompensation()
					+ "  |  Step : " + cp.getExposureCompensationStep();
			item.ae = "Exposure Compensation : " + cp.getExposureCompensation();
			bitmapList.add(item);
			
			safeToTakePicture = true;
			mCamera.stopPreview();
			mCamera.startPreview();
			aeMinValue++;
			captureImage(aeMinValue, aeMaxValue);
			// setTestFinished(mFile);
		}
	};
	
	private void createDirectoryAndSaveFile(Bitmap imageToSave, String fileName) {

	    File direct = new File(Environment.getExternalStorageDirectory() + "/Samplytics_Testing");

	    if (!direct.exists()) {
	        File wallpaperDirectory = new File("/sdcard/Samplytics_Testing/");
	        wallpaperDirectory.mkdirs();
	    }

	    File file = new File(new File("/sdcard/Samplytics_Testing/"), fileName);
	    if (file.exists()) {
	        file.delete();
	    }
	    try {
	        FileOutputStream out = new FileOutputStream(file);
	        imageToSave.compress(Bitmap.CompressFormat.JPEG, 100, out);
	        out.flush();
	        out.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
}

class MyImageAdapter extends BaseAdapter {

	List<ImageItem> imageBitmapList;
	Context mContext;

	public MyImageAdapter(List<ImageItem> bitmapList, Context context) {
		this.imageBitmapList = bitmapList;
		this.mContext = context;
	}

	@Override
	public int getCount() {
		return imageBitmapList.size();
	}

	@Override
	public ImageItem getItem(int position) {
		return imageBitmapList.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.image_item, null);
		}
		ImageItem item = getItem(position);
		((ImageView) convertView.findViewById(R.id.captured_image)).setImageBitmap(item.imageBitMap);
		((TextView) convertView.findViewById(R.id.focus)).setText(item.focus);
		((TextView) convertView.findViewById(R.id.wb)).setText(item.wb);
		((TextView) convertView.findViewById(R.id.iso)).setText(item.iso);

		((TextView) convertView.findViewById(R.id.supported_ae)).setText(item.aeSupported);
		((TextView) convertView.findViewById(R.id.ae)).setText(item.ae);

		return convertView;
	}
}
