package xyz.gmiller.robobrains;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void onClickGoCamera(View view) {
        Intent intent = new Intent(this, CameraViewActivity.class);
        startActivity(intent);
    }
    public void onClickGoRotationVectorDemo(View view) {
        Intent intent = new Intent(this, RotationVectorDemo.class);
        startActivity(intent);
    }

}
