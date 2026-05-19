package com.studyguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyguardian.StudyGuardianApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as StudyGuardianApp
        app.coordinator.startGuardianServices()
    }
}
