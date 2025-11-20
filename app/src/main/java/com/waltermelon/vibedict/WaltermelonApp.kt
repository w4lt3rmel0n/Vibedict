package com.waltermelon.vibedict

import android.app.Application
import com.waltermelon.vibedict.data.UserPreferencesRepository

class WaltermelonApp : Application() {
    // The single source of truth for the repository
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        userPreferencesRepository = UserPreferencesRepository(this)
    }
}