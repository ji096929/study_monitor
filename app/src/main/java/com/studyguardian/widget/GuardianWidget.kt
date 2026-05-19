package com.studyguardian.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.studyguardian.StudyGuardianApp
import com.studyguardian.data.model.UserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuardianWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val status = withContext(Dispatchers.IO) {
            WidgetStatusLoader.load(context)
        }
        provideContent { WidgetContent(status) }
    }
}

class GuardianWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GuardianWidget()
}

data class WidgetStatus(
    val emoji: String,
    val message: String,
    val bgColor: Color,
)

object WidgetStatusLoader {
    suspend fun load(context: Context): WidgetStatus {
        val app = context.applicationContext as StudyGuardianApp
        val live = app.mqttClient.partnerStatus.value
        val status = live ?: app.preferences.readCachedPartnerStatus()
        if (status == null) {
            return WidgetStatus("💫", "等待进入星际频道…", Color(0xFFF5EBE0))
        }
        val bg = when {
            status.isSignalLost -> Color(0xFFB0BEC5)
            status.state == UserState.STUDY -> Color(0xFF9FD8B7)
            status.state == UserState.PLAY -> Color(0xFFFFB5A7)
            status.state == UserState.SLEEP -> Color(0xFF8B9DC3)
            else -> Color(0xFFF5EBE0)
        }
        return WidgetStatus(status.emoji, status.displayMessage, bg)
    }
}

@Composable
private fun WidgetContent(status: WidgetStatus) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(status.bgColor))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status.emoji, style = TextStyle(fontSize = 28.sp))
        Text(
            status.message,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(Color(0xFF4A4A4A)),
            ),
        )
    }
}
