package com.studyguardian

import android.app.Application
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.mqtt.MqttGuardianClient
import com.studyguardian.domain.GuardianCoordinator

class StudyGuardianApp : Application() {

    lateinit var preferences: UserPreferences
        private set

    lateinit var mqttClient: MqttGuardianClient
        private set

    lateinit var coordinator: GuardianCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = UserPreferences(this)
        coordinator = GuardianCoordinator(this, preferences)
        mqttClient = MqttGuardianClient(this, preferences, coordinator::onPartnerMqttMessage)
        coordinator.attachMqtt(mqttClient)
    }
}
