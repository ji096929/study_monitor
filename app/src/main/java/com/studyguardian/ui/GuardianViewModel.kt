package com.studyguardian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.mqtt.MqttGuardianClient
import com.studyguardian.domain.ChannelValidator
import com.studyguardian.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuardianUiState(
    val onboarded: Boolean = false,
    val channel: String = "",
    val partnerStatus: com.studyguardian.data.model.PartnerStatus? = null,
    val mqttConnected: Boolean = false,
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val bindError: String? = null,
)

class GuardianViewModel(app: Application) : AndroidViewModel(app) {

    private val studyApp = app as StudyGuardianApp
    private val prefs = studyApp.preferences
    private val mqtt = studyApp.mqttClient
    private val coordinator = studyApp.coordinator

    private val _bindError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GuardianUiState> = combine(
        prefs.onboardedFlow,
        prefs.channelFlow,
        mqtt.partnerStatus,
        mqtt.connectionState,
        _bindError,
    ) { onboarded, channel, partner, conn, err ->
        GuardianUiState(
            onboarded = onboarded,
            channel = channel ?: "",
            partnerStatus = partner,
            mqttConnected = conn == MqttGuardianClient.ConnectionState.CONNECTED,
            hasUsagePermission = PermissionHelper.hasUsageStatsPermission(app),
            hasOverlayPermission = PermissionHelper.canDrawOverlays(app),
            bindError = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardianUiState())

    init {
        viewModelScope.launch {
            prefs.ensureDeviceId()
            if (prefs.channelFlow.first() != null) {
                mqtt.connectIfNeeded()
            }
        }
    }

    fun joinChannel(rawChannel: String) {
        viewModelScope.launch {
            _bindError.value = null
            val validated = ChannelValidator.validate(rawChannel)
            if (validated.isFailure) {
                _bindError.value = validated.exceptionOrNull()?.message
                return@launch
            }
            prefs.setChannel(validated.getOrThrow())
            mqtt.connectIfNeeded()
            coordinator.startGuardianServices()
        }
    }

    fun pokePartner() {
        coordinator.sendPoke()
    }

    fun approvePartnerDelay() {
        coordinator.sendApproveDelay(10)
    }

    fun refreshPermissions() {
        _bindError.value = _bindError.value
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefs.markSetupComplete()
            mqtt.connectIfNeeded()
            coordinator.startGuardianServices()
        }
    }
}
