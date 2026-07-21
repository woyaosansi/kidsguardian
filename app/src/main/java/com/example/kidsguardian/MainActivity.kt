package com.example.kidsguardian

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    
    private var postureService: PostureDetectionService? = null
    private var isServiceBound = false
    private var pendingStart = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PostureDetectionService.LocalBinder
            postureService = binder.getService()
            isServiceBound = true
            
            // 如果有等待启动的请求，现在启动
            if (pendingStart) {
                postureService?.startDetection()
                pendingStart = false
            }
            updateStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            postureService = null
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnStartStop = findViewById(R.id.btnStartStop)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupClickListeners() {
        btnStartStop.setOnClickListener {
            if (isServiceBound && postureService?.isDetecting == true) {
                stopProtection()
            } else {
                startProtection()
            }
        }
    }

    private fun startProtection() {
        // 检查悬浮窗权限（Android 6.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限才能正常工作", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        
        if (!isServiceBound) {
            // 服务还未绑定，先绑定，绑定完成后自动启动
            pendingStart = true
            bindPostureService()
            Toast.makeText(this, "少儿卫士正在启动...", Toast.LENGTH_SHORT).show()
        } else {
            postureService?.startDetection()
            updateButtonState(true)
            Toast.makeText(this, "少儿卫士已启动保护", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopProtection() {
        postureService?.stopDetection()
        updateButtonState(false)
        Toast.makeText(this, "少儿卫士已停止保护", Toast.LENGTH_SHORT).show()
    }

    private fun bindPostureService() {
        val intent = Intent(this, PostureDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateButtonState(isProtecting: Boolean) {
        if (isProtecting) {
            btnStartStop.text = getString(R.string.stop_protection)
            tvStatus.text = "保护已开启"
            tvStatus.setTextColor(getColor(R.color.green))
        } else {
            btnStartStop.text = getString(R.string.start_protection)
            tvStatus.text = "保护已关闭"
            tvStatus.setTextColor(getColor(R.color.red))
        }
    }

    private fun updateStatus() {
        if (isServiceBound) {
            updateButtonState(postureService?.isDetecting == true)
        } else {
            updateButtonState(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}