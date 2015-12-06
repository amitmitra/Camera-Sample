package com.exam.camerasample;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.R.layout;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class MainActivity extends Activity {

	private static final String TAG = MainActivity.class.getSimpleName();

	private TextureView mTextureView;
	private LinearLayout camera1Layout, camera2Layout;
	//for camera1
	//private ListView mImageList;
	private AppCamera mCamera;
	private Button mCaptureButton, mCaptureButton2, setEVButton, singleCaptureButtun;
	private EditText delayText, aeMinValue, aeMaxValue, aeStepValue, evValue;
	private TextView aeValuesView, mISOSupported;
	private Spinner mWhiteBalanceSpinner, mISOSpinner;
	
	//for camera2
	private SeekBar mFocusSeekBar;
	private SeekBar mAeSeekBar;
	private SeekBar mIsoseekbar;
	private View mDipTimerButton;
	private View mDelayCaptureButton;
	private boolean mIsFastCapture;
	private ViewFlipper mViewfliper1, mViewfliper2;
	private EditText mAeEditext, mISOEdittext, mFocusEdittext, mWBEdittext;
	private int[] mAWBmodes;
	private View mPrevSelectedView1;
	private View mPrevSelectedView2;

	private final int MAXBURSTSIZE = 4;
	private int imageCaptureCount = MAXBURSTSIZE;// Max photo taken
	private int patientId = 1;
	private int DIP_TIMER = 15 * 1000; // 15 seconds
	private int CAPTURE_TIMER = 60 * 1000 * 5; // 5 minutes
	private final double[] mExposureTimes = new double[] { 1.2, 2, 4, 6, 8, 15,
			30, 60, 100, 125, 250, 500, 750, 1000, 1500, 2000, 3000, 4000,
			5000, 6000, 8000, 10000, 20000, 30000, 75000 };
	private final int[] mISOs = new int[] { 40, 50, 80, 100, 200, 300, 400,
			600, 800, 1000, 1600, 2000, 3200, 4000, 6400, 8000, 10000 };
	private SeekBar mWBseekbar;

	AppPreferences mAppPreferences;
	public List<ImageItem> bitmapList;
	public boolean camera2captureAll = false;
	public long mDelay;
	public long mMinAEValue;
	public long mMaxAEValue;
	public long mAEStep;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		camera1Layout = (LinearLayout)findViewById(R.id.camera1_layout);
		camera2Layout = (LinearLayout)findViewById(R.id.camera2_layout);
		mTextureView = (TextureView) findViewById(R.id.camera_preview);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Log.d(TAG, "camera2 selected");
			camera1Layout.setVisibility(View.GONE);
			camera2Layout.setVisibility(View.VISIBLE);
			initViews();
		} else {
			Log.d(TAG, "camera1 selected");
			camera1Layout.setVisibility(View.VISIBLE);
			camera2Layout.setVisibility(View.GONE);
			initialize();
		}
	}
	
	@Override
	protected void onDestroy() {
		mCamera.closeCamera();
		super.onDestroy();
	}
	
	@Override
	protected void onPause() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mCamera.closeCamera();
			mAppPreferences.saveAE(mAeSeekBar.getProgress());
			mAppPreferences.saveFocus(mFocusSeekBar.getProgress());
			mAppPreferences.saveISO(mIsoseekbar.getProgress());
			mAppPreferences.saveWBMode(mWBseekbar.getProgress());
		} else {
			mCamera.closeCamera();
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mCamera.start(mTextureView);
	}
	
	private void initViews() {
		mCamera = new OffScreenCamera(this, new ICameraOperation() {
			
			@Override
			public void onCameraIntialized() {
				Range<Long> aeRange = mCamera.getAERange();
				Range<Integer> isoRange = mCamera.getISORange();
				mAWBmodes = mCamera.getAWBModes();
				mAeSeekBar.setMax(mExposureTimes.length - 1);
				mIsoseekbar.setMax(mISOs.length - 1);
				mIsoseekbar.setProgress(mAppPreferences.getISO());
				mAeSeekBar.setProgress(mAppPreferences.getAE());
				mFocusSeekBar.setProgress(mAppPreferences.getFocus());
				mWBseekbar.setMax(mAWBmodes.length - 1);
				mWBseekbar.setProgress(mAppPreferences.getWBMode());
			}
		});
		//mTextureView = (TextureView) findViewById(R.id.texture);
		delayText = (EditText) findViewById(R.id.capture_delay2);
		aeMinValue = (EditText) findViewById(R.id.aeMin2);
		aeMaxValue = (EditText) findViewById(R.id.aeMax2);
		aeStepValue = (EditText) findViewById(R.id.aeStep2);
		mCaptureButton2 = (Button) findViewById(R.id.button2);
		mFocusSeekBar = (SeekBar) findViewById(R.id.focusseekbar);
		mAeSeekBar = (SeekBar) findViewById(R.id.aeseekbar);
		mIsoseekbar = (SeekBar) findViewById(R.id.isoseekbar);
		mWBseekbar = (SeekBar) findViewById(R.id.whiteBalance_seekbar);
		mFocusSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		mAeSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		mIsoseekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		mWBseekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		mDipTimerButton = findViewById(R.id.diptimer_button);
		mDelayCaptureButton = findViewById(R.id.delaycapture_button);
		mViewfliper2 = (ViewFlipper) findViewById(R.id.viewfliper2);
		mAeEditext = (EditText) findViewById(R.id.AE_edittext);
		mISOEdittext = (EditText) findViewById(R.id.ISO_edittext);
		mFocusEdittext = (EditText) findViewById(R.id.FocalLength_edittext);
		mWBEdittext = (EditText) findViewById(R.id.WhiteBalance_edittext);
		//mImageList = (ListView) findViewById(R.id.image_list2);
		mPrevSelectedView2 = findViewById(R.id.timers_tab);
		mPrevSelectedView2.setSelected(true);
		mAppPreferences = new AppPreferences(this);
		setPatientId();
		AppUtility.createTestImageFolders();
		
		mCaptureButton2.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				String delayString = delayText.getText().toString();
				String minAEString = aeMinValue.getText().toString();
				String maxAEString = aeMaxValue.getText().toString();
				String aeStepString = aeStepValue.getText().toString();
				if (minAEString == null || minAEString.isEmpty()
						|| maxAEString == null || maxAEString.isEmpty()
						|| aeStepString == null || aeStepString.isEmpty()) {
					Toast.makeText(MainActivity.this, "Fill all the AE fields", Toast.LENGTH_SHORT).show();
				} else {
					if (delayString == null || delayString.isEmpty()){
						mDelay = 0;
					} else {
						mDelay = Long.parseLong(delayString);
					}
					mMinAEValue = Long.parseLong(minAEString);
					mMaxAEValue = Long.parseLong(maxAEString);
					mAEStep = Long.parseLong(aeStepString);
					camera2captureAll = true;
					bitmapList = new ArrayList<>();
					//mImageList.setAdapter(new MyImageAdapter(bitmapList, MainActivity.this));
					captureAllMethod();
				}
			}
		});
	}
	
	public void captureAllMethod(){
		if(mMinAEValue <= mMaxAEValue){
			mCamera.setAE(mMinAEValue);
			try {
				Thread.sleep(mDelay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			mCamera.takePicture();
		} else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					//mImageList.setAdapter(new MyImageAdapter(bitmapList, MainActivity.this));
				}
			});
			showToast(this, "Capture finished!");
			camera2captureAll = false;
		}
	}
	
	public void onTab2Clicked(View view) {
		if (mPrevSelectedView2 != null) {
			mPrevSelectedView2.setSelected(false);
		}
		view.setSelected(true);
		mViewfliper2.setDisplayedChild(Integer.valueOf(view.getTag() + ""));
		mPrevSelectedView2 = view;
	}
	
	public void onCameraSettingsApply(View view) {
		try
		{
			int progress = 0;
			int enteredIso = Integer.valueOf(mISOEdittext.getText().toString());
			if ((progress = indexof(mISOs, enteredIso)) != -1) {
				mIsoseekbar.setProgress(progress);
			}

			double enteredAe = Double.valueOf(mAeEditext.getText().toString());
			if ((progress = indexof(mExposureTimes, enteredAe)) != -1) {
				mAeSeekBar.setProgress(progress);
			}

			float enteredFocus = Float.valueOf(mFocusEdittext.getText().toString());
			progress = mCamera.toFocalToProgeres(enteredFocus);
			mFocusSeekBar.setProgress(progress);
		}catch(Exception ex) {
			showToast(this, "Please provide the valid camera settings");
		}
	}
	
	private int indexof(double[] arry, double value) {
		for (int i = 0; i < arry.length; i++) {
			if (arry[i] == value) {
				return i;
			}
		}

		return -1;
	}

	private int indexof(int[] arry, int value) {
		for (int i = 0; i < arry.length; i++) {
			if (arry[i] == value) {
				return i;
			}
		}

		return -1;
	}
	
	static private <T> int indexof(T[] arry, T value) {
		for (int i = 0; i < arry.length; i++) {
			if (arry[i] == value) {
				return i;
			}
		}

		return -1;
	}

	public void onPictureCaptured() {
			imageCaptureCount--;
			if (imageCaptureCount >= 1) {
				mCamera.takePicture();
			} else {
				mIsFastCapture = false;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						//mImageList.setAdapter(new MyImageAdapter(bitmapList, MainActivity.this));
					}
				});
				showToast(this, "Capture finished!");
			}
	}

	private void startTimer(long time) {
		getTimerFor(time).start();
	}

	private CountDownTimer getTimerFor(final long time) {
		return new CountDownTimer(time, 1000) {
			@Override
			public void onTick(long millisUntilFinished) {
				updateTime(millisUntilFinished / 1000);
				if (time == CAPTURE_TIMER) {
					if (millisUntilFinished / 1000 == 5) {
						// May required in future
					}
				}
			}

			@Override
			public void onFinish() {
				updateTime(0);
				if (time == CAPTURE_TIMER) {
					imageCaptureCount = MAXBURSTSIZE;
					mCamera.takePicture();
				}

				mDelayCaptureButton.setEnabled(true);
			}
		};
	}

	public void onMinus(View view) {
		if (patientId <= 1)
			return;
		patientId--;
		setPatientId();
	}

	public void onPlus(View view) {
		patientId++;
		setPatientId();
	}

	private void setPatientId() {
		((TextView) findViewById(R.id.pateint_id)).setText(String
				.valueOf(patientId));
	}

	public void onStartDipTimer(View view) {
		startTimer(DIP_TIMER);
		view.setVisibility(View.GONE);
		mDelayCaptureButton.setVisibility(View.VISIBLE);
		mDelayCaptureButton.setEnabled(false);
	}

	public void onStartCaptureTimer(View view) {
		startTimer(CAPTURE_TIMER);
		view.setVisibility(View.GONE);
		mDipTimerButton.setVisibility(View.VISIBLE);
		mDipTimerButton.setEnabled(false);
	}
	
	private void updateTime(long time) {
		((TextView) findViewById(R.id.remaining_time)).setText(String
				.valueOf(time)); 
	}

	public File getFileName() {
		if (camera2captureAll) {
			return new File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
							+ getTestImageDir(), getFilePrefix() + "Delay_"
							+ mDelay + "_EV_" + mMinAEValue + ".jpg");
		} else {
			return new File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
							+ getTestImageDir(), getFilePrefix() + "patient_"
							+ patientId + "_" + getFileIndex() + "_"
							+ getDateStr() + ".jpg");
		}
	}

	private String getFilePrefix() {
		return mIsFastCapture ? "F_" : "";
	}

	private String getTestImageDir() {
		return mIsFastCapture ? AppUtility.getFastCapturedImageFolder() : AppUtility.getDelayCapturedImageFolder();
	}

	private int getFileIndex() {
		int index = imageCaptureCount % MAXBURSTSIZE;

		return index == 0 ? MAXBURSTSIZE : index;
	}

	private String getDateStr() {
		Date date = Calendar.getInstance().getTime();
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"yyyy-MM-dd hh-mm-ss");
		return dateFormat.format(date) + "_" + System.currentTimeMillis();
	}

	public void onStartFastCapture(View view) {
		imageCaptureCount = MAXBURSTSIZE;
		mIsFastCapture = true;
		bitmapList = new ArrayList<>();
		//mImageList.setAdapter(new MyImageAdapter(bitmapList, this));
		mCamera.takePicture();
	}
	
	public void showToast(final Context currentContext, final String message) {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mDipTimerButton.setEnabled(true);
				Toast.makeText(currentContext, message, Toast.LENGTH_SHORT)
				.show();
			}
		});
	}
	
	OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			switch (seekBar.getId()) {
			case R.id.focusseekbar:
				float focal = mCamera.calculateFocal(progress);
				mFocusEdittext.setText(String.format("%2.1f", focal));
				mCamera.changeFocal(focal);
				break;
			case R.id.isoseekbar:
				mISOEdittext.setText(String.valueOf(mISOs[progress]));
				mCamera.setISO(mISOs[progress]);
				break;

			case R.id.aeseekbar:
				mAeEditext.setText(String.valueOf(mExposureTimes[progress]));
				mCamera.setAE(mExposureTimes[progress]);
				break;

			case R.id.whiteBalance_seekbar:
				mWBEdittext.setText(String.valueOf(mAWBmodes[progress]));
				mCamera.setWhiteBalaceMode(mAWBmodes[progress]);
			default:
				break;
			}
		}
	};

	//camera1 code
	
	public void onTab1Clicked(View view){
		if (mPrevSelectedView1 != null) {
			mPrevSelectedView1.setSelected(false);
		}
		view.setSelected(true);
		mViewfliper1.setDisplayedChild(Integer.valueOf(view.getTag() + ""));
		mPrevSelectedView1 = view;
	}
	
	@SuppressWarnings("deprecation")
	public void initialize() {
		mCamera = new AdvancedCamera(this);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mCaptureButton = (Button) findViewById(R.id.button);
		delayText = (EditText) findViewById(R.id.capture_delay);
		aeMinValue = (EditText) findViewById(R.id.aeMin);
		aeMaxValue = (EditText) findViewById(R.id.aeMax);
		aeStepValue = (EditText) findViewById(R.id.aeStep);
		aeValuesView = (TextView) findViewById(R.id.aeValues);
		mWhiteBalanceSpinner = (Spinner) findViewById(R.id.white_balance_spinner);
		mISOSpinner = (Spinner) findViewById(R.id.iso_spinner);
		mISOSupported = (TextView) findViewById(R.id.iso_supported);
		setEVButton = (Button) findViewById(R.id.setEVButton);
		singleCaptureButtun = (Button) findViewById(R.id.capture_button);
		mViewfliper1 = (ViewFlipper) findViewById(R.id.viewfliper1);
		evValue = (EditText) findViewById(R.id.ev_value);
		mPrevSelectedView1 = findViewById(R.id.capture_tab);
		//mImageList = (ListView) findViewById(R.id.image_list1);
		mPrevSelectedView1.setSelected(true);

		Camera camera = Camera.open();
		Parameters cp = camera.getParameters();
		int min = cp.getMinExposureCompensation();
		int max = cp.getMaxExposureCompensation();
		float step = cp.getExposureCompensationStep();
		List<String> wbList = cp.getSupportedWhiteBalance();
		String supportedIsoValues = cp.get("iso-values");
		camera.release();
		camera = null;

		aeValuesView.setText("Min Exp : " + min + " | Max Exp : " + max
				+ "  |  Step : " + step);
		mWhiteBalanceSpinner.setAdapter(new ArrayAdapter<>(this,
				layout.simple_dropdown_item_1line, wbList));
		if (supportedIsoValues != null && !supportedIsoValues.isEmpty()) {
			String[] isoList = supportedIsoValues.split(",");
			mISOSpinner.setAdapter(new ArrayAdapter<>(this,
					layout.simple_dropdown_item_1line, isoList));
			mISOSpinner.setVisibility(View.VISIBLE);
			mISOSupported.setText("ISO Values :");
		} else {
			mISOSpinner.setVisibility(View.GONE);
			mISOSupported.setText("ISO Not Supported");
		}

		mWhiteBalanceSpinner
				.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

					@Override
					public void onItemSelected(AdapterView<?> parent,
							View view, int position, long id) {
						mCamera.setWB(mWhiteBalanceSpinner.getSelectedItem()
								.toString());
					}

					@Override
					public void onNothingSelected(AdapterView<?> parent) {

					}
				});

		mISOSpinner
				.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

					@Override
					public void onItemSelected(AdapterView<?> parent,
							View view, int position, long id) {
						mCamera.setISO(mISOSpinner.getSelectedItem().toString());
					}

					@Override
					public void onNothingSelected(AdapterView<?> parent) {
						// TODO Auto-generated method stub

					}
				});

		setEVButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String evString = evValue.getText().toString();
				if (evString == null || evString.isEmpty()) {
					Toast.makeText(MainActivity.this, "Fill EV Value",
							Toast.LENGTH_SHORT).show();
				} else {
					mCamera.setEV(Integer.parseInt(evString));
				}
			}
		});

		singleCaptureButtun.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				String evString = evValue.getText().toString();
				if (evString == null || evString.isEmpty()) {
					Toast.makeText(MainActivity.this, "Fill EV Value",
							Toast.LENGTH_SHORT).show();
				} else {
					mCamera.setEV(Integer.parseInt(evString));
					String delayString = delayText.getText().toString();
					if (delayString == null || delayString.isEmpty()) {
						mCamera.takeSinglePicture(/*mImageList*/null, 0);
					} else {
						mCamera.takeSinglePicture(/*mImageList*/null,Long.parseLong(delayString));
					}
				}
			}
		});

		mCaptureButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				String delayString = delayText.getText().toString();
				String minAEString = aeMinValue.getText().toString();
				String maxAEString = aeMaxValue.getText().toString();
				String aeStepString = aeStepValue.getText().toString();
				String wbString = mWhiteBalanceSpinner.getSelectedItem()
						.toString();
				String isoString = null;
				if (mISOSpinner.getSelectedItem() != null)
					isoString = mISOSpinner.getSelectedItem().toString();
				if (delayString == null || delayString.isEmpty()) {
					if (minAEString == null || minAEString.isEmpty()
							|| maxAEString == null || maxAEString.isEmpty()
							|| aeStepString == null || aeStepString.isEmpty())
						mCamera.takePicture(/*mImageList*/null, 0, 0, 0, 0, wbString,
								isoString);
					else
						mCamera.takePicture(/*mImageList*/null, 0,
								Integer.parseInt(minAEString),
								Integer.parseInt(maxAEString),
								Integer.parseInt(aeStepString), wbString,
								isoString);
				} else {
					if (minAEString == null || minAEString.isEmpty()
							|| maxAEString == null || maxAEString.isEmpty()
							|| aeStepString == null || aeStepString.isEmpty())
						mCamera.takePicture(/*mImageList*/null,
								Long.parseLong(delayString), 0, 0, 0, wbString,
								isoString);
					else
						mCamera.takePicture(/*mImageList*/null,
								Long.parseLong(delayString),
								Integer.parseInt(minAEString),
								Integer.parseInt(maxAEString),
								Integer.parseInt(aeStepString), wbString,
								isoString);
				}
			}
		});
	}

}