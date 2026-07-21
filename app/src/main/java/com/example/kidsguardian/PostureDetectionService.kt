package com.example.kidsguardian

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class PostureDetectionService : Service(), SensorEventListener {
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var toneGenerator: ToneGenerator? = null
    private lateinit var vibrator: Vibrator
    private lateinit var powerManager: PowerManager
    
    var isDetecting = false
        private set
    private var isDeviceLyingDown = false
    private var alertCount = 0
    
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val CHANNEL_ID = "PostureDetectionChannel"
        private const val NOTIFICATION_ID = 1
        private const val LYING_THRESHOLD = 0.7f
        private const val ALERT_THRESHOLD = 3
    }

    inner class LocalBinder : Binder() {
        fun getService(): PostureDetectionService = this@PostureDetectionService
    }

    override fun onCreate() {
        super.onCreate()
        initializeSensors()
        createNotificationChannel()
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        try {
            toneGenerator = ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "姿态检测服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即启动前台通知，避免 ANR
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    fun startDetection() {
        if (!isDetecting) {
            // 检查传感器是否可用
            if (accelerometer == null) {
                return
            }
            
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            isDetecting = true
            startForeground(NOTIFICATION_ID, createNotification())
            acquireWakeLock()
        }
    }

    fun stopDetection() {
        if (isDetecting) {
            sensorManager.unregisterListener(this)
            isDetecting = false
            stopForeground(true)
            releaseWakeLock()
            alertCount = 0
            isDeviceLyingDown = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            detectPosture(event.values)
        }
    }

    private fun detectPosture(accelValues: FloatArray) {
        val x = accelValues[0]
        val y = accelValues[1]
        val z = accelValues[2]
        
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude == 0f) return
        
        val normalizedZ = abs(z) / magnitude
        
        // 当 z 轴加速度占比超过阈值，说明设备处于水平（躺着）状态
        if (normalizedZ > LYING_THRESHOLD) {
            if (!isDeviceLyingDown) {
                isDeviceLyingDown = true
                alertCount = 0
            }
            
            alertCount++
            if (alertCount >= ALERT_THRESHOLD) {
                triggerAlert()
                alertCount = 0
            }
        } else {
            isDeviceLyingDown = false
            alertCount = 0
        }
    }

    private fun triggerAlert() {
        playAlertSound()
        vibrate()
        showAlertMessage()
        
        if (shouldLockScreen()) {
            lockScreen()
        }
    }

    private fun playAlertSound() {
        try {
            val tone = toneGenerator ?: return
            // 播放三声"嘀"音
            Thread {
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    Thread.sleep(400)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    Thread.sleep(400)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
        }
    }

    private fun showAlertMessage() {
        val alertIntent = Intent(this, AlertActivity::class.java)
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(alertIntent)
    }

    private fun shouldLockScreen(): Boolean {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("enable_lock", false)
    }

    private fun lockScreen() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                return
            }
            
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                width = 1
                height = 1
            }
            
            val view = View(this)
            windowManager.addView(view, params)
            
            // 3秒后移除视图
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 3000)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KidsGuardian::PostureDetectionWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10分钟超时
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, pendingFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("少儿卫士正在运行")
            .setContentText("正在保护您的视力健康")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
        toneGenerator?.release()
    }
}