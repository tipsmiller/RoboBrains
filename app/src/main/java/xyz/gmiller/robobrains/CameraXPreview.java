package xyz.gmiller.robobrains;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class CameraXPreview extends AppCompatActivity implements ObjectDetectorHelper.DetectorListener {
    private static final String TAG = "CameraXPreview";
    private ExecutorService cameraExecutor;
    private PreviewView previewView;
    private OverlayView overlayView;
    private static final String[] permissionsRequired = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    private static final int permissionsRequestCode = 10;
    private TextView analysisFps;
    private ObjectDetectorHelper objectDetectorHelper;
    protected Bitmap bitmapBuffer;
    protected @LayoutRes int contentViewId = R.layout.activity_camerax_preview;
    protected @ImageAnalysis.OutputImageFormat int imageAnalysisFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(contentViewId);
        previewView = findViewById(R.id.cameraXPreviewView);
        overlayView = findViewById(R.id.detectionOverlay);
        analysisFps = findViewById(R.id.analysisFps);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, permissionsRequired, permissionsRequestCode);
        }
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private boolean allPermissionsGranted() {
        return Arrays.stream(permissionsRequired).allMatch((p) -> ContextCompat.checkSelfPermission(this.getBaseContext(), p) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == permissionsRequestCode) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private Function<Double, Void> analysisListener = (Double fps) -> {
        ContextCompat.getMainExecutor(this).execute(() -> analysisFps.setText(String.valueOf(fps)));
        return null;
    };

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            ProcessCameraProvider cameraProvider = null;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

            Size imageSize = new Size(400, 400);
            int aspectRatio = AspectRatio.RATIO_16_9;
            // Preview
            Preview preview = (new Preview.Builder())
                    //.setTargetResolution(imageSize)
                    //.setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // Analysis
            ImageAnalysis imageAnalyzer = (new ImageAnalysis.Builder())
                    .setTargetResolution(imageSize)
                    //.setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(imageAnalysisFormat)
                    .build();
            imageAnalyzer.setAnalyzer(cameraExecutor, (ImageProxy image) -> {
                if (bitmapBuffer == null) {
                    // The image rotation and RGB image buffer are initialized only once
                    // the analyzer has started running
                    bitmapBuffer = Bitmap.createBitmap(
                            image.getWidth(),
                            image.getHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                }
                detectObjects(image);
            });

            // Select back camera as a default
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
                Log.i(TAG, "Camera preview bound to surface");

            } catch (Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    protected void detectObjects(ImageProxy image) {
        //Log.d(TAG, "detectObjects received frame");
        if(objectDetectorHelper == null) {
            objectDetectorHelper = new ObjectDetectorHelper(
                    0.5f,
                    2,
                    3,
                    ObjectDetectorHelper.DELEGATE_NNAPI,
                    ObjectDetectorHelper.MODEL_MOBILENETV1,
                    getBaseContext(),
                    this
            );
        }
        // Copy out RGB bits to the shared bitmap buffer
        bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

        int imageRotation = image.getImageInfo().getRotationDegrees();
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation);
        image.close();
    }

    @Override
    public void onResults(@Nullable List<Detection> results, long inferenceTime, int imageHeight, int imageWidth) {
        //Log.d(TAG, "onResults received detection results");
        runOnUiThread(() -> {
            // Pass necessary information to OverlayView for drawing on the canvas
            overlayView.setResults(
                    (results == null) ? (new ArrayList<Detection>()) : results,
                    imageHeight,
                    imageWidth
            );

            // Force a redraw
            overlayView.invalidate();
            analysisListener.apply((double)inferenceTime);
        });
    }

    @Override
    public void onError(@NotNull String error) {
        runOnUiThread(() -> {
            Toast.makeText(getBaseContext(), error, Toast.LENGTH_SHORT).show();
        });
    }
}
