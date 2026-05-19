package com.studyguardian

import android.app.Application
import com.studyguardian.data.local.UserPreferences
import com.studyguardian.data.remote.BaasRepository
import com.studyguardian.domain.GuardianCoordinator

class StudyGuardianApp : Application() {

    lateinit var preferences: UserPreferences
        private set

    lateinit var baasRepository: BaasRepository
        private set

    lateinit var coordinator: GuardianCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = UserPreferences(this)
        baasRepository = BaasRepository(this)
        coordinator = GuardianCoordinator(this, preferences, baasRepository)
    }
}
