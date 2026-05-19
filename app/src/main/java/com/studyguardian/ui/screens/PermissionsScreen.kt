package com.studyguardian.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.studyguardian.ui.GuardianUiState
import com.studyguardian.ui.components.GlassButton
import com.studyguardian.ui.components.GlassCard
import com.studyguardian.util.PermissionHelper

@Composable
fun PermissionsScreen(
    state: GuardianUiState,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("开启守护权限", style = MaterialTheme.typography.headlineLarge)
        Text(
            "都是为了温柔陪伴，不会上传多余隐私～",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("使用情况访问", style = MaterialTheme.typography.titleLarge)
                Text(if (state.hasUsagePermission) "✅ 已开启" else "用于识别专注 / 摸鱼")
                if (!state.hasUsagePermission) {
                    GlassButton(onClick = {
                        context.startActivity(PermissionHelper.usageStatsIntent())
                    }) { Text("去开启") }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("悬浮窗", style = MaterialTheme.typography.titleLarge)
                Text(if (state.hasOverlayPermission) "✅ 已开启" else "爱心戳戳 & 晚安遮罩")
                if (!state.hasOverlayPermission) {
                    GlassButton(onClick = {
                        context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                    }) { Text("去开启") }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("电池优化白名单", style = MaterialTheme.typography.titleLarge)
                Text("保持低调前台守护")
                if (!PermissionHelper.isIgnoringBatteryOptimizations(context)) {
                    GlassButton(onClick = {
                        context.startActivity(PermissionHelper.batteryOptimizationIntent(context))
                    }) { Text("去设置") }
                } else {
                    Text("✅ 已忽略优化")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        GlassButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("开始守护")
        }
    }
}
