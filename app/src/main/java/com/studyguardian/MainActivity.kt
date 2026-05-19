package com.studyguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyguardian.ui.GuardianViewModel
import com.studyguardian.ui.screens.HomeScreen
import com.studyguardian.ui.screens.OnboardingScreen
import com.studyguardian.ui.screens.PermissionsScreen
import com.studyguardian.ui.theme.StudyGuardianTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val app = application as StudyGuardianApp
            if (app.preferences.onboardedFlow.first()) {
                app.mqttClient.connectIfNeeded()
                app.coordinator.startGuardianServices()
            }
        }

        setContent {
            StudyGuardianTheme {
                val vm: GuardianViewModel = viewModel()
                val state by vm.uiState.collectAsState()
                var showPermissions by remember { mutableStateOf(false) }
                val needsPermissions = !state.hasUsagePermission || !state.hasOverlayPermission

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when {
                        needsPermissions || showPermissions -> {
                            PermissionsScreen(
                                state = state,
                                onDone = {
                                    vm.completeSetup()
                                    vm.refreshPermissions()
                                    showPermissions = false
                                },
                            )
                        }
                        !state.onboarded || state.channel.isBlank() -> {
                            OnboardingScreen(
                                state = state,
                                onJoinChannel = vm::joinChannel,
                            )
                        }
                        else -> {
                            HomeScreen(
                                state = state,
                                onPoke = vm::pokePartner,
                                onApproveDelay = vm::approvePartnerDelay,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }
}
