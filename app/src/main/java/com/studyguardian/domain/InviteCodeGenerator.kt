package com.studyguardian.domain

import java.security.SecureRandom
import java.util.UUID

object InviteCodeGenerator {
    fun generateInviteCode(): String {
        val n = SecureRandom().nextInt(900_000) + 100_000
        return n.toString()
    }

    fun generateUid(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}
