package com.example.kidsguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class ScreenReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                // 屏幕打开时可以重新启动服务
                handleScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                // 屏幕关闭时可以暂停服务
                handleScreenOff(context)
            }
        }
    }
    
    private fun handleScreenOn(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isProtectionEnabled = prefs.getBoolean("protection_enabled", false)
        
        if (isProtectionEnabled) {
            val serviceIntent = Intent(context, PostureDetectionService::class.java)
            context.startService(serviceIntent)
        }
    }
    
    private fun handleScreenOff(context: Context) {
        // 可以在这里实现暂停检测的逻辑
        // 当前版本暂时不做处理
    }
}