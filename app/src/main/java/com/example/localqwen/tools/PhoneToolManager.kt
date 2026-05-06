package com.example.localqwen.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.content.pm.PackageManager

class PhoneToolManager(private val context: Context) {

    fun getBatteryStatus(): PhoneToolResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        
        val percentage = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        val content = "نسبة البطارية: $percentage%\nالحالة: ${if (isCharging) "جارٍ الشحن" else "لا يتم الشحن"}"
        return PhoneToolResult("حالة البطارية", content)
    }

    fun getDeviceInfo(): PhoneToolResult {
        val content = StringBuilder()
            .append("المصنع: ${Build.MANUFACTURER}\n")
            .append("الموديل: ${Build.MODEL}\n")
            .append("إصدار أندرويد: ${Build.VERSION.RELEASE}\n")
            .append("مستوى SDK: ${Build.VERSION.SDK_INT}")
            .toString()
        return PhoneToolResult("معلومات الجهاز", content)
    }

    fun getStorageInfo(): PhoneToolResult {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val totalSpace = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
        val freeSpace = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        
        val content = "إجمالي التخزين: $totalSpace جيجابايت\nالمساحة المتوفرة: $freeSpace جيجابايت"
        return PhoneToolResult("التخزين", content)
    }

    fun getInstalledAppsSummary(): PhoneToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val content = "عدد التطبيقات المثبتة: ${apps.size}"
        return PhoneToolResult("التطبيقات المثبتة", content)
    }
}
