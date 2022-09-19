package xyz.gmiller.robobrains;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.util.function.Function;

public class BallCascadeAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "BallCascadeAnalyzer";
    private Function<Double, Void> listener;
    private long lastTime;

    public BallCascadeAnalyzer(Function<Double, Void> listener) {
        this.listener = listener;
        this.lastTime = System.currentTimeMillis();
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        Log.d(TAG, "received image " + image.getWidth() + "x" + image.getHeight());
        long newTime = System.currentTimeMillis();
        double fps = 1000.0 / (newTime - lastTime);
        lastTime = newTime;
        listener.apply(fps);
        image.close();
    }
}
