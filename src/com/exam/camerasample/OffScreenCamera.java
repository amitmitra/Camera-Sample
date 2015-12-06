package com.exam.camerasample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.content.Context;
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
import android.view.Surface;
import android.view.TextureView;
import android.widget.ListView;

public class OffScreenCamera implements AppCamera{

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
	private CaptureRequest.Builder mPreviewRequestBuilder;
	private CameraCaptureSession mCaptureSession;
	private CaptureRequest mPreviewRequest;
	private HandlerThread mBackgroundThread;
	private ICameraOperation mCameraopenedListener;
	

	public OffScreenCamera(Context context, ICameraOperation cameraopenedListener) {
		mContext = context;
		mCameraopenedListener = cameraopenedListener;
	}

	public void start(TextureView textureView) {
		if (textureView == null) {
			textureView = new TextureView(mContext);
		}
		mTextureView = textureView;
		startBackgroundThread();

		// When the screen is turned off and turned back on, the SurfaceTexture
		// is already
		// available, and "onSurfaceTextureAvailable" will not be called. In
		// that case, we can open
		// a camera and start preview from here (otherwise, we wait until the
		// surface is ready in
		// the SurfaceTextureListener).
		if (mTextureView.isAvailable()) {
			// Log.d("KB", "onSurfaceTextureAvailable isAvailable");
			openCamera(mTextureView.getWidth(), mTextureView.getHeight());
		} else {
			// Log.d("KB",
			// "onSurfaceTextureAvailable setSurfaceTextureListener");
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
		public void onSurfaceTextureAvailable(SurfaceTexture texture,
				int width, int height) {
			// Log.d("KB", "onSurfaceTextureAvailable");
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture,
				int width, int height) {
			// Log.d("KB", "onSurfaceTextureSizeChanged");
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
			// Log.d("KB", "onSurfaceTextureDestroyed");
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture) {
			// Log.d("KB", "onSurfaceTextureUpdated");
		}
	};
	private CameraCharacteristics mCharacteristics;

	private void configureTransform(int viewWidth, int viewHeight) {
		if (null == mTextureView || null == mPreviewSize || null == mContext) {
			return;
		}
		int rotation = 0; // mContext.getWindowManager().getDefaultDisplay().getRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(),
				mPreviewSize.getWidth());
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY
					- bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max(
					(float) viewHeight / mPreviewSize.getHeight(),
					(float) viewWidth / mPreviewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY);
		}
		mTextureView.setTransform(matrix);
	}

	private void setUpCameraOutputs(int width, int height) {
		CameraManager manager = (CameraManager) mContext
				.getSystemService(Context.CAMERA_SERVICE);
		try {
			for (String cameraId : manager.getCameraIdList()) {
				mCharacteristics = manager.getCameraCharacteristics(cameraId);

				// We don't use a front facing camera in this sample.
				if (mCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}

				StreamConfigurationMap map = mCharacteristics
						.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(
						Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
						new CompareSizesByArea());
				mImageReader = ImageReader.newInstance(largest.getWidth(),
						largest.getHeight(), ImageFormat.JPEG, /* maxImages */
						2);
				mImageReader.setOnImageAvailableListener(
						mOnImageAvailableListener, mBackgroundHandler);

				// Danger, W.R.! Attempting to use too large a preview size
				// could exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but
				// the storage of
				// garbage capture data.
				mPreviewSize = chooseOptimalSize(
						map.getOutputSizes(SurfaceTexture.class), width,
						height, largest);

				// We fit the aspect ratio of TextureView to the size of preview
				// we picked.
				int orientation = mContext.getResources().getConfiguration().orientation;
				/*
				 * if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				 * mTextureView.setAspectRatio(mPreviewSize.getWidth(),
				 * mPreviewSize.getHeight()); } else {
				 * mTextureView.setAspectRatio(mPreviewSize.getHeight(),
				 * mPreviewSize.getWidth()); }
				 */

				mCameraId = cameraId;
				return;
			}
		} catch (CameraAccessException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
			// Currently an NPE is thrown when the Camera2API is used but not
			// supported on the
			// device this code runs.
			// new ErrorDialog().show(getFragmentManager(), "dialog");
		}
	}

	private void openCamera(int width, int height) {
		setUpCameraOutputs(width, height);
		configureTransform(width, height);
		CameraManager manager = (CameraManager) mContext
				.getSystemService(Context.CAMERA_SERVICE);
		try {
			if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw new RuntimeException(
						"Time out waiting to lock camera opening.");
			}
			manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			throw new RuntimeException(
					"Interrupted while trying to lock camera opening.", e);
		}
	}

	public void createCameraPreviewSession() {
		try {
			SurfaceTexture texture = mTextureView.getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera
			// preview we want.
			texture.setDefaultBufferSize(mPreviewSize.getWidth(),
					mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			Surface surface = new Surface(texture);

			// We set up a CaptureRequest.Builder with the output Surface.
			mPreviewRequestBuilder = mCameraDevice
					.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			mCameraDevice.createCaptureSession(
					Arrays.asList(surface, mImageReader.getSurface()),
					new CameraCaptureSession.StateCallback() {

						@Override
						public void onConfigured(
								CameraCaptureSession cameraCaptureSession) {
							// The camera is already closed
							if (null == mCameraDevice) {
								return;
							}

							// When the session is ready, we start displaying
							// the preview.
							mCaptureSession = cameraCaptureSession;
							try {

								removeAutoFocus(mPreviewRequestBuilder);

								mPreviewRequest = mPreviewRequestBuilder
										.build();
								mCaptureSession.setRepeatingRequest(
										mPreviewRequest, mCaptureCallback,
										mBackgroundHandler);
								if (mCameraopenedListener != null)
									mCameraopenedListener.onCameraIntialized();
							} catch (CameraAccessException e) {
								e.printStackTrace();
							}
						}

						@Override
						public void onConfigureFailed(
								CameraCaptureSession cameraCaptureSession) {
							// showToast("Failed");
						}
					}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

		private void process(CaptureResult result) {
			switch (mState) {
			case STATE_PREVIEW: {
				// We have nothing to do when the camera preview is working
				// normally.
				break;
			}
			case STATE_WAITING_LOCK: {
				int afState = result.get(CaptureResult.CONTROL_AF_STATE);
				if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
						|| CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result
							.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null
							|| aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
						mState = STATE_WAITING_NON_PRECAPTURE;
						captureStillPicture();
					} else {
						runPrecaptureSequence();
					}
				}
				break;
			}
			case STATE_WAITING_PRECAPTURE: {
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null
						|| aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
						|| aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
					mState = STATE_WAITING_NON_PRECAPTURE;
				}
				break;
			}
			case STATE_WAITING_NON_PRECAPTURE: {
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null
						|| aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
					mState = STATE_PICTURE_TAKEN;
					captureStillPicture();
				}
				break;
			}
			}
		}

		@Override
		public void onCaptureProgressed(CameraCaptureSession session,
				CaptureRequest request, CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(CameraCaptureSession session,
				CaptureRequest request, TotalCaptureResult result) {
			// Log.e("KB", "onCaptureCompleted");
			process(result);
		}
	};

	private void runPrecaptureSequence() {
		try {
			// This is how to tell the camera to trigger.
			mPreviewRequestBuilder.set(
					CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
					CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the precapture sequence to be
			// set.
			mState = STATE_WAITING_PRECAPTURE;
			mCaptureSession.capture(mPreviewRequestBuilder.build(),
					mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void captureStillPicture() {
		try {
			if (null == mContext || null == mCameraDevice) {
				return;
			}
			// This is the CaptureRequest.Builder that we use to take a picture.
			final CaptureRequest.Builder captureBuilder = mCameraDevice
					.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mImageReader.getSurface());
			removeAutoFocus(captureBuilder);
			captureBuilder.set(CaptureRequest.JPEG_QUALITY, Byte.valueOf("100"));
			captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mCurrentAE);
			captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mCurrentFocal);
			captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mCurrentWbMode);
			captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mCurrentIso);

			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 1/*
			 * ORIENTATIONS.get
			 * (rotation)
			 */);

			CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(CameraCaptureSession session,
						CaptureRequest request, TotalCaptureResult result) {
					// showToast("Saved: " + mFile);

					Log.e("KB", "Saved");
					unlockFocus();
				}
			};

			mCaptureSession.stopRepeating();
			mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	public void takePicture() {
		// lockFocus();
		captureStillPicture();
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
		captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
				CaptureRequest.CONTROL_AE_MODE_OFF);
		captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
		captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
	}
	
	private void lockFocus() {
		try {
			// This is how to tell the camera to lock focus.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			mState = STATE_WAITING_LOCK;
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
					mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void unlockFocus() {
		try {
			// Reset the autofucos trigger
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			mCaptureSession.capture(mPreviewRequestBuilder.build(),
					mCaptureCallback, mBackgroundHandler);
			// After this, the camera will go back to the normal state of
			// preview.
			mState = STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest,
					mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

		@Override
		public void onImageAvailable(ImageReader reader) {
			Log.e("KB", "onImageAvailable");
			mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
		}
	};

	private static Size chooseOptimalSize(Size[] choices, int width,
			int height, Size aspectRatio) {
		// Collect the supported resolutions that are at least as big as the
		// preview Surface
		List<Size> bigEnough = new ArrayList<Size>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices) {
			if (option.getHeight() == option.getWidth() * h / w
					&& option.getWidth() >= width
					&& option.getHeight() >= height) {
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
			// This method is called when the camera is opened. We start camera
			// preview here.
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
				// mContext.finish();
			}
		}
	};
	private long mCurrentAE;
	private int mCurrentIso;
	private float mCurrentFocal;
	private int mCurrentWbMode;

	private static class ImageSaver implements Runnable {
		/**
		 * The JPEG image
		 */
		private final Image mImage;
		/**
		 * The file we save the image into.
		 */
		private final File mFile;

		public ImageSaver(Image image) {
			mImage = image;
			mFile = ((MainActivity) mContext).getFileName();
		}

		@Override
		public void run() {
			ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(mFile);
				output.write(bytes);
				addImageToGallery(mFile);
				ImageItem item = new ImageItem();
				item.imageFile = mFile;
				MainActivity activity = ((MainActivity) mContext);
				activity.bitmapList.add(item);
				if (activity.camera2captureAll){
					activity.mMinAEValue = activity.mMinAEValue + activity.mAEStep;
					activity.captureAllMethod();
				} else {
					activity.onPictureCaptured();
				}
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
			}
		}
	}

	private static void addImageToGallery(File file) {
		ContentValues values = new ContentValues();
		values.put(Media.TITLE, file.getName());
		values.put(Media.DESCRIPTION, "desc");
		values.put(Images.Media.DATE_TAKEN, Calendar.getInstance()
				.getTimeInMillis());
		values.put(Images.Media.MIME_TYPE, "image/jpeg");
		values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
		mContext.getContentResolver()
		.insert(Media.EXTERNAL_CONTENT_URI, values);
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
		} catch (InterruptedException e) {
			throw new RuntimeException(
					"Interrupted while trying to lock camera closing.", e);
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

	static class CompareSizesByArea implements Comparator<Size> {

		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight()
					- (long) rhs.getWidth() * rhs.getHeight());
		}

	}

	public void changeFocal(float focal) {
		mCurrentFocal = focal;
		mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,
				mCurrentFocal);
		Log.e("INPUTS", "Focus : " + mCurrentFocal);
		// Finally, we start displaying the camera preview.
		buildPreviewRequest();
	}
	
	public void setWhiteBalaceMode(int mode) {
		mCurrentWbMode = mode;
		mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mode);
		buildPreviewRequest();
	}
	
	public int[] getAWBModes() {
		return mCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
		
	}
	
	public float calculateFocal(int progress) {
		float minimumLens = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
		return (((float) progress) * minimumLens / 100);
	}
	
	public int toFocalToProgeres(float focal) {
		float minimumLens = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
		return (int)(focal / minimumLens * 100);
	}
	
	public void setAE(double progress) {
		if (progress == 0) return ;
		Range<Long> range3 = mCharacteristics
				.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
		Long max = range3.getUpper();// 10000
		Log.e("INPUTS", "AE  max: " + max);
		Long min = range3.getLower();// 100
		Log.e("INPUTS", "AE  min: " + min);
		//mCurrentAE = ((progress * (max - min)) / 100 + min);
		mCurrentAE = (long) ((long) (1000000000.0 / progress)); 
		Log.e("INPUTS", "AE : " + mCurrentAE);
		mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mCurrentAE);
		buildPreviewRequest();
	}
	
	public Range<Long> getAERange(){
		return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
	}

	public void setISO(int iso) {
		if (iso == 0) return ;
		Range<Integer> range2 = mCharacteristics
				.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
		int max1 = range2.getUpper();// 10000
		int min1 = range2.getLower();// 100
		//mCurrentIso = ((progress * (max1 - min1)) / 100 + min1);
		mCurrentIso = iso;
		Log.e("INPUTS", "ISO : " + mCurrentIso);
		mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,
				mCurrentIso);
		buildPreviewRequest();
	}
	
	public Range<Integer> getISORange(){
		return mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
	}
	
	private void buildPreviewRequest() {
		mPreviewRequest = mPreviewRequestBuilder.build();
		try {
			mCaptureSession.setRepeatingRequest(mPreviewRequest,
					mCaptureCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void takePicture(ListView imageList, long delay, int minAE, int maxAE, int aeStep, String wb, String iso) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void takeSinglePicture(ListView imageList, long delay) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setWB(String wb) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setISO(String ISO) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setEV(double ev) {
		// TODO Auto-generated method stub
		
	}
}
