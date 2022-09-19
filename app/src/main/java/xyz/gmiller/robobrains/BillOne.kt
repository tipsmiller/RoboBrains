package xyz.gmiller.robobrains

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class BillOneActivity : AppCompatActivity() {
    private val TAG = "RoboBrains::BillOneActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bill_one)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}