package com.studyguardian.domain

/**
 * Default study whitelist — extend via settings in future versions.
 */
object StudyWhitelist {
    val packageNames = setOf(
        "com.forestapp",           // Forest
        "com.pomodoro.timer",      // generic pomodoro
        "com.superlib",            // 学习通
        "com.chaoxing.mobile",     // 超星
        "com.anki.android",        // Anki
        "com.maimemo.android.momo", // 墨墨背单词
        "com.shanbay.news",        // 扇贝
        "com.tencent.mm",          // WeChat — optional, remove if too permissive
        "com.studyguardian",       // self
    )

    fun isWhitelisted(packageName: String?) =
        packageName != null && packageNames.any { packageName == it || packageName.startsWith("$it.") }
}
