package com.studyguardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studyguardian.ui.GuardianUiState
import com.studyguardian.ui.components.GlassButton
import com.studyguardian.ui.components.GlassCard

@Composable
fun OnboardingScreen(
    state: GuardianUiState,
    onJoinChannel: (String) -> Unit,
) {
    var channelInput by rememberSaveable { mutableStateOf(state.channel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✨ 考研星人守护", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "零后端 · MQTT 实时结伴",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), breathing = true) {
            Column {
                Text("星际频道号", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "两人约定同一个复杂频道名，即可双向实时通信。无需注册、无需数据库。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "示例：Xingqiu_2026_LOVE（8–64 位，字母数字 _ -）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = channelInput,
            onValueChange = { if (it.length <= 64) channelInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入你们的专属频道号") },
            placeholder = { Text("与 TA 输入完全相同") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        )

        state.bindError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(16.dp))
        GlassButton(
            onClick = { onJoinChannel(channelInput) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("进入频道并开始守护", modifier = Modifier.padding(vertical = 4.dp))
        }

        if (state.mqttConnected) {
            Spacer(Modifier.height(12.dp))
            Text("已连接公共节点 broker.emqx.io 🛰️", color = MaterialTheme.colorScheme.primary)
        }
    }
}
