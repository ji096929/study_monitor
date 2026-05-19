package com.studyguardian.domain

import android.content.Context
import android.content.Intent
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.model.UserState
import com.studyguardian.data.mqtt.GuardianMqttPayload
import com.studyguardian.data.mqtt.MqttGuardianClient
import com.studyguardian.service.GuardianForegroundService
import com.studyguardian.service.OverlayService
import com.studyguardian.util.HapticHelper
import com.studyguardian.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GuardianCoordinator(
    private val context: Context,
    private val prefs: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var mqtt: MqttGuardianClient

    fun attachMqtt(client: MqttGuardianClient) {
        mqtt = client
        client.startStalePartnerWatch()
    }

    fun startGuardianServices() {
        if (!::mqtt.isInitialized) return
        mqtt.connectIfNeeded()
        context.startForegroundService(
            Intent(context, GuardianForegroundService::class.java),
        )
    }

    fun onPartnerMqttMessage(type: String, payload: String?) {
        when (type) {
            GuardianMqttPayload.TYPE_POKE -> {
                OverlayService.showPoke(context, "你的 TA")
                HapticHelper.pokeVibrate(context)
            }
            GuardianMqttPayload.TYPE_REQUEST_DELAY -> {
                NotificationHelper.showPartnerAlert(
                    context,
                    "撒娇申请～",
                    "你的考研星人想再玩 ${payload ?: "10"} 分钟，要不要同意？",
                )
            }
            GuardianMqttPayload.TYPE_APPROVE_DELAY -> {
                scope.launch {
                    val minutes = payload?.toIntOrNull() ?: 10
                    prefs.setDelayGrantedUntil(System.currentTimeMillis() + minutes * 60_000L)
                }
            }
        }
    }

    fun publishDeviceState(
        state: UserState,
        screenOn: Boolean,
        charging: Boolean,
        foregroundApp: String?,
    ) {
        if (!::mqtt.isInitialized) return
        mqtt.publishStatus(state, screenOn, charging, foregroundApp)
    }

    fun sendPoke() {
        if (!::mqtt.isInitialized) return
        mqtt.publishPoke()
    }

    fun sendApproveDelay(minutes: Int = 10) {
        if (!::mqtt.isInitialized) return
        mqtt.publishApproveDelay(minutes)
    }

    fun connectMqtt() {
        if (::mqtt.isInitialized) mqtt.connectIfNeeded()
    }

    fun disconnectMqtt() {
        if (::mqtt.isInitialized) mqtt.disconnectGracefully()
    }

    companion object {
        const val AUTO_APPROVE_WAIT_MS = 3 * 60 * 1000L
        const val AUTO_GRANT_MINUTES = 5
        const val STRICT_LOCK_MINUTES = 10
        const val OFFLINE_THRESHOLD_MS = 15 * 60 * 1000L
    }
}
