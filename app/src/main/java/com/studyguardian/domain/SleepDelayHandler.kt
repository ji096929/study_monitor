package com.studyguardian.domain

import android.content.Context
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.mqtt.MqttGuardianClient
import com.studyguardian.service.OverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 心疼机制：延时申请发出后 3 分钟内未收到 approve_delay → 自动 5 分钟 + 严格锁死。
 */
class SleepDelayHandler(
    private val prefs: UserPreferences,
    private val mqtt: MqttGuardianClient,
) {
    suspend fun requestWithAutoFallback(context: Context) {
        mqtt.publishRequestDelay(10)
        delay(GuardianCoordinator.AUTO_APPROVE_WAIT_MS)
        val grantedUntil = prefs.delayGrantedUntilFlow.first()
        if (grantedUntil > System.currentTimeMillis()) return

        val grantEnd = System.currentTimeMillis() + GuardianCoordinator.AUTO_GRANT_MINUTES * 60_000L
        prefs.setDelayGrantedUntil(grantEnd)
        val strictUntil = grantEnd + GuardianCoordinator.STRICT_LOCK_MINUTES * 60_000L
        prefs.setStrictLockUntil(strictUntil)
        OverlayService.showSleepMask(
            context,
            canRequestDelay = false,
            message = "TA 可能睡着啦，心疼机制送你 ${GuardianCoordinator.AUTO_GRANT_MINUTES} 分钟，之后要乖乖睡觉哦～",
        )
    }
}
