package com.neo.chevere.data.security

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import timber.log.Timber

private const val TAG = "AppCheckInitializer"

/**
 * Release build variant initializer for Firebase App Check.
 *
 * Installs [PlayIntegrityAppCheckProviderFactory] so Firestore requests carry
 * Google Play Integrity attestation tokens in production and closed testing.
 */
object AppCheckInitializer {
    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: return
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Firebase App Check initialization failed")
        }
    }
}
