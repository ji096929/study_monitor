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

    private data class DeviceSnapshot(
        val state: UserState,
        val screenOn: Boolean,
        val charging: Boolean,
        val foregroundApp: String?,
    )

    private var lastSnapshot: DeviceSnapshot? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        startForeground(
            NotificationHelper.FG_NOTIFICATION_ID,
            NotificationHelper.buildForegroundNotification(this),
        )
        usageMonitor = UsageMonitor(this)
        sleepMonitor = SleepMonitor(this)
        sleepMonitor.refreshChargingState()

        val app = application as StudyGuardianApp
        app.coordinator.connectMqtt()
        scope.launch { monitorLoop(app) }
    }

    private suspend fun monitorLoop(app: StudyGuardianApp) {
        while (true) {
            val prefs = app.preferences
            val coordinator = app.coordinator
            val now = System.currentTimeMillis()
            val strictUntil = prefs.strictLockUntilFlow.first()
            val exemptUntil = prefs.tempExemptUntilFlow.first()
            val delayUntil = prefs.delayGrantedUntilFlow.first()

            val screenOn = sleepMonitor.screenOn
            val charging = sleepMonitor.charging
            val foregroundApp = usageMonitor.foregroundPackage()

            if (now < strictUntil && now >= delayUntil) {
                if (screenOn) {
                    OverlayService.showStrictLock(this, "严格锁死模式：魔法睡觉时间，只能关机或睡觉啦～")
                }
                publishIfChanged(coordinator, UserState.OFFLINE, screenOn, charging, foregroundApp)
            } else if (exemptUntil > now) {
                publishIfChanged(coordinator, UserState.STUDY, screenOn, charging, foregroundApp)
            } else {
                val sleepStart = prefs.sleepStartMinutesFlow.first()
                val sleepEnd = prefs.sleepEndMinutesFlow.first()
                val bedtime = SleepSchedule.isBedtime(SleepSchedule.nowMinutes(), sleepStart, sleepEnd)

                when {
                    sleepMonitor.isAsleep() -> {
                        publishIfChanged(coordinator, UserState.SLEEP, screenOn, charging, foregroundApp)
                    }
                    usageMonitor.isPlaying() -> {
                        publishIfChanged(coordinator, UserState.PLAY, screenOn, charging, foregroundApp)
                        if (bedtime && screenOn) {
                            val canDelay = delayUntil < now
                            OverlayService.showSleepMask(this, canRequestDelay = canDelay)
                        }
                    }
                    usageMonitor.isStudying() -> {
                        publishIfChanged(coordinator, UserState.STUDY, screenOn, charging, foregroundApp)
                    }
                    else -> {
                        publishIfChanged(coordinator, UserState.OFFLINE, screenOn, charging, foregroundApp)
                    }
                }
            }
            delay(5_000)
        }
    }

    private fun publishIfChanged(
        coordinator: com.studyguardian.domain.GuardianCoordinator,
        state: UserState,
        screenOn: Boolean,
        charging: Boolean,
        foregroundApp: String?,
    ) {
        val snapshot = DeviceSnapshot(state, screenOn, charging, foregroundApp)
        if (snapshot != lastSnapshot) {
            lastSnapshot = snapshot
            coordinator.publishDeviceState(state, screenOn, charging, foregroundApp)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val app = application as StudyGuardianApp
        app.coordinator.disconnectMqtt()
        scope.cancel()
        super.onDestroy()
    }
}
