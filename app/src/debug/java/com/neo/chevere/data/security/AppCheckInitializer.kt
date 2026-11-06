package com.neo.chevere.data.security

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import timber.log.Timber

private const val TAG = "AppCheckInitializer"

/**
 * Debug build variant initializer for Firebase App Check.
 *
 * Installs [DebugAppCheckProviderFactory] for local development and emulator testing
 * while keeping the debug provider dependency out of release binaries.
 */
object AppCheckInitializer {
    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: return
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w("Firebase App Check initialization skipped in debug environment")
        }
    }
}
