package com.studyguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.model.UserState
import com.studyguardian.monitor.SleepMonitor
import com.studyguardian.monitor.UsageMonitor
import com.studyguardian.service.OverlayService
import com.studyguardian.util.NotificationHelper
import com.studyguardian.util.SleepSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenPowerReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StudyGuardianApp
        val sleepMonitor = SleepMonitor(context)
        val usageMonitor = UsageMonitor(context)
        sleepMonitor.refreshChargingState()

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                sleepMonitor.updateScreen(true)
                scope.launch {
                    publishSnapshot(app, sleepMonitor, usageMonitor, UserState.OFFLINE)
                    val prefs = app.preferences
                    val start = prefs.sleepStartMinutesFlow.first()
                    val end = prefs.sleepEndMinutesFlow.first()
                    if (SleepSchedule.isBedtime(SleepSchedule.nowMinutes(), start, end)) {
                        if (prefs.channelFlow.first() != null) {
                            NotificationHelper.showPartnerAlert(
                                context,
                                "夜猫子出没！",
                                "她竟然还在玩手机！",
                            )
                        }
                        OverlayService.showSleepMask(context, canRequestDelay = true)
                    }
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                sleepMonitor.updateScreen(false)
                scope.launch {
                    val state = if (sleepMonitor.isAsleep()) UserState.SLEEP else UserState.OFFLINE
                    publishSnapshot(app, sleepMonitor, usageMonitor, state)
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                sleepMonitor.updateCharging(true)
                scope.launch {
                    publishSnapshot(app, sleepMonitor, usageMonitor, UserState.OFFLINE)
                }
            }
        }
    }

    private fun publishSnapshot(
        app: StudyGuardianApp,
        sleep: SleepMonitor,
        usage: UsageMonitor,
        state: UserState,
    ) {
        app.coordinator.publishDeviceState(
            state = state,
            screenOn = sleep.screenOn,
            charging = sleep.charging,
            foregroundApp = usage.foregroundPackage(),
        )
    }
}
