package com.studyguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.model.UserState
import com.studyguardian.monitor.SleepMonitor
import com.studyguardian.service.OverlayService
import com.studyguardian.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.studyguardian.util.SleepSchedule

class ScreenPowerReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StudyGuardianApp
        val sleepMonitor = SleepMonitor(context)
        sleepMonitor.refreshChargingState()

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                sleepMonitor.updateScreen(true)
                scope.launch {
                    val prefs = app.preferences
                    val start = prefs.sleepStartMinutesFlow.first()
                    val end = prefs.sleepEndMinutesFlow.first()
                    if (SleepSchedule.isBedtime(SleepSchedule.nowMinutes(), start, end)) {
                        val partner = prefs.partnerUidFlow.first()
                        if (partner != null) {
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
                    if (sleepMonitor.isAsleep()) {
                        app.coordinator.reportState(UserState.SLEEP)
                    }
                }
            }
            Intent.ACTION_POWER_CONNECTED -> {
                sleepMonitor.updateCharging(true)
            }
        }
    }
}
