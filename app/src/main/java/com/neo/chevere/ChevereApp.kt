package com.neo.chevere

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.inference.InferenceManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ChevereApp"

@HiltAndroidApp
class ChevereApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var inferenceManager: InferenceManager

    @Inject
    lateinit var imageGenerationManager: ImageGenerationManager

    private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        registerBackgroundLifecycleTracker()
    }

    private fun registerBackgroundLifecycleTracker() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            private var backgroundJob: Job? = null

            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                if (startedActivities == 1) {
                    // App came to foreground
                    Timber.tag(TAG).d("App came to foreground. Cancelling unload timer.")
                    backgroundJob?.cancel()
                    backgroundJob = null
                }
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    // App went to background
                    Timber.tag(TAG).d("App went to background. Starting 3-minute unload timer.")
                    backgroundJob?.cancel()
                    backgroundJob = appScope.launch {
                        delay(3 * 60 * 1000L) // 3 minutes
                        Timber.tag(TAG).i("App has been in background for 3 minutes. Unloading local models to free RAM.")
                        try {
                            inferenceManager.unload()
                            imageGenerationManager.unload()
                            Timber.tag(TAG).i("Unloaded local models successfully.")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to unload models on backgrounding")
                        }
                    }
                }
            }

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.ERROR || priority == Log.WARN) {
                try {
                    val crashlytics = FirebaseCrashlytics.getInstance()
                    val priorityStr = when (priority) {
                        Log.WARN -> "WARN"
                        Log.ERROR -> "ERROR"
                        Log.ASSERT -> "ASSERT"
                        else -> "UNKNOWN"
                    }
                    crashlytics.log("$priorityStr/${tag ?: "Timber"}: $message")
                    if (t != null) {
                        crashlytics.recordException(t)
                    } else {
                        crashlytics.recordException(Exception(message))
                    }
                } catch (e: Exception) {
                    // Fallback to standard Logcat if Firebase/Crashlytics is not initialized or fails
                    Log.println(priority, tag, message)
                }
            }
        }
    }
}

