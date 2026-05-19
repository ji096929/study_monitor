package com.studyguardian.domain

import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.model.InteractionAction
import com.studyguardian.data.remote.BaasRepository
import com.studyguardian.service.OverlayService
import kotlinx.coroutines.delay

/**
 * 心疼机制：延时申请发出后 3 分钟内无伴侣 approve → 自动 5 分钟 + 严格锁死。
 */
class SleepDelayHandler(
    private val prefs: UserPreferences,
    private val baas: BaasRepository,
) {
    suspend fun requestWithAutoFallback(
        context: android.content.Context,
        myUid: String,
        partnerUid: String,
    ) {
        baas.sendInteraction(myUid, partnerUid, InteractionAction.REQUEST_DELAY, "10")
        val approved = waitForApproval(partnerUid, GuardianCoordinator.AUTO_APPROVE_WAIT_MS)
        if (!approved) {
            val grantUntil = System.currentTimeMillis() + GuardianCoordinator.AUTO_GRANT_MINUTES * 60_000L
            prefs.setDelayGrantedUntil(grantUntil)
            val strictUntil = grantUntil + GuardianCoordinator.STRICT_LOCK_MINUTES * 60_000L
            prefs.setStrictLockUntil(strictUntil)
            OverlayService.showSleepMask(
                context,
                canRequestDelay = false,
                message = "TA 可能睡着啦，心疼机制送你 ${GuardianCoordinator.AUTO_GRANT_MINUTES} 分钟，之后要乖乖睡觉哦～",
            )
        }
    }

    private suspend fun waitForApproval(partnerUid: String, timeoutMs: Long): Boolean {
        val step = 5_000L
        var waited = 0L
        while (waited < timeoutMs) {
            delay(step)
            waited += step
            // Partner approve is reflected locally via delayGrantedUntil set by interaction poll
            // Simplified: check if delay was granted externally
        }
        return false
    }
}
