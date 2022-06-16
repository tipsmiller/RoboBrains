package xyz.gmiller.robobrains;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.os.Environment.MEDIA_SHARED;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

public class CameraViewActivity extends CameraActivity implements CameraView.CvCameraViewListener2, Arduino.ArduinoListener {
    private static final String TAG = "RoboBrains::CameraView";

    private CameraView mOpenCvCameraView;
    Mat mRgba;
    Mat mGray;
    File mCascadeClassifierFile;
    CascadeClassifier mCascadeClassifier;
    MatOfRect mObjects;
    long mObjectMinSize, mObjectMaxSize;
    String mTakeFrame;
    SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SS", Locale.US);
    Arduino mArduino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera_view);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOpenCvCameraView = findViewById(R.id.cameraView);
        mOpenCvCameraView.setVisibility(CameraView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setMaxFrameSize(800, 400);
        //mOpenCvCameraView.setMaxFrameSize(1920, 1080);
        mOpenCvCameraView.enableFpsMeter();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mOpenCvCameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // load opencv binaries
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        mTakeFrame = null;

        mArduino = new Arduino(this, (UsbManager) getSystemService(Context.USB_SERVICE), this);
        mArduino.tryConnect();
    }

    public void onDestroy() {
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
        if (mArduino != null) {
            mArduino.destroy();
        }
        super.onDestroy();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();

                    try{
                        InputStream is = getResources().openRawResource(R.raw.cascade);
                        File cascadeDir = getDir("OpenCV_data", Context.MODE_PRIVATE);
                        mCascadeClassifierFile = new File(cascadeDir, "soccer_classifier.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeClassifierFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();
                        mCascadeClassifier = new CascadeClassifier();
                        mCascadeClassifier.load(mCascadeClassifierFile.getAbsolutePath());
                        if(mCascadeClassifier.empty()) {
                            Log.e(TAG, "No mCascadeClassifierFile loaded");
                            mCascadeClassifier = null;
                        } else {
                            Log.i(TAG, "Loaded mCascadeClassifierFile from " + mCascadeClassifierFile.getAbsolutePath());
                        }
                    } catch (IOException ex) {
                        Log.e(TAG, "Failed to load mCascadeClassifier mCascadeClassifierFile", ex);
                    }
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public void onClickPositive(View view) {
        mTakeFrame = "positives";
    }

    public void onClickNegative(View view) {
        mTakeFrame = "negatives";
    }

    @Override
    protected List<? extends CameraView> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, String.format("Camera view started: w %s h %s", width, height));
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        mObjects = new MatOfRect();
        mObjectMinSize = 64;
        mObjectMaxSize = Math.round(height * 0.5);
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "Camera view stopped");
        mRgba.release();
        mGray.release();
        mObjects.release();
    }

    @Override
    public Mat onCameraFrame(CameraView.CvCameraViewFrame inputFrame) {
        Log.i(TAG, "Camera view received frame");
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mTakeFrame != null) {
            takePicture(mTakeFrame, mRgba);
            mTakeFrame = null;
        }

        if (mCascadeClassifier != null && !mGray.empty()) {
            mCascadeClassifier.detectMultiScale(mGray, mObjects, 1.3, 2, Objdetect.CASCADE_SCALE_IMAGE,
                    new Size(mObjectMinSize, mObjectMinSize), new Size(mObjectMaxSize, mObjectMaxSize));
        }

        Rect[] objectsArray = mObjects.toArray();
        for (Rect object : objectsArray)
            Imgproc.rectangle(mRgba, object.tl(), object.br(), new Scalar(0, 255, 0), 3);

        return mRgba;
    }

    public void takePicture(String folder, Mat frame) {
        if (!frame.empty()) {
            try {
                if (Environment.getExternalStorageState().equals(MEDIA_SHARED)) {
                    Toast.makeText(this, "Shared media, cannot write file", Toast.LENGTH_SHORT).show();
                    return;
                }
                // create output directory if it doesn't exist
                File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath() + "/" + folder + "/");
                outputDir.mkdirs();
                // write the PNG file
                Mat output = new Mat(frame.rows(), frame.cols(), CvType.CV_8UC3);
                String outputFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath() + "/" + folder + "/" + mDateFormat.format(new Date()) + ".png";
                Imgproc.cvtColor(frame, output, Imgproc.COLOR_RGB2BGR);
                boolean imageWritten = imwrite(outputFile, output);
                if (imageWritten) {
                    Toast.makeText(this, "Picture taken: " + outputFile, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Picture not saved", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error saving picture", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMessageReceived(String message) {
        Log.i(TAG, message);
    }
}
