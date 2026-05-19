package com.studyguardian.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.model.PartnerStatus
import com.studyguardian.domain.InviteCodeGenerator
import com.studyguardian.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuardianUiState(
    val onboarded: Boolean = false,
    val inviteCode: String = "",
    val uid: String = "",
    val partnerUid: String? = null,
    val partnerStatus: PartnerStatus? = null,
    val hasUsagePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val bindError: String? = null,
)

class GuardianViewModel(app: Application) : AndroidViewModel(app) {

    private val studyApp = app as StudyGuardianApp
    private val prefs = studyApp.preferences
    private val baas = studyApp.baasRepository
    private val coordinator = studyApp.coordinator

    private val _partnerStatus = MutableStateFlow<PartnerStatus?>(null)
    private val _bindError = MutableStateFlow<String?>(null)
    private val _permissionTick = MutableStateFlow(0)

    val uiState: StateFlow<GuardianUiState> = combine(
        prefs.onboardedFlow,
        prefs.inviteCodeFlow,
        prefs.uidFlow,
        prefs.partnerUidFlow,
        _partnerStatus,
        _bindError,
        _permissionTick,
    ) { onboarded, code, uid, partner, status, err, _ ->
        GuardianUiState(
            onboarded = onboarded,
            inviteCode = code ?: "",
            uid = uid ?: "",
            partnerUid = partner,
            partnerStatus = status,
            hasUsagePermission = PermissionHelper.hasUsageStatsPermission(app),
            hasOverlayPermission = PermissionHelper.canDrawOverlays(app),
            bindError = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardianUiState())

    init {
        viewModelScope.launch {
            if (prefs.uidFlow.first() == null) {
                createLocalIdentity()
            }
            refreshPartnerLoop()
        }
    }

    private suspend fun createLocalIdentity() {
        val uid = InviteCodeGenerator.generateUid()
        val code = InviteCodeGenerator.generateInviteCode()
        prefs.setOnboarded(uid, code, partnerUid = null)
        baas.upsertUser(
            com.studyguardian.data.model.UserRecord(
                uid = uid,
                invite_code = code,
                current_state = "offline",
            ),
        )
    }

    fun bindPartner(inputCode: String) {
        viewModelScope.launch {
            _bindError.value = null
            if (inputCode.length != 6) {
                _bindError.value = "请输入 6 位星际坐标"
                return@launch
            }
            val partner = baas.findUserByInviteCode(inputCode)
            if (partner == null) {
                _bindError.value = "找不到这个坐标，再确认一下？"
                return@launch
            }
            val myUid = prefs.uidFlow.first() ?: return@launch
            val myCode = prefs.inviteCodeFlow.first()
            prefs.bindPartner(partner.uid)
            baas.upsertUser(
                com.studyguardian.data.model.UserRecord(
                    uid = myUid,
                    partner_uid = partner.uid,
                    invite_code = myCode,
                    current_state = "offline",
                ),
            )
            baas.upsertUser(partner.copy(partner_uid = myUid))
            prefs.markSetupComplete()
            coordinator.startGuardianServices()
        }
    }

    fun pokePartner() {
        viewModelScope.launch {
            val partner = prefs.partnerUidFlow.first() ?: return@launch
            coordinator.sendPoke(partner)
        }
    }

    fun approvePartnerDelay() {
        viewModelScope.launch {
            val uid = prefs.uidFlow.first() ?: return@launch
            val pending = baas.pollPendingInteractions(uid)
                .firstOrNull { it.action_type == "request_delay" } ?: return@launch
            coordinator.approveSleepDelay(pending.sender_id, pending.objectId ?: return@launch)
        }
    }

    fun refreshPermissions() {
        _permissionTick.value++
    }

    private suspend fun refreshPartnerLoop() {
        while (true) {
            val partner = prefs.partnerUidFlow.first()
            if (partner != null) {
                _partnerStatus.value = baas.fetchPartnerStatus(partner)
            }
            delay(12_000)
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefs.markSetupComplete()
            coordinator.startGuardianServices()
        }
    }

    fun skipPartnerForNow() {
        viewModelScope.launch { prefs.markSetupComplete() }
    }
}
