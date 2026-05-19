package com.studyguardian.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.model.UserState
import com.studyguardian.monitor.SleepMonitor
import com.studyguardian.monitor.UsageMonitor
import com.studyguardian.util.NotificationHelper
import com.studyguardian.util.SleepSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GuardianForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var usageMonitor: UsageMonitor
    private lateinit var sleepMonitor: SleepMonitor
    private var lastPlayNotifyAt = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        startForeground(NotificationHelper.FG_NOTIFICATION_ID, NotificationHelper.buildForegroundNotification(this))
        usageMonitor = UsageMonitor(this)
        sleepMonitor = SleepMonitor(this)
        sleepMonitor.refreshChargingState()

        val app = application as StudyGuardianApp
        app.coordinator.pollInteractionsLoop()
        scope.launch { monitorLoop(app) }
    }

    private suspend fun monitorLoop(app: StudyGuardianApp) {
        while (true) {
            val prefs = app.preferences
            val now = System.currentTimeMillis()
            val strictUntil = prefs.strictLockUntilFlow.first()
            val exemptUntil = prefs.tempExemptUntilFlow.first()
            val delayUntil = prefs.delayGrantedUntilFlow.first()

            if (now < strictUntil && now >= delayUntil) {
                if (sleepMonitor.screenOn) {
                    OverlayService.showStrictLock(this, "严格锁死模式：魔法睡觉时间，只能关机或睡觉啦～")
                }
            } else if (exemptUntil > now) {
                // temporary whitelist window — no overlay
            } else {
                val sleepStart = prefs.sleepStartMinutesFlow.first()
                val sleepEnd = prefs.sleepEndMinutesFlow.first()
                val bedtime = SleepSchedule.isBedtime(SleepSchedule.nowMinutes(), sleepStart, sleepEnd)

                when {
                    sleepMonitor.isAsleep() -> app.coordinator.reportState(UserState.SLEEP)
                    usageMonitor.isPlaying() -> {
                        app.coordinator.reportState(UserState.PLAY)
                        val partner = prefs.partnerUidFlow.first()
                        if (partner != null && now - lastPlayNotifyAt > 5 * 60_000L) {
                            lastPlayNotifyAt = now
                            app.coordinator.notifyPartnerPlaying(partner, "星人")
                        }
                        if (bedtime && sleepMonitor.screenOn) {
                            val canDelay = delayUntil < now
                            OverlayService.showSleepMask(this, canRequestDelay = canDelay)
                        }
                    }
                    usageMonitor.isStudying() -> app.coordinator.reportState(UserState.STUDY)
                    else -> app.coordinator.reportState(UserState.OFFLINE)
                }
            }
            delay(5_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
