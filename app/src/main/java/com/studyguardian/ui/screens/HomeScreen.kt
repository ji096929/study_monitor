package com.studyguardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyguardian.data.model.PartnerStatus
import com.studyguardian.data.model.UserState
import com.studyguardian.ui.GuardianUiState
import com.studyguardian.ui.components.GlassButton
import com.studyguardian.ui.components.GlassCard
import com.studyguardian.ui.theme.CoralOrange
import com.studyguardian.ui.theme.MatchaGreen
import com.studyguardian.ui.theme.MintGreen
import com.studyguardian.ui.theme.NavyBlue
import com.studyguardian.ui.theme.OfflineGray
import com.studyguardian.ui.theme.PeachPink

@Composable
fun HomeScreen(
    state: GuardianUiState,
    onPoke: () -> Unit,
    onApproveDelay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partner = state.partnerStatus
    val cardColor = when {
        partner?.isSignalLost == true -> OfflineGray.copy(alpha = 0.35f)
        partner?.state == UserState.STUDY -> MintGreen.copy(alpha = 0.45f)
        partner?.state == UserState.PLAY -> PeachPink.copy(alpha = 0.45f)
        partner?.state == UserState.SLEEP -> NavyBlue.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.5f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("考研星人守护", style = MaterialTheme.typography.headlineLarge)
        Text(
            "频道：${state.channel} · ${if (state.mqttConnected) "MQTT 已连接" else "连接中…"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = cardColor,
            breathing = partner?.state == UserState.STUDY,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(partner?.emoji ?: "💫", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    partner?.displayMessage ?: "等待 TA 上线…",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                if (partner?.state == UserState.SLEEP) {
                    Spacer(Modifier.height(4.dp))
                    Text("月亮悄悄亮起来啦", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(
                onClick = onPoke,
                modifier = Modifier.weight(1f),
                containerColor = MatchaGreen,
            ) {
                Text("戳一下 💕", color = Color.White)
            }
            GlassButton(
                onClick = onApproveDelay,
                modifier = Modifier.weight(1f),
                containerColor = CoralOrange,
            ) {
                Text("同意延时", color = Color.White)
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("温柔提醒", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("• 状态经 MQTT 实时广播，无云端数据库")
                Text("• 非白名单 App → 对方收到「偷偷冲浪」推送")
                Text("• 断网/杀进程 → 遗嘱消息通知离线")
            }
        }
    }
}
