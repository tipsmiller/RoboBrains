package xyz.gmiller.robobrains;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.util.AttributeSet;

import org.opencv.android.JavaCamera2View;

public class CameraView extends JavaCamera2View {

    public CameraDevice mCameraDevice;

    public CameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
