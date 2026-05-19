package com.studyguardian.data.model

enum class UserState {
    STUDY,
    PLAY,
    SLEEP,
    OFFLINE,
}

data class PartnerStatus(
    val state: UserState,
    val lastUpdateMillis: Long,
    val isSignalLost: Boolean,
    val displayMessage: String,
    val emoji: String,
)
