package com.studyguardian.util

import java.util.Calendar

object SleepSchedule {
    fun isBedtime(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        return if (startMinutes > endMinutes) {
            // e.g. 23:30 - 06:00 crosses midnight
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        } else {
            nowMinutes in startMinutes until endMinutes
        }
    }

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}
