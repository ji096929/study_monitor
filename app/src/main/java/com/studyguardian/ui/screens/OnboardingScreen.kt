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
    onBind: (String) -> Unit,
    onContinueSolo: () -> Unit,
) {
    var inputCode by rememberSaveable { mutableStateOf("") }

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
            "灵魂结对 · 双向平等陪伴",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), breathing = true) {
            Column {
                Text("你的星际坐标", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    state.inviteCode.ifBlank { "······" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "发给 TA，或输入对方的坐标完成绑定",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = inputCode,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inputCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入 TA 的 6 位坐标") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        )

        state.bindError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(16.dp))
        GlassButton(
            onClick = { onBind(inputCode) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("完成结对", modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        GlassButton(
            onClick = onContinueSolo,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text("先自己逛逛", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
