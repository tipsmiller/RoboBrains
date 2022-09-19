package xyz.gmiller.robobrains;

import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import edu.umich.eecs.april.apriltag.ApriltagDetection;
import edu.umich.eecs.april.apriltag.ApriltagNative;

public class ApriltagActivity extends CameraXPreview {
    private String TAG = "AprilTagActivity";
    protected @ImageAnalysis.OutputImageFormat int imageAnalysisFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ApriltagNative.native_init();
        super.contentViewId = R.layout.activity_apriltag;
        ApriltagNative.apriltag_init("tag36h11", 2, 1.0, 1.0, 4);
        super.imageAnalysisFormat = imageAnalysisFormat;
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void detectObjects(ImageProxy image) {
        long inferenceTime = SystemClock.uptimeMillis();
        // Copy out YUV bits to the shared bitmap buffer
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        byte[] bufBytes = new byte[buf.remaining()];
        buf.get(bufBytes);
        List<ApriltagDetection> detections = ApriltagNative.apriltag_detect_yuv(bufBytes, image.getWidth(), image.getHeight());
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        onResults(convertToApriltagTensorDetections(detections, image.getWidth(), image.getHeight()), inferenceTime, image.getWidth(), image.getHeight());
        image.close();
    }

    private List<Detection> convertToApriltagTensorDetections(List<ApriltagDetection> apriltagDetections, int imageWidth, int imageHeight) {
        return apriltagDetections.stream().map((apriltagDetection) -> {
            Log.d(TAG, "converting apriltag to tensor detection for display");
            Log.d(TAG, String.format("image bounds W%d X H%d", imageWidth, imageHeight));
            Log.d(TAG, String.format("apriltag bounds: [%f, %f],[%f, %f],[%f, %f],[%f, %f]",
                    apriltagDetection.p[0],
                    apriltagDetection.p[1],
                    apriltagDetection.p[2],
                    apriltagDetection.p[3],
                    apriltagDetection.p[4],
                    apriltagDetection.p[5],
                    apriltagDetection.p[6],
                    apriltagDetection.p[7]
            ));
            float left = (float) Math.min(apriltagDetection.p[5], apriltagDetection.p[7]);
            float top = (float) Math.min(apriltagDetection.p[0], apriltagDetection.p[6]);
            float right = (float) Math.max(apriltagDetection.p[3], apriltagDetection.p[1]);
            float bottom = (float) Math.max(apriltagDetection.p[2], apriltagDetection.p[4]);
            RectF boundingBox = new RectF((imageHeight * 0.85f)-left, top, (imageHeight * 0.85f)-right, bottom);
            Log.d(TAG, String.format("bounding box: %s", boundingBox));
            List<Category> categories = Arrays.asList(Category.create("apriltag", "apriltag", 1.0F));
            return Detection.create(boundingBox, categories);
        }).collect(Collectors.toList());
    }
}
