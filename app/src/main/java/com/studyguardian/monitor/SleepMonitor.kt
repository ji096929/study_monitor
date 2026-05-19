package com.studyguardian.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class SleepMonitor(private val context: Context) {
    var screenOn: Boolean = true
        private set
    var charging: Boolean = false
        private set

    fun updateScreen(on: Boolean) {
        screenOn = on
    }

    fun updateCharging(connected: Boolean) {
        charging = connected
    }

    fun refreshChargingState() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** 插上充电线 + 屏幕熄灭 = 已入睡 */
    fun isAsleep(): Boolean = charging && !screenOn
}
