package com.studyguardian.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.studyguardian.domain.StudyWhitelist
import com.studyguardian.util.PermissionHelper

class UsageMonitor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun foregroundPackage(): String? {
        if (!PermissionHelper.hasUsageStatsPermission(context)) return null
        val end = System.currentTimeMillis()
        val start = end - 15_000
        val events = usageStatsManager.queryEvents(start, end)
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    fun isStudying(): Boolean {
        val pkg = foregroundPackage() ?: return true
        if (pkg == context.packageName) return true
        return StudyWhitelist.isWhitelisted(pkg)
    }

    fun isPlaying(): Boolean {
        val pkg = foregroundPackage() ?: return false
        if (pkg == context.packageName) return false
        return !StudyWhitelist.isWhitelisted(pkg)
    }
}
