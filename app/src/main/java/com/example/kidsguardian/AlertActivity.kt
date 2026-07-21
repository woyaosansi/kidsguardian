package com.example.kidsguardian

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlertActivity : AppCompatActivity() {
    
    private lateinit var tvAlert: TextView
    private lateinit var btnDismiss: Button
    private lateinit var ivWarning: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        setContentView(R.layout.activity_alert)
        
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        tvAlert = findViewById(R.id.tvAlert)
        btnDismiss = findViewById(R.id.btnDismiss)
        ivWarning = findViewById(R.id.ivWarning)
    }

    private fun setupClickListeners() {
        btnDismiss.setOnClickListener {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}