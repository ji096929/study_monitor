package com.studyguardian.domain

import android.content.Context
import android.content.Intent
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.model.InteractionAction
import com.studyguardian.data.model.UserState
import com.studyguardian.data.remote.BaasRepository
import com.studyguardian.service.GuardianForegroundService
import com.studyguardian.service.OverlayService
import com.studyguardian.util.HapticHelper
import com.studyguardian.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

class GuardianCoordinator(
    private val context: Context,
    private val prefs: UserPreferences,
    private val baas: BaasRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startGuardianServices() {
        context.startForegroundService(
            Intent(context, GuardianForegroundService::class.java),
        )
    }

    fun handleInteraction(
        action: InteractionAction,
        senderName: String,
        objectId: String? = null,
        payload: String? = null,
    ) {
        when (action) {
            InteractionAction.POKE_SCREEN -> {
                OverlayService.showPoke(context, senderName)
                HapticHelper.pokeVibrate(context)
            }
            InteractionAction.REQUEST_DELAY -> {
                NotificationHelper.showPartnerAlert(
                    context,
                    "撒娇申请～",
                    "你的考研星人想再玩 10 分钟，要不要同意？",
                )
            }
            InteractionAction.APPROVE_DELAY -> {
                scope.launch {
                    val minutes = payload?.toIntOrNull() ?: 10
                    prefs.setDelayGrantedUntil(System.currentTimeMillis() + minutes * 60_000L)
                    objectId?.let { baas.markInteractionProcessed(it) }
                }
            }
        }
    }

    suspend fun reportState(state: UserState) {
        val uid = prefs.uidFlow.first() ?: return
        baas.upsertUser(
            com.studyguardian.data.model.UserRecord(
                uid = uid,
                partner_uid = prefs.partnerUidFlow.first(),
                current_state = state.name.lowercase(),
                last_update_time = Instant.now().toString(),
            ),
        )
    }

    suspend fun notifyPartnerPlaying(partnerUid: String, myName: String) {
        NotificationHelper.showPartnerAlert(
            context,
            "叮咚～",
            "你的考研星人正在偷偷冲浪，去戳戳她吧～",
        )
        baas.sendInteraction(
            senderId = prefs.uidFlow.first() ?: return,
            receiverId = partnerUid,
            action = InteractionAction.POKE_SCREEN,
            payload = "play_detected",
        )
    }

    suspend fun sendPoke(partnerUid: String) {
        val uid = prefs.uidFlow.first() ?: return
        baas.sendInteraction(uid, partnerUid, InteractionAction.POKE_SCREEN)
    }

    suspend fun requestSleepDelay(partnerUid: String) {
        val uid = prefs.uidFlow.first() ?: return
        baas.sendInteraction(uid, partnerUid, InteractionAction.REQUEST_DELAY, payload = "10")
    }

    suspend fun approveSleepDelay(partnerUid: String, interactionId: String) {
        val uid = prefs.uidFlow.first() ?: return
        baas.sendInteraction(uid, partnerUid, InteractionAction.APPROVE_DELAY, payload = "10")
        baas.markInteractionProcessed(interactionId)
    }

    fun pollInteractionsLoop() {
        scope.launch {
            while (true) {
                val uid = prefs.uidFlow.first() ?: break
                val pending = baas.pollPendingInteractions(uid)
                pending.forEach { record ->
                    val action = when (record.action_type.lowercase()) {
                        "poke_screen" -> InteractionAction.POKE_SCREEN
                        "request_delay" -> InteractionAction.REQUEST_DELAY
                        "approve_delay" -> InteractionAction.APPROVE_DELAY
                        else -> return@forEach
                    }
                    handleInteraction(action, "你的星人", record.objectId, record.payload)
                    record.objectId?.let { baas.markInteractionProcessed(it) }
                }
                delay(8_000)
            }
        }
    }

    companion object {
        const val AUTO_APPROVE_WAIT_MS = 3 * 60 * 1000L
        const val AUTO_GRANT_MINUTES = 5
        const val STRICT_LOCK_MINUTES = 10
        const val OFFLINE_THRESHOLD_MS = 15 * 60 * 1000L
    }
}
