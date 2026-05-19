package com.studyguardian.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("guardian_prefs")

class UserPreferences(private val context: Context) {

    val uidFlow: Flow<String?> = context.dataStore.data.map { it[KEY_UID] }
    val partnerUidFlow: Flow<String?> = context.dataStore.data.map { it[KEY_PARTNER_UID] }
    val inviteCodeFlow: Flow<String?> = context.dataStore.data.map { it[KEY_INVITE_CODE] }
    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }
    val displayNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_DISPLAY_NAME] ?: "考研星人" }

    val sleepStartMinutesFlow: Flow<Int> = context.dataStore.data.map { it[KEY_SLEEP_START] ?: 23 * 60 + 30 }
    val sleepEndMinutesFlow: Flow<Int> = context.dataStore.data.map { it[KEY_SLEEP_END] ?: 6 * 60 }

    val strictLockUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_STRICT_LOCK_UNTIL] ?: 0L }
    val tempExemptUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_TEMP_EXEMPT_UNTIL] ?: 0L }
    val delayGrantedUntilFlow: Flow<Long> = context.dataStore.data.map { it[KEY_DELAY_GRANTED_UNTIL] ?: 0L }

    suspend fun setOnboarded(uid: String, inviteCode: String, partnerUid: String? = null) {
        context.dataStore.edit {
            it[KEY_UID] = uid
            it[KEY_INVITE_CODE] = inviteCode
            partnerUid?.let { p -> it[KEY_PARTNER_UID] = p }
        }
    }

    suspend fun markSetupComplete() {
        context.dataStore.edit { it[KEY_ONBOARDED] = true }
    }

    suspend fun bindPartner(partnerUid: String) {
        context.dataStore.edit {
            it[KEY_PARTNER_UID] = partnerUid
            it[KEY_ONBOARDED] = true
        }
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name }
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
        private val KEY_UID = stringPreferencesKey("uid")
        private val KEY_PARTNER_UID = stringPreferencesKey("partner_uid")
        private val KEY_INVITE_CODE = stringPreferencesKey("invite_code")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_SLEEP_START = intPreferencesKey("sleep_start_minutes")
        private val KEY_SLEEP_END = intPreferencesKey("sleep_end_minutes")
        private val KEY_STRICT_LOCK_UNTIL = longPreferencesKey("strict_lock_until")
        private val KEY_TEMP_EXEMPT_UNTIL = longPreferencesKey("temp_exempt_until")
        private val KEY_DELAY_GRANTED_UNTIL = longPreferencesKey("delay_granted_until")
    }
}
