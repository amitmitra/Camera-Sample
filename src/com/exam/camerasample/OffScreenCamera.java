package com.exam.camerasample;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.exam.camerasample.DLSRCamera.CompareSizesByArea;

public class OffScreenCamera implements AppCamera{
	
	private static final String TAG = OffScreenCamera.class.getSimpleName();

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	private static final int STATE_PREVIEW = 0;
	private static final int STATE_WAITING_LOCK = 1;
	private static final int STATE_WAITING_PRECAPTURE = 2;
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;
	private static final int STATE_PICTURE_TAKEN = 4;

	private Semaphore mCameraOpenCloseLock = new Semaphore(1);
	private int mState = STATE_PREVIEW;
	private TextureView mTextureView;
	private static Context mContext;
	private Size mPreviewSize;
	private ImageReader mImageReader;
	private Handler mBackgroundHandler;
	private String mCameraId;
	private CameraDevice mCameraDevice;
	private static File mFile;
	private static CaptureRequest.Builder mPreviewRequestBuilder;
	private CameraCaptureSession mCaptureSession;
	private CaptureRequest mPreviewRequest;
	private HandlerThread mBackgroundThread;
	private ListView mImageList;
	
	private CameraCharacteristics mCharacteristics;
	
	public OffScreenCamera(Context context) {
		mContext = context;
	}

	public void start(TextureView textureView) {
		if (textureView == null) {
			textureView = new TextureView(mContext);
		}
		mTextureView = textureView;
		startBackgroundThread();
		if (mTextureView.isAvailable()) {
			openCamera(mTextureView.getWidth(), mTextureView.getHeight());
		} else {
			mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
		}
	}

	private void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("CameraBackground");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture) {
		}
	};

	private void configureTransform(int viewWidth, int viewHeight) {
		//		Activity activity = getActivity();
		if (null == mTextureView || null == mPreviewSize || null == mContext) {
			return;
		}
		int rotation = 0; //mContext.getWindowManager().getDefaultDisplay().getRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY);
		}
		mTextureView.setTransform(matrix);
	}

	private void setUpCameraOutputs(int width, int height) {
		CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
		try {
			for (String cameraId : manager.getCameraIdList()) {
				CameraCharacteristics characteristics
				= manager.getCameraCharacteristics(cameraId);
				mCharacteristics = characteristics;
				Log.i("CAM SETTINGS", "TRF");
				// We don't use a front facing camera in this sample.
				if (characteristics.get(CameraCharacteristics.LENS_FACING)
						== CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}

				StreamConfigurationMap map = characteristics.get(
						CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),new CompareSizesByArea());
				mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
						ImageFormat.JPEG, /*maxImages*/2);
				mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

				// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
				// garbage capture data.
				mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
						width, height, largest);

				// We fit the aspect ratio of TextureView to the size of preview we picked.
				int orientation = mContext.getResources().getConfiguration().orientation;
				/*if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
					mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
				} else {
					mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
				}*/

				mCameraId = cameraId;
				return;
			}
		} catch (CameraAccessException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
			// Currently an NPE is thrown when the Camera2API is used but not supported on the
			// device this code runs.
			//			new ErrorDialog().show(getFragmentManager(), "dialog");
		}
	}

	private void openCamera(int width, int height) {
		setUpCameraOutputs(width, height);
		configureTransform(width, height);
		//	        Activity activity = getActivity();
		CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
		try {
			if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
		}
	}
	

	
	private void buildPreviewRequest() {
		mPreviewRequest = mPreviewRequestBuilder.build();
		try {
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void removeAutoFocus(CaptureRequest.Builder captureBuilder) {
		captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
				CaptureRequest.CONTROL_AF_MODE_OFF);
		captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
				CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
		captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
				CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
		captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
				CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
		captureBuilder.set(CaptureRequest.CONTROL_AE_MODE , 
				CaptureRequest.CONTROL_AE_MODE_OFF);
		captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
		captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
	}
	
	
	private void createCameraPreviewSession() {
		try {
			SurfaceTexture texture = mTextureView.getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
			texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			Surface surface = new Surface(texture);

			// We set up a CaptureRequest.Builder with the output Surface.
			mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
					new CameraCaptureSession.StateCallback() {

				@Override
				public void onConfigured(CameraCaptureSession cameraCaptureSession) {
					// The camera is already closed
					if (null == mCameraDevice) {
						return;
					}

					// When the session is ready, we start displaying the preview.
					mCaptureSession = cameraCaptureSession;
					try {
						Log.i("OSC", "trying to remove focus");
						removeAutoFocus(mPreviewRequestBuilder);
						
						// Finally, we start displaying the camera preview.
						mPreviewRequest = mPreviewRequestBuilder.build();
						mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
						
					} catch (CameraAccessException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
										Log.e("Failed" , "Cam Config");
				}
			}, null
					);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

		private void process(CaptureResult result) {
			switch (mState) {
			case STATE_PREVIEW: {
				// We have nothing to do when the camera preview is working normally.
				break;
			}
			case STATE_WAITING_LOCK: {
				int afState = result.get(CaptureResult.CONTROL_AF_STATE);
				if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
						mState = STATE_WAITING_NON_PRECAPTURE;
						captureStillPicture(  0 , 9.0 , 8.0 , 100 , 6); // Varun asks::does this part of code execute?
					} else {
						runPrecaptureSequence();
					}
				}
				break;
			}
			case STATE_WAITING_PRECAPTURE: {
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
					mState = STATE_WAITING_NON_PRECAPTURE;
				}
				break;
			}
			case STATE_WAITING_NON_PRECAPTURE: {
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
					mState = STATE_PICTURE_TAKEN;
					captureStillPicture( 0 , 9.0 , 8.0 , 100 , 6);
				}
				break;
			}
			}
		}

		@Override
		public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//			Log.e("KB", "onCaptureCompleted");
			process(result);
		}
	};

	private void runPrecaptureSequence() {
		try {
			// This is how to tell the camera to trigger.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
					CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the precapture sequence to be set.
			mState = STATE_WAITING_PRECAPTURE;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
					mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void captureStillPicture(int picnumber , double focallength , double exposure , int iso , int whitebalance) {
		try {
			if (null == mContext || null == mCameraDevice) {
				return;
			}
			String picname = String.format("pic%d.jpg", picnumber);
			mFile = new File(mContext.getExternalFilesDir(null), picname);
			// This is the CaptureRequest.Builder that we use to take a picture.
			final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mImageReader.getSurface());
			removeAutoFocus(captureBuilder);
			
			captureBuilder.set(CaptureRequest.JPEG_QUALITY, Byte.valueOf("100"));
			try{
				mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);
			} catch (RuntimeException e){
				Log.e(TAG, "Not Able to Set FOCUS : " + e.getMessage());
				Toast.makeText(mContext, "Camera Not Supported", Toast.LENGTH_SHORT).show();
			}
			
			try{
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT);
			} catch (RuntimeException e){
				Log.e(TAG, "Not able to set White Balance : " + e.getMessage());
				Toast.makeText(mContext, "Camera Not Supported", Toast.LENGTH_SHORT).show();
			}
			
			try{
				Range<Integer> range = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
				if(range != null){
					int minValue = range.getLower();
					mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, minValue);
				} else {
					//take n images and run a algorithm
				}
			} catch (RuntimeException e){
				Log.e(TAG, "Not able to set ISO : " + e.getMessage());
				Toast.makeText(mContext, "Camera Not Supported", Toast.LENGTH_SHORT).show();
			}
			
			try{
				mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)0.125);
			} catch (RuntimeException e){
				Log.e(TAG, "Not able to set Exposure : " + e.getMessage());
				Toast.makeText(mContext, "Camera Not Supported", Toast.LENGTH_SHORT).show();
			}
			
			Log.i(TAG, "FOCAL_DISTANCE : " + mPreviewRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE));
			Log.i(TAG, "WHITE BALANCE : " + mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE));
			Log.i(TAG, "ISO : " + mPreviewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
			Log.i(TAG, "EXPOSURE : " + mPreviewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME));

			// Orientation
			// int rotation = mContext.getWindowManager().getDefaultDisplay().getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 1/*ORIENTATIONS.get(rotation)*/);

			CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
					Log.e("_OffScreenCamera_", "Saved");
					unlockFocus();
					
				}
			};

			mCaptureSession.stopRepeating();
			mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	public void takePicture(ListView imageList) {
		mImageList = imageList;
		captureStillPicture( 0 , 0 , 0 , 0 , 0);
	}

	private void lockFocus() {
		try {
			// This is how to tell the camera to lock focus.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			mState = STATE_WAITING_LOCK;
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void unlockFocus() {
		try {
			// Reset the autofucos trigger
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			mState = STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

		@Override
		public void onImageAvailable(ImageReader reader) {
			Log.e("KB", "onImageAvailable");
			mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile, mImageList));
		}
	};

	private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
		// Collect the supported resolutions that are at least as big as the preview Surface
		List<Size> bigEnough = new ArrayList<Size>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices) {
			if (option.getHeight() == option.getWidth() * h / w &&
					option.getWidth() >= width && option.getHeight() >= height) {
				bigEnough.add(option);
			}
		}

		// Pick the smallest of those, assuming we found any
		if (bigEnough.size() > 0) {
			return Collections.min(bigEnough, new CompareSizesByArea());
		} else {
			Log.e("KB", "Couldn't find any suitable preview size");
			return choices[0];
		}
	}


	private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(CameraDevice cameraDevice) {
			Log.e("KB", "onOpened");
			// This method is called when the camera is opened.  We start camera preview here.
			mCameraOpenCloseLock.release();
			mCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(CameraDevice cameraDevice, int error) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
			if (null != mContext) {
				//				mContext.finish();
			}
		}
	};

	private static class ImageSaver implements Runnable {

		/**
		 * The JPEG image
		 */
		private final Image mImage;
		/**
		 * The file we save the image into.
		 */
		private final File mFile;
		
		private ListView mImageList;

		public ImageSaver(Image image, File file, ListView imageList) {
			mImage = image;
			mFile = file;
			mImageList = imageList;
		}

		@Override
		public void run() {
			ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
			byte[] bytes = new byte[buffer.remaining()];
			Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
			
			LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View imageItem = inflater.inflate(R.layout.image_item, null);
			((ImageView)imageItem.findViewById(R.id.captured_image)).setImageBitmap(bm);
			((TextView)imageItem.findViewById(R.id.focus)).setText(mPreviewRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE) + "");
			((TextView)imageItem.findViewById(R.id.wb)).setText(mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE));
			((TextView)imageItem.findViewById(R.id.iso)).setText(mPreviewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
			((TextView)imageItem.findViewById(R.id.ae)).setText(mPreviewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME)+"");
			
			mImageList.addView(imageItem);
			
			/*buffer.get(bytes);
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(mFile);
				output.write(bytes);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				mImage.close();
				if (null != output) {
					try {
						output.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}*/
		}
	}
	
	private static void addImageToGallery() {
		ContentValues values = new ContentValues();
	    values.put(Media.TITLE, mFile.getName());
	    values.put(Media.DESCRIPTION, "desc"); 
	    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
	    values.put(Images.Media.MIME_TYPE, "image/jpeg");
	    values.put(MediaStore.MediaColumns.DATA, mFile.getAbsolutePath());
	    mContext.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
	}
	
	/*private static void broadcastCaptureFinished() {
		Intent localIntent = new Intent(ServiceConstants.ACTION_PHOTO_CAPTURE);
		localIntent.putExtra(ServiceConstants.CAPTURE_STATUS, "SUCCESS");
		localIntent.putExtra(ServiceConstants.IMAGE_PATH, mFile.getAbsolutePath());
		LocalBroadcastManager.getInstance(mContext).sendBroadcast(localIntent);
	}*/
	
	private static void setTestFinished() {
		/*AppPreferences ap = GlobalAccess.getAppPref();
		ap.setTestFinished();
		ap.setTestTestResultImagePath(mFile.getAbsolutePath());*/
	}

	public void closeCamera() {
		try {
			mCameraOpenCloseLock.acquire();
			if (null != mCaptureSession) {
				mCaptureSession.close();
				mCaptureSession = null;
			}
			if (null != mCameraDevice) {
				mCameraDevice.close();
				mCameraDevice = null;
			}
			if (null != mImageReader) {
				mImageReader.close();
				mImageReader = null;
			}
			
			//WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
			//wm.removeView(mTextureView);
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
		} finally {
			mCameraOpenCloseLock.release();
		}
	}

	private void stopBackgroundThread() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
