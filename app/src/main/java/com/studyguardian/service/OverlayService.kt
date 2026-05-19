package com.studyguardian.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.studyguardian.StudyGuardianApp
import com.studyguardian.domain.SleepDelayHandler
import com.studyguardian.ui.components.GlassButton
import com.studyguardian.ui.theme.GlassDark
import com.studyguardian.ui.theme.LavenderGray
import com.studyguardian.ui.theme.NavyBlue
import com.studyguardian.ui.theme.PeachPink
import com.studyguardian.ui.theme.StudyGuardianTheme
import com.studyguardian.util.HapticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : android.app.Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

  private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_MODE)) {
            MODE_POKE -> showOverlay(
                dark = false,
                message = intent.getStringExtra(EXTRA_MESSAGE)
                    ?: "休息一下眼睛吧，考研辛苦啦～\n(来自某人的爱心戳戳)",
                showDismissStudy = true,
                showDelayRequest = false,
                showTempExempt = false,
            )
            MODE_SLEEP -> showOverlay(
                dark = true,
                message = intent.getStringExtra(EXTRA_MESSAGE)
                    ?: "今天已经超级棒啦，现在是魔法睡觉时间，再不闭眼明天会变笨哦～",
                showDismissStudy = false,
                showDelayRequest = intent.getBooleanExtra(EXTRA_CAN_REQUEST_DELAY, true),
                showTempExempt = false,
            )
            MODE_STRICT -> showOverlay(
                dark = true,
                message = intent.getStringExtra(EXTRA_MESSAGE) ?: "严格锁死模式：该睡觉啦～",
                showDismissStudy = false,
                showDelayRequest = false,
                showTempExempt = true,
            )
            MODE_DISMISS -> removeOverlay()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(
        dark: Boolean,
        message: String,
        showDismissStudy: Boolean,
        showDelayRequest: Boolean,
        showTempExempt: Boolean,
    ) {
        removeOverlay()
        if (!com.studyguardian.util.PermissionHelper.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        HapticHelper.pokeVibrate(this)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                StudyGuardianTheme {
                    OverlayContent(
                        dark = dark,
                        message = message,
                        showDismissStudy = showDismissStudy,
                        showDelayRequest = showDelayRequest,
                        showTempExempt = showTempExempt,
                        onDismiss = { removeOverlay(); stopSelf() },
                        onRequestDelay = {
                            val app = application as StudyGuardianApp
                            CoroutineScope(Dispatchers.Main).launch {
                                SleepDelayHandler(app.preferences, app.mqttClient)
                                    .requestWithAutoFallback(this@OverlayService)
                            }
                            removeOverlay()
                            stopSelf()
                        },
                        onTempExempt = {
                            val app = application as StudyGuardianApp
                            CoroutineScope(Dispatchers.Main).launch {
                                val until = System.currentTimeMillis() + 3 * 60_000L
                                app.preferences.setTempExemptUntil(until)
                            }
                            removeOverlay()
                            stopSelf()
                        },
                    )
                }
            }
        }
        overlayView = composeView
        windowManager?.addView(composeView, params)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_CAN_REQUEST_DELAY = "can_request_delay"
        const val MODE_POKE = "poke"
        const val MODE_SLEEP = "sleep"
        const val MODE_STRICT = "strict"
        const val MODE_DISMISS = "dismiss"

        fun showPoke(context: Context, senderName: String) {
            context.startService(intent(context, MODE_POKE).apply {
                putExtra(EXTRA_MESSAGE, "休息一下眼睛吧，考研辛苦啦～\n(来自 $senderName 的爱心戳戳)")
            })
        }

        fun showSleepMask(context: Context, canRequestDelay: Boolean, message: String? = null) {
            context.startService(intent(context, MODE_SLEEP).apply {
                putExtra(EXTRA_CAN_REQUEST_DELAY, canRequestDelay)
                message?.let { putExtra(EXTRA_MESSAGE, it) }
            })
        }

        fun showStrictLock(context: Context, message: String) {
            context.startService(intent(context, MODE_STRICT).apply {
                putExtra(EXTRA_MESSAGE, message)
            })
        }

        private fun intent(context: Context, mode: String) =
            Intent(context, OverlayService::class.java).putExtra(EXTRA_MODE, mode)
    }
}

@Composable
private fun OverlayContent(
    dark: Boolean,
    message: String,
    showDismissStudy: Boolean,
    showDelayRequest: Boolean,
    showTempExempt: Boolean,
    onDismiss: () -> Unit,
    onRequestDelay: () -> Unit,
    onTempExempt: () -> Unit,
) {
    val bg = if (dark) {
        Brush.verticalGradient(listOf(NavyBlue.copy(alpha = 0.92f), LavenderGray.copy(alpha = 0.88f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), PeachPink.copy(alpha = 0.35f)))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (dark) "🌙" else "💕",
            fontSize = 48.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (dark) Color.White else GlassDark,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        if (showDismissStudy) {
            GlassButton(onClick = onDismiss, containerColor = com.studyguardian.ui.theme.MatchaGreen) {
                Text("我知道啦，这就去学习", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        if (showDelayRequest) {
            GlassButton(onClick = onRequestDelay, containerColor = PeachPink) {
                Text("向 TA 撒娇申请再玩 10 分钟", modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        if (showTempExempt) {
            Spacer(Modifier.height(12.dp))
            GlassButton(onClick = onTempExempt, containerColor = LavenderGray) {
                Text("临时豁免 3 分钟", color = Color.White)
            }
        }
    }
}
