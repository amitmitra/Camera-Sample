package com.exam.camerasample;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
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
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

public class DLSRCamera {
	private static Context mContext;
	private CameraCharacteristics mCharacteristics;
	private CaptureRequest.Builder mPreviewRequestBuilder;
	private CaptureRequest mPreviewRequest;
	private CameraCaptureSession mCaptureSession;
	private Handler mBackgroundHandler;
	private int mState = STATE_PREVIEW;
	
	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}
	
	/**
	 * Camera state: Showing camera preview.
	 */
	private static final int STATE_PREVIEW = 0;

	/**
	 * Camera state: Waiting for the focus to be locked.
	 */
	private static final int STATE_WAITING_LOCK = 1;
	/**
	 * Camera state: Waiting for the exposure to be precapture state.
	 */
	private static final int STATE_WAITING_PRECAPTURE = 2;
	/**
	 * Camera state: Waiting for the exposure state to be something other than precapture.
	 */
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;
	/**
	 * Camera state: Picture was taken.
	 */
	private static final int STATE_PICTURE_TAKEN = 4;
	
	public DLSRCamera(Context appcontext){
		mContext = appcontext;
		startBackgroundThread();
	}
	private CameraDevice mCameraDevice;
	private HandlerThread mBackgroundThread;
	
	/*private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
			openCamera();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
			//configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture) {
		}
	};*/
	
	private void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("CameraBackground");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(CameraDevice cameraDevice) {
			// This method is called when the camera is opened.  We start camera preview here.
			//mCameraOpenCloseLock.release();
			Log.d("KB", "onOpened");
			mCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(CameraDevice cameraDevice) {
			//mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(CameraDevice cameraDevice, int error) {
			//mCameraOpenCloseLock.release();
			Log.d("KB", "onError: " + error);
			cameraDevice.close();
			mCameraDevice = null;			
		}
	};
	
	private CameraCaptureSession.CaptureCallback mCaptureCallback
	= new CameraCaptureSession.CaptureCallback() {

		private void process(CaptureResult result) {
			switch (mState) {
			case STATE_PREVIEW: {
				// We have nothing to do when the camera preview is working normally.
				break;
			}
			case STATE_WAITING_LOCK: {
				int afState = result.get(CaptureResult.CONTROL_AF_STATE);
				if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
						CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null ||
							aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
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
				if (aeState == null ||
						aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
						aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
					mState = STATE_WAITING_NON_PRECAPTURE;
				}
				break;
			}
			case STATE_WAITING_NON_PRECAPTURE: {
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
					mState = STATE_PICTURE_TAKEN;
					captureStillPicture();
				}
				break;
			}
			}
		}

		@Override
		public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
				CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			process(result);
		}

	};
	private ImageReader mImageReader;
	private String getAvailableCamera() {
		String availableCameraId = null;
		CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
		try {
			for (String cameraId : manager.getCameraIdList()) {
				mCharacteristics = manager.getCameraCharacteristics(cameraId);

				// We don't use a front facing camera in this sample.
				if (mCharacteristics.get(CameraCharacteristics.LENS_FACING)
						== CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}
				availableCameraId = cameraId;
				
				StreamConfigurationMap map = mCharacteristics.get(
						CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(
						Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
						new CompareSizesByArea());
				mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
						ImageFormat.JPEG, /*maxImages*/2);
				mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
			}			
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return availableCameraId;
	}	
	
	private void createCameraPreviewSession() {
		try {
			//SurfaceTexture texture = mTextureView.getSurfaceTexture();
			//assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
		//	texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			
			SurfaceTexture surfaceTexture = new SurfaceTexture(0);
			surfaceTexture.setOnFrameAvailableListener(new OnFrameAvailableListener() {
				
				@Override
				public void onFrameAvailable(SurfaceTexture surfaceTexture) {
					Log.d("KB", "onFrameAvailable");
					takePicture();
				}
			}, new Handler(new Callback() {
				
				@Override
				public boolean handleMessage(Message msg) {
					Log.d("KB", "handleMessage");
					return true;
				}
			}));
			
			Surface surface = new Surface(surfaceTexture);

			// We set up a CaptureRequest.Builder with the output Surface.
			mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

				@Override
				public void onConfigured(CameraCaptureSession cameraCaptureSession) {
					// The camera is already closed
					if (null == mCameraDevice) {
						return;
					}

					// When the session is ready, we start displaying the preview.
					mCaptureSession = cameraCaptureSession;
					try {
						// Auto focus should be continuous for camera preview.
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
								CaptureRequest.CONTROL_AF_MODE_OFF);
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
								CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
								CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
								CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
						// Flash is automatically enabled when necessary.
						//mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
							//	CaptureRequest.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
						mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
								CaptureRequest.CONTROL_AE_MODE_OFF);
						
						float minimumLens = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
						float num = (((float) 5) * minimumLens / 100);
						mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,
								num);					

						
						// Finally, we start displaying the camera preview.
						mPreviewRequest = mPreviewRequestBuilder.build();
						mCaptureSession.setRepeatingRequest(mPreviewRequest,
								mCaptureCallback, mBackgroundHandler);
					} catch (CameraAccessException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
					//showToast("Failed");
				}
			}, null
					);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Lock the focus as the first step for a still image capture.
	 */
	private void lockFocus() {
		try {
			// This is how to tell the camera to lock focus.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			mState = STATE_WAITING_LOCK;
			mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
					mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Run the precapture sequence for capturing a still image. This method should be called when we
	 * get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
	 */
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

	/**
	 * Capture a still picture. This method should be called when we get a response in
	 * {@link #mCaptureCallback} from both {@link #lockFocus()}.
	 */
	private void captureStillPicture() {
		try {			
			Surface surface = new Surface(new SurfaceTexture(0));
			// This is the CaptureRequest.Builder that we use to take a picture.
			final CaptureRequest.Builder captureBuilder =
					mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(surface);

			// Use the same AE and AF modes as the preview.
			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

			// Orientation
			WindowManager wm = (WindowManager) mContext
			        .getSystemService(Context.WINDOW_SERVICE);
			int rotation = wm.getDefaultDisplay().getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,   ORIENTATIONS.get(rotation));

			CameraCaptureSession.CaptureCallback CaptureCallback
			= new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
						TotalCaptureResult result) {
					//showToast("Saved: " + mFile);
					unlockFocus();
				}
			};

			mCaptureSession.stopRepeating();
			mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Unlock the focus. This method should be called when still image capture sequence is finished.
	 */
	private void unlockFocus() {
		try {
			// Reset the autofucos trigger
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
					mBackgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			mState = STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
					mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
	
	private void takePicture() {
		lockFocus();
	}
	
	public void openCamera() {
		//setUpCameraOutputs(width, height);
		//configureTransform(width, height);
		//Activity activity = getActivity();
		String cameraid = getAvailableCamera();
		CameraManager manager = (CameraManager)mContext.getSystemService(Context.CAMERA_SERVICE);
		try {
			//if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				//throw new RuntimeException("Time out waiting to lock camera opening.");
		//	}
			manager.openCamera(cameraid, mStateCallback, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		} 
	}

	/**
	 * Closes the current {@link CameraDevice}.
	 */
	private void closeCamera() {
		try {
			//mCameraOpenCloseLock.acquire();
			if (null != mCaptureSession) {
				mCaptureSession.close();
				mCaptureSession = null;
			}
			if (null != mCameraDevice) {
				mCameraDevice.close();
				mCameraDevice = null;
			}
			
		} finally {
			//mCameraOpenCloseLock.release();
		}
	}
	
	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

		@Override
		public void onImageAvailable(ImageReader reader) {
			Log.d("KB", "onImageAvailable");
			File file = new File(mContext.getExternalFilesDir(null), "pic.jpg");
			mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), file));
			closeCamera();
		}
	};

	static class CompareSizesByArea implements Comparator<Size> {

		@Override
		public int compare(Size lhs, Size rhs) {
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
					(long) rhs.getWidth() * rhs.getHeight());
		}

	}
	
	private static class ImageSaver implements Runnable {

		private static final String TAG = "_ImageSaver_";
		/**
		 * The JPEG image
		 */
		private final Image mImage;
		/**
		 * The file we save the image into.
		 */
		private final File mFile;

		public ImageSaver(Image image, File file) {
			mImage = image;
			mFile = file;
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
				
				ContentValues values = new ContentValues();
			    values.put(Media.TITLE, "pic.jpg");
			    values.put(Media.DESCRIPTION, "desc"); 
			    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
			    values.put(Images.Media.MIME_TYPE, "image/jpeg");
			    values.put(MediaStore.MediaColumns.DATA, mFile.getAbsolutePath());
			    mContext.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
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
}
