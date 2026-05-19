package com.studyguardian.data.mqtt

import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.model.PartnerStatus
import com.studyguardian.data.model.UserState
import com.studyguardian.domain.ChannelValidator
import com.studyguardian.domain.GuardianCoordinator
import com.studyguardian.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class MqttGuardianClient(
    private val context: Context,
    private val prefs: UserPreferences,
    private val onInteraction: (type: String, payload: String?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val payloadAdapter = moshi.adapter(GuardianMqttPayload::class.java)

    private var client: Mqtt3AsyncClient? = null
    private var connectedTopic: String? = null
    private var lastPartnerMessageAt = 0L
    private var lastPlayNotifyAt = 0L

    private val _partnerStatus = MutableStateFlow<PartnerStatus?>(null)
    val partnerStatus: StateFlow<PartnerStatus?> = _partnerStatus.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    fun connectIfNeeded() {
        scope.launch {
            connectMutex.withLock {
                val channel = prefs.channelFlow.first() ?: return@withLock
                val topic = ChannelValidator.toMqttTopic(channel)
                if (connectedTopic == topic &&
                    client?.state == com.hivemq.client.mqtt.MqttClientState.CONNECTED
                ) {
                    return@withLock
                }
                disconnectInternal()
                connectInternal(topic)
            }
        }
    }

    private suspend fun connectInternal(topic: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val deviceId = prefs.ensureDeviceId()
        val clientId = "sg_${deviceId.take(12)}_${System.currentTimeMillis() % 100_000}"

        val mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(BROKER_HOST)
            .serverPort(BROKER_PORT)
            .buildAsync()

        val lwtJson = payloadAdapter.toJson(
            GuardianMqttPayload(
                device_id = deviceId,
                type = GuardianMqttPayload.TYPE_OFFLINE,
                state = UserState.OFFLINE.name.lowercase(),
                timestamp = System.currentTimeMillis(),
            ),
        )

        try {
            val connAck: Mqtt3ConnAck = mqttClient.connect(
                com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect.builder()
                    .keepAlive(60)
                    .willPublish()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(false)
                    .payload(lwtJson.toByteArray(Charsets.UTF_8))
                    .applyWillPublish()
                    .build(),
            ).get(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)

            if (connAck.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                client = mqttClient
                connectedTopic = topic
                _connectionState.value = ConnectionState.CONNECTED

                mqttClient.subscribe(
                    com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe.builder()
                        .topicFilter(topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .build(),
                    { publish -> handleIncoming(publish) },
                ).get(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)

                publishStatus(
                    state = UserState.OFFLINE,
                    screenOn = true,
                    charging = false,
                    foregroundApp = null,
                )
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun handleIncoming(publish: Mqtt3Publish) {
        val json = String(publish.payloadAsBytes, Charsets.UTF_8)
        val message = runCatching { payloadAdapter.fromJson(json) }.getOrNull() ?: return

        scope.launch {
            val myId = prefs.deviceIdFlow.first()
            if (message.device_id == myId) return@launch

            when (message.type) {
                GuardianMqttPayload.TYPE_OFFLINE -> updatePartnerFromMessage(message, forceOffline = true)
                GuardianMqttPayload.TYPE_STATUS -> {
                    updatePartnerFromMessage(message, forceOffline = false)
                    maybeNotifyPartnerPlaying(message)
                }
                GuardianMqttPayload.TYPE_POKE -> onInteraction(GuardianMqttPayload.TYPE_POKE, message.payload)
                GuardianMqttPayload.TYPE_REQUEST_DELAY ->
                    onInteraction(GuardianMqttPayload.TYPE_REQUEST_DELAY, message.payload)
                GuardianMqttPayload.TYPE_APPROVE_DELAY ->
                    onInteraction(GuardianMqttPayload.TYPE_APPROVE_DELAY, message.payload)
            }
        }
    }

    private suspend fun updatePartnerFromMessage(message: GuardianMqttPayload, forceOffline: Boolean) {
        lastPartnerMessageAt = message.timestamp
        val state = if (forceOffline) {
            UserState.OFFLINE
        } else {
            when (message.state.lowercase()) {
                "study" -> UserState.STUDY
                "play" -> UserState.PLAY
                "sleep" -> UserState.SLEEP
                else -> UserState.OFFLINE
            }
        }
        val lost = System.currentTimeMillis() - message.timestamp > GuardianCoordinator.OFFLINE_THRESHOLD_MS
        val effective = if (lost && !forceOffline) UserState.OFFLINE else state
        val status = PartnerStatus(
            state = effective,
            lastUpdateMillis = message.timestamp,
            isSignalLost = lost || forceOffline,
            displayMessage = partnerMessage(effective, lost || forceOffline),
            emoji = partnerEmoji(effective, lost || forceOffline),
        )
        _partnerStatus.value = status
        prefs.cachePartnerStatus(status)
    }

    private fun maybeNotifyPartnerPlaying(message: GuardianMqttPayload) {
        if (message.state.lowercase() != "play") return
        val now = System.currentTimeMillis()
        if (now - lastPlayNotifyAt < 5 * 60_000L) return
        lastPlayNotifyAt = now
        NotificationHelper.showPartnerAlert(
            context,
            "叮咚～",
            "你的考研星人正在偷偷冲浪，去戳戳她吧～",
        )
    }

    fun publishStatus(
        state: UserState,
        screenOn: Boolean,
        charging: Boolean,
        foregroundApp: String?,
    ) {
        scope.launch {
            val deviceId = prefs.deviceIdFlow.first() ?: return@launch
            val payload = GuardianMqttPayload(
                device_id = deviceId,
                type = GuardianMqttPayload.TYPE_STATUS,
                state = state.name.lowercase(),
                screen_on = screenOn,
                charging = charging,
                foreground_app = foregroundApp,
                timestamp = System.currentTimeMillis(),
            )
            publish(payload)
        }
    }

    fun publishPoke() {
        scope.launch {
            val deviceId = prefs.deviceIdFlow.first() ?: return@launch
            publish(
                GuardianMqttPayload(
                    device_id = deviceId,
                    type = GuardianMqttPayload.TYPE_POKE,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun publishRequestDelay(minutes: Int = 10) {
        scope.launch {
            val deviceId = prefs.deviceIdFlow.first() ?: return@launch
            publish(
                GuardianMqttPayload(
                    device_id = deviceId,
                    type = GuardianMqttPayload.TYPE_REQUEST_DELAY,
                    payload = minutes.toString(),
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun publishApproveDelay(minutes: Int = 10) {
        scope.launch {
            val deviceId = prefs.deviceIdFlow.first() ?: return@launch
            publish(
                GuardianMqttPayload(
                    device_id = deviceId,
                    type = GuardianMqttPayload.TYPE_APPROVE_DELAY,
                    payload = minutes.toString(),
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun publish(payload: GuardianMqttPayload) {
        connectMutex.withLock {
            if (client == null || client?.state != com.hivemq.client.mqtt.MqttClientState.CONNECTED) {
                val channel = prefs.channelFlow.first() ?: return
                connectInternal(ChannelValidator.toMqttTopic(channel))
            }
            val topic = connectedTopic ?: return
            val c = client ?: return
            if (c.state != com.hivemq.client.mqtt.MqttClientState.CONNECTED) return
            val json = payloadAdapter.toJson(payload)
            c.publish(
                com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish.builder()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(false)
                    .payload(json.toByteArray(Charsets.UTF_8))
                    .build(),
            ).get(10, TimeUnit.SECONDS)
        }
    }

    fun disconnectGracefully() {
        scope.launch {
            connectMutex.withLock {
                val deviceId = prefs.deviceIdFlow.first()
                if (deviceId != null && connectedTopic != null) {
                    val offline = GuardianMqttPayload(
                        device_id = deviceId,
                        type = GuardianMqttPayload.TYPE_OFFLINE,
                        state = UserState.OFFLINE.name.lowercase(),
                        timestamp = System.currentTimeMillis(),
                    )
                    runCatching { publish(offline) }
                }
                disconnectInternal()
            }
        }
    }

    private fun disconnectInternal() {
        runCatching {
            client?.disconnect()?.get(5, TimeUnit.SECONDS)
        }
        client = null
        connectedTopic = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun startStalePartnerWatch() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val last = lastPartnerMessageAt
                if (last == 0L) continue
                if (System.currentTimeMillis() - last > GuardianCoordinator.OFFLINE_THRESHOLD_MS) {
                    val current = _partnerStatus.value
                    if (current != null && !current.isSignalLost) {
                        _partnerStatus.value = current.copy(
                            state = UserState.OFFLINE,
                            isSignalLost = true,
                            displayMessage = "星人信号丢失中 📡",
                            emoji = "📡",
                        )
                    }
                }
            }
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

    companion object {
        const val BROKER_HOST = "broker.emqx.io"
        const val BROKER_PORT = 1883
        private const val CONNECT_TIMEOUT_SEC = 15L
    }
}
