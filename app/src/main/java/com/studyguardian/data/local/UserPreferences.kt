package com.studyguardian.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.studyguardian.data.model.PartnerStatus
import com.studyguardian.data.model.UserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by
    preferencesDataStore("guardian_prefs")

class UserPreferences(private val context: Context) {

    val channelFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CHANNEL] }
    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }
    val displayNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_DISPLAY_NAME] ?: "考研星人" }

    val sleepStartMinutesFlow: Flow<Int> = context.dataStore.data.map { it[KEY_SLEEP_START] ?: 23 * 60 + 30 }
    val sleepEndMinutesFlow: Flow<Int> = context.dataStore.data.map { it[KEY_SLEEP_END] ?: 6 * 60 }

    val strictLockUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_STRICT_LOCK_UNTIL] ?: 0L }
    val tempExemptUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_TEMP_EXEMPT_UNTIL] ?: 0L }
    val delayGrantedUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_DELAY_GRANTED_UNTIL] ?: 0L }

    val cachedPartnerStateFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CACHED_PARTNER_STATE] }
    val cachedPartnerEmojiFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CACHED_PARTNER_EMOJI] }
    val cachedPartnerMessageFlow: Flow<String?> = context.dataStore.data.map { it[KEY_CACHED_PARTNER_MSG] }
    val cachedPartnerLostFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CACHED_PARTNER_LOST] ?: false }

    suspend fun ensureDeviceId(): String {
        val existing = deviceIdFlow.first()
        if (existing != null) return existing
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        context.dataStore.edit { it[KEY_DEVICE_ID] = id }
        return id
    }

    suspend fun setChannel(channel: String) {
        context.dataStore.edit {
            it[KEY_CHANNEL] = channel
            it[KEY_ONBOARDED] = true
        }
    }

    suspend fun markSetupComplete() {
        context.dataStore.edit { it[KEY_ONBOARDED] = true }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name }
    }

    suspend fun cachePartnerStatus(status: PartnerStatus) {
        context.dataStore.edit {
            it[KEY_CACHED_PARTNER_STATE] = status.state.name
            it[KEY_CACHED_PARTNER_EMOJI] = status.emoji
            it[KEY_CACHED_PARTNER_MSG] = status.displayMessage
            it[KEY_CACHED_PARTNER_LOST] = status.isSignalLost
        }
    }

    suspend fun readCachedPartnerStatus(): PartnerStatus? {
        val stateName = cachedPartnerStateFlow.first() ?: return null
        val state = runCatching { UserState.valueOf(stateName) }.getOrDefault(UserState.OFFLINE)
        return PartnerStatus(
            state = state,
            lastUpdateMillis = 0L,
            isSignalLost = cachedPartnerLostFlow.first(),
            displayMessage = cachedPartnerMessageFlow.first() ?: "等待星人上线中…",
            emoji = cachedPartnerEmojiFlow.first() ?: "💫",
        )
    }

    suspend fun setStrictLockUntil(epochMs: Long) {
        context.dataStore.edit { it[KEY_STRICT_LOCK_UNTIL] = epochMs }
    }

    suspend fun setTempExemptUntil(epochMs: Long) {
        context.dataStore.edit { it[KEY_TEMP_EXEMPT_UNTIL] = epochMs }
    }

    suspend fun setDelayGrantedUntil(epochMs: Long) {
        context.dataStore.edit { it[KEY_DELAY_GRANTED_UNTIL] = epochMs }
    }

    companion object {
        private val KEY_CHANNEL = stringPreferencesKey("mqtt_channel")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_SLEEP_START = intPreferencesKey("sleep_start_minutes")
        private val KEY_SLEEP_END = intPreferencesKey("sleep_end_minutes")
        private val KEY_STRICT_LOCK_UNTIL = longPreferencesKey("strict_lock_until")
        private val KEY_TEMP_EXEMPT_UNTIL = longPreferencesKey("temp_exempt_until")
        private val KEY_DELAY_GRANTED_UNTIL = longPreferencesKey("delay_granted_until")
        private val KEY_CACHED_PARTNER_STATE = stringPreferencesKey("cached_partner_state")
        private val KEY_CACHED_PARTNER_EMOJI = stringPreferencesKey("cached_partner_emoji")
        private val KEY_CACHED_PARTNER_MSG = stringPreferencesKey("cached_partner_msg")
        private val KEY_CACHED_PARTNER_LOST = booleanPreferencesKey("cached_partner_lost")
    }
}
