package com.studyguardian.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class UserState {
    @Json(name = "study") STUDY,
    @Json(name = "play") PLAY,
    @Json(name = "sleep") SLEEP,
    @Json(name = "offline") OFFLINE,
}

enum class InteractionAction {
    @Json(name = "poke_screen") POKE_SCREEN,
    @Json(name = "request_delay") REQUEST_DELAY,
    @Json(name = "approve_delay") APPROVE_DELAY,
}

enum class InteractionStatus {
    @Json(name = "pending") PENDING,
    @Json(name = "processed") PROCESSED,
}

@JsonClass(generateAdapter = true)
data class UserRecord(
  val objectId: String? = null,
  val uid: String,
  val partner_uid: String? = null,
  val invite_code: String? = null,
  val current_state: String = "offline",
  val last_update_time: String? = null,
)

@JsonClass(generateAdapter = true)
data class InteractionRecord(
  val objectId: String? = null,
  val sender_id: String,
  val receiver_id: String,
  val action_type: String,
  val status: String = "pending",
  val payload: String? = null,
)

data class PartnerStatus(
    val state: UserState,
    val lastUpdateMillis: Long,
    val isSignalLost: Boolean,
    val displayMessage: String,
    val emoji: String,
)
