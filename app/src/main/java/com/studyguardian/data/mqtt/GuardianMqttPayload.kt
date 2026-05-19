package com.studyguardian.data.mqtt

import com.squareup.moshi.JsonClass
import com.studyguardian.data.model.UserState

@JsonClass(generateAdapter = true)
data class GuardianMqttPayload(
    val device_id: String,
    val type: String,
    val state: String = UserState.OFFLINE.name.lowercase(),
    val screen_on: Boolean = false,
    val charging: Boolean = false,
    val foreground_app: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: String? = null,
) {
    companion object {
        const val TYPE_STATUS = "status"
        const val TYPE_POKE = "poke"
        const val TYPE_REQUEST_DELAY = "request_delay"
        const val TYPE_APPROVE_DELAY = "approve_delay"
        const val TYPE_OFFLINE = "offline"
    }
}
