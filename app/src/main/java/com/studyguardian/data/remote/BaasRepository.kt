package com.studyguardian.data.remote

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.studyguardian.BuildConfig
import com.studyguardian.data.model.InteractionAction
import com.studyguardian.data.model.InteractionRecord
import com.studyguardian.data.model.PartnerStatus
import com.studyguardian.data.model.UserRecord
import com.studyguardian.data.model.UserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST adapter for MemFire / LeanCloud-style BaaS.
 * Configure BAAS_* fields in app/build.gradle.kts before release.
 */
class BaasRepository(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private val appId = BuildConfig.BAAS_APP_ID.ifBlank {
        context.getSharedPreferences("baas_debug", Context.MODE_PRIVATE)
            .getString("app_id", "") ?: ""
    }
    private val appKey = BuildConfig.BAAS_APP_KEY.ifBlank {
        context.getSharedPreferences("baas_debug", Context.MODE_PRIVATE)
            .getString("app_key", "") ?: ""
    }
    private val baseUrl = BuildConfig.BAAS_SERVER_URL.trimEnd('/')

    val isConfigured: Boolean get() = appId.isNotBlank() && appKey.isNotBlank() && baseUrl.isNotBlank()

    suspend fun upsertUser(record: UserRecord): Result<UserRecord> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.success(record)
        runCatching {
            val body = JSONObject().apply {
                put("uid", record.uid)
                record.partner_uid?.let { put("partner_uid", it) }
                record.invite_code?.let { put("invite_code", it) }
                put("current_state", record.current_state)
                put("last_update_time", record.last_update_time ?: isoNow())
            }.toString()
            val existing = findUserByUid(record.uid)
            if (existing?.objectId != null) {
                patch("classes/Users/${existing.objectId}", body)
                record.copy(objectId = existing.objectId)
            } else {
                val created = post("classes/Users", body)
                record.copy(objectId = created.optString("objectId"))
            }
        }
    }

    suspend fun findUserByInviteCode(code: String): UserRecord? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        runCatching {
            val where = JSONObject().put("invite_code", code).toString()
            val url = "$baseUrl/1.1/classes/Users?where=${java.net.URLEncoder.encode(where, "UTF-8")}&limit=1"
            get(url)?.optJSONArray("results")?.optJSONObject(0)?.let { parseUser(it) }
        }.getOrNull()
    }

    suspend fun findUserByUid(uid: String): UserRecord? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        runCatching {
            val where = JSONObject().put("uid", uid).toString()
            val url = "$baseUrl/1.1/classes/Users?where=${java.net.URLEncoder.encode(where, "UTF-8")}&limit=1"
            get(url)?.optJSONArray("results")?.optJSONObject(0)?.let { parseUser(it) }
        }.getOrNull()
    }

    suspend fun fetchPartnerStatus(partnerUid: String): PartnerStatus? = withContext(Dispatchers.IO) {
        val user = findUserByUid(partnerUid) ?: return@withContext null
        val state = when (user.current_state.lowercase()) {
            "study" -> UserState.STUDY
            "play" -> UserState.PLAY
            "sleep" -> UserState.SLEEP
            else -> UserState.OFFLINE
        }
        val lastMs = parseIso(user.last_update_time)
        val offlineThreshold = 15 * 60 * 1000L
        val lost = System.currentTimeMillis() - lastMs > offlineThreshold
        val effective = if (lost) UserState.OFFLINE else state
        PartnerStatus(
            state = effective,
            lastUpdateMillis = lastMs,
            isSignalLost = lost,
            displayMessage = partnerMessage(effective, lost),
            emoji = partnerEmoji(effective, lost),
        )
    }

    suspend fun sendInteraction(
        senderId: String,
        receiverId: String,
        action: InteractionAction,
        payload: String? = null,
    ): Result<InteractionRecord> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.success(
                InteractionRecord(
                    sender_id = senderId,
                    receiver_id = receiverId,
                    action_type = action.name.lowercase(),
                    status = "pending",
                    payload = payload,
                ),
            )
        }
        runCatching {
            val body = JSONObject().apply {
                put("sender_id", senderId)
                put("receiver_id", receiverId)
                put("action_type", action.name.lowercase())
                put("status", "pending")
                payload?.let { put("payload", it) }
            }.toString()
            val resp = post("classes/Interactions", body)
            InteractionRecord(
                objectId = resp.optString("objectId"),
                sender_id = senderId,
                receiver_id = receiverId,
                action_type = action.name.lowercase(),
                status = "pending",
                payload = payload,
            )
        }
    }

    suspend fun pollPendingInteractions(receiverId: String): List<InteractionRecord> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext emptyList()
            runCatching {
                val where = JSONObject()
                    .put("receiver_id", receiverId)
                    .put("status", "pending")
                    .toString()
                val url =
                    "$baseUrl/1.1/classes/Interactions?where=${java.net.URLEncoder.encode(where, "UTF-8")}&order=-createdAt"
                val arr = get(url)?.optJSONArray("results") ?: return@runCatching emptyList()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    InteractionRecord(
                        objectId = o.optString("objectId"),
                        sender_id = o.optString("sender_id"),
                        receiver_id = o.optString("receiver_id"),
                        action_type = o.optString("action_type"),
                        status = o.optString("status"),
                        payload = o.optString("payload").takeIf { it.isNotBlank() },
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun markInteractionProcessed(objectId: String) = withContext(Dispatchers.IO) {
        if (!isConfigured || objectId.isBlank()) return@withContext
        runCatching {
            patch("classes/Interactions/$objectId", JSONObject().put("status", "processed").toString())
        }
    }

    private fun partnerMessage(state: UserState, lost: Boolean) = when {
        lost -> "星人信号丢失中 📡"
        state == UserState.STUDY -> "乖乖复习中，不打扰～"
        state == UserState.PLAY -> "捉到一只偷懒的小朋友👀"
        state == UserState.SLEEP -> "她已盖好小被子，晚安啦 🌙"
        else -> "等待星人上线中…"
    }

    private fun partnerEmoji(state: UserState, lost: Boolean) = when {
        lost -> "📡"
        state == UserState.STUDY -> "🌱"
        state == UserState.PLAY -> "🐱"
        state == UserState.SLEEP -> "🌙"
        else -> "💤"
    }

    private fun parseUser(o: JSONObject) = UserRecord(
        objectId = o.optString("objectId"),
        uid = o.optString("uid"),
        partner_uid = o.optString("partner_uid").takeIf { it.isNotBlank() },
        invite_code = o.optString("invite_code").takeIf { it.isNotBlank() },
        current_state = o.optString("current_state", "offline"),
        last_update_time = o.optString("last_update_time").takeIf { it.isNotBlank() },
    )

    private fun get(url: String): JSONObject? {
        val req = Request.Builder().url(url).headers(headers()).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return JSONObject(resp.body?.string() ?: "{}")
        }
    }

    private fun post(path: String, body: String): JSONObject {
        val req = Request.Builder()
            .url("$baseUrl/1.1/$path")
            .headers(headers())
            .post(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) error("BaaS POST failed: ${resp.code} $text")
            return JSONObject(text)
        }
    }

    private fun patch(path: String, body: String) {
        val req = Request.Builder()
            .url("$baseUrl/1.1/$path")
            .headers(headers())
            .patch(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("BaaS PATCH failed: ${resp.code}")
        }
    }

    private fun headers() = okhttp3.Headers.Builder()
        .add("X-LC-Id", appId)
        .add("X-LC-Key", appKey)
        .add("Content-Type", "application/json")
        .build()

    private fun isoNow(): String =
        java.time.Instant.now().toString()

    private fun parseIso(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }
}
