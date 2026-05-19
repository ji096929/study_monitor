package com.studyguardian.domain

object ChannelValidator {
    private val CHANNEL_PATTERN = Regex("^[a-zA-Z0-9_-]{8,64}$")

    fun validate(raw: String): Result<String> {
        val channel = raw.trim()
        if (channel.length < 8) {
            return Result.failure(IllegalArgumentException("星际频道号至少 8 位"))
        }
        if (channel.length > 64) {
            return Result.failure(IllegalArgumentException("频道号过长"))
        }
        if (!CHANNEL_PATTERN.matches(channel)) {
            return Result.failure(
                IllegalArgumentException("仅支持字母、数字、下划线与连字符"),
            )
        }
        return Result.success(channel)
    }

    /** MQTT Topic：两人输入相同频道号即订阅同一主题 */
    fun toMqttTopic(channel: String): String = "studyguardian/$channel"
}
