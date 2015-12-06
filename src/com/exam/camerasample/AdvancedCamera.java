package com.exam.camerasample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
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
	private long mDelay;
	private int minw = 0, minh = 0;
	private int mMinAE, mMaxAE;
	private int mAEStep;
	private String mWB;
	private String mISO;

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

	public void takePicture(ListView imageList, long delay, int minAE,
			int maxAE, int aeStep, String wb, String iso) {
		if (null == mContext || null == mCamera) {
			return;
		}
		mImageList = imageList;
		mDelay = delay;
		mMinAE = minAE;
		mMaxAE = maxAE;
		mAEStep = aeStep;
		mWB = wb;
		mISO = iso;
		// mCamera.startPreview();

		try {
			Parameters cp = mCamera.getParameters();
			List<Size> sl = cp.getSupportedPictureSizes();

			// now that you have the list of supported sizes, pick one and set
			// it back to the parameters...
			int w, h, nC = 0;
			int prod = 0, minprod = 0;
			for (Size s : sl) {
				// if s.width meets whatever criteria you want set it to your w
				// and s.height meets whatever criteria you want for your h
				w = s.width;
				h = s.height;
				prod = w * h;
				if (nC == 0) {
					minw = w;
					minh = h;
					minprod = minw * minh;
				}
				if (prod > minprod) {
					minw = w;
					minh = h;
				}
				nC = nC + 1;
				Log.i(TAG, "Got supported width = " + w + " and height = " + h);

			}
			Log.i(TAG, "Using min width = " + minw + " and height = " + minh);
			cp.setPictureSize(minw, minh);
			mCamera.setParameters(cp);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set Focus" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | Picture Size",
					Toast.LENGTH_SHORT).show();
		}

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
			cp.setWhiteBalance(mWB);
			mCamera.setParameters(cp);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set White Balance" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | WB",
					Toast.LENGTH_SHORT).show();
		}

		if(mISO != null){
			Parameters cp = mCamera.getParameters();
			cp.set("iso", mISO);
			mCamera.setParameters(cp);
		} else {
			Log.i(TAG, "ISO Not supported");
		}
		/*try {
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
		}*/

		try {
			Parameters cp = mCamera.getParameters();
			int min = cp.getMinExposureCompensation();
			int max = cp.getMaxExposureCompensation();
			float step = cp.getExposureCompensationStep();
			Log.i("Amit", "Min Exp : " + min + " | Max Exp : " + max
					+ "  |  Step : " + step + " AutoExposure Lock Supported : "
					+ cp.isAutoExposureLockSupported());
			if ((mMinAE == 0 && mMaxAE == 0) || mAEStep == 0) {
				aeMinValue = Math.round(min * step);
				aeMaxValue = Math.round(max * step);
			} else {
				aeMinValue = mMinAE;
				aeMaxValue = mMaxAE;
			}
			cp.setAutoExposureLock(false);
			mCamera.setParameters(cp);
			bitmapList = new ArrayList<ImageItem>();
			if (mImageList != null)
				mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));
			captureImage(aeMinValue, aeMaxValue);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set Exposure" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | AE",
					Toast.LENGTH_SHORT).show();
		}
	}
	
	public void takeSinglePicture(ListView imageList, final long delay){
		if (null == mContext || null == mCamera) {
			return;
		}
		mImageList = imageList;
		try {
			Parameters cp = mCamera.getParameters();
			List<Size> sl = cp.getSupportedPictureSizes();

			// now that you have the list of supported sizes, pick one and set
			// it back to the parameters...
			int w, h, nC = 0;
			int prod = 0, minprod = 0;
			for (Size s : sl) {
				// if s.width meets whatever criteria you want set it to your w
				// and s.height meets whatever criteria you want for your h
				w = s.width;
				h = s.height;
				prod = w * h;
				if (nC == 0) {
					minw = w;
					minh = h;
					minprod = minw * minh;
				}
				if (prod > minprod) {
					minw = w;
					minh = h;
				}
				nC = nC + 1;
				Log.i(TAG, "Got supported width = " + w + " and height = " + h);

			}
			Log.i(TAG, "Using min width = " + minw + " and height = " + minh);
			cp.setPictureSize(minw, minh);
			mCamera.setParameters(cp);
		} catch (RuntimeException e) {
			Log.e(TAG, "Can not Set Focus" + e.getMessage());
			Toast.makeText(mContext, "Camera Not Supported | Picture Size",
					Toast.LENGTH_SHORT).show();
		}
		bitmapList = new ArrayList<ImageItem>();
		if(mImageList != null)
			mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (safeToTakePicture) {
			try {
				mCamera.takePicture(null, null, new Camera.PictureCallback() {	
					@Override
					public void onPictureTaken(byte[] data, Camera camera) {
						Parameters cp = mCamera.getParameters();
						ImageItem item = new ImageItem();
						item.imageFile = createDirectoryAndSaveFile(data, String.format("delay_%d_ev_%d.jpeg", delay, cp.getExposureCompensation()));
						addImageToGallery(item.imageFile);
						// item.imageBitMap = bm;
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
						if(mImageList != null)
							mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));			
					}
				});
				safeToTakePicture = false;
			} catch (RuntimeException e) {
				Log.i(TAG, "Not able to take picture");
			} finally {
			}
		}
	}
	
	public void setWB(String wb){
		Parameters p = mCamera.getParameters();
		p.setWhiteBalance(wb);
		mCamera.setParameters(p);
	}
	
	public void setISO(String ISO){
		Parameters p = mCamera.getParameters();
		p.set("iso", ISO);
		mCamera.setParameters(p);
	}
	
	public void setEV(double ev){
		Parameters p = mCamera.getParameters();
		p.setExposureCompensation((int)Math.round(ev));
		mCamera.setParameters(p);
	}

	private void captureImage(int value, int maxValue) throws RuntimeException {
		if (value > maxValue) {
			if(mImageList != null)
				mImageList.setAdapter(new MyImageAdapter(bitmapList, mContext));
			Toast.makeText(mContext,
					"pictures are taken, you can see images in folder",
					Toast.LENGTH_SHORT).show();
			return;
		}
		Parameters cp = mCamera.getParameters();
		cp.setExposureCompensation(value);
		mCamera.setParameters(cp);
		String picname = String.format("delay_%d_ev_%d.jpg", mDelay, value);
		mFile = new File(mContext.getExternalFilesDir(null), picname);
		try {
			Thread.sleep(mDelay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
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
			
			Parameters cp = mCamera.getParameters();
			ImageItem item = new ImageItem();
			item.imageFile = createDirectoryAndSaveFile(data, String.format("delay_%d_ev_%d.jpeg", mDelay, aeMinValue));
			addImageToGallery(item.imageFile);
			// item.imageBitMap = bm;
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
			if ((mMinAE == 0 && mMaxAE == 0) || mAEStep == 0) {
				aeMinValue = aeMinValue + 1;
			} else {
				aeMinValue = aeMinValue + mAEStep;
			}
			captureImage(aeMinValue, aeMaxValue);
			// setTestFinished(mFile);
		}
	};

	private File createDirectoryAndSaveFile(byte[] data, String fileName) {

		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM ), "Samplytics_Testing");
		//if this "JCGCamera folder does not exist
		if (!mediaStorageDir.exists()) {
			//if you cannot make this folder return
			if (!mediaStorageDir.mkdirs()) {
				return null;
			} else{
				mediaStorageDir.setReadable(true, false);
				
			}
		}

		File file = new File(mediaStorageDir.getPath() + File.separator, fileName);
		if (file.exists()) {
			file.delete();
		}
		try {
			FileOutputStream out = new FileOutputStream(file);
			out.write(data);
			// imageToSave.compress(Bitmap.CompressFormat.JPEG, 100, out);
			out.flush();
			out.close();
			file.setReadable(true, false);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return file;
	}
	
	private void addImageToGallery(File file) {
		ContentValues values = new ContentValues();
		values.put(Media.TITLE, file.getName());
		values.put(Media.DESCRIPTION, "desc");
		values.put(Images.Media.DATE_TAKEN, Calendar.getInstance()
				.getTimeInMillis());
		values.put(Images.Media.MIME_TYPE, "image/jpeg");
		values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
		mContext.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
	}

	@Override
	public int[] getAWBModes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int toFocalToProgeres(float focal) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void takePicture() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public float calculateFocal(int progress) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void changeFocal(float focal) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setISO(int iso) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setAE(double progress) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setWhiteBalaceMode(int mode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Range<Integer> getISORange() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Range<Long> getAERange() {
		// TODO Auto-generated method stub
		return null;
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
			LayoutInflater inflater = (LayoutInflater) mContext
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.image_item, null);
		}
		ImageItem item = getItem(position);
		ImageView imageView = ((ImageView) convertView.findViewById(R.id.captured_image));
		 BitmapFactory.Options options = new BitmapFactory.Options();
         options.inSampleSize = 2;
         Bitmap myBitmap = BitmapFactory.decodeFile(item.imageFile.getAbsolutePath(), options);
         imageView.setImageBitmap(myBitmap);
		((TextView) convertView.findViewById(R.id.ae)).setText(item.ae);
		if (position == 0) {
			((TextView) convertView.findViewById(R.id.focus)).setText(item.focus);
			((TextView) convertView.findViewById(R.id.wb)).setText(item.wb);
			((TextView) convertView.findViewById(R.id.iso)).setText(item.iso);
			((TextView) convertView.findViewById(R.id.focus)).setVisibility(View.VISIBLE);
			((TextView) convertView.findViewById(R.id.wb)).setVisibility(View.VISIBLE);
			((TextView) convertView.findViewById(R.id.iso)).setVisibility(View.VISIBLE);
		} else {
			((TextView) convertView.findViewById(R.id.focus)).setVisibility(View.GONE);
			((TextView) convertView.findViewById(R.id.wb)).setVisibility(View.GONE);
			((TextView) convertView.findViewById(R.id.iso)).setVisibility(View.GONE);
		}

		return convertView;
	}
}
