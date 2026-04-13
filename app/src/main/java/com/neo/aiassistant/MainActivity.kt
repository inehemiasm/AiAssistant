package com.neo.aiassistant

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.ui.designsystem.HighTechAiTheme
import com.neo.aiassistant.ui.navigation.AiAssistantApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the AI Assistant application.
 *
 * Handles splash screen transitions, theme management, and edge-to-edge layout.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Track when we started so we can ensure minimum display time
        val startTime = System.currentTimeMillis()

        // Set up splash screen exit animation
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val splashView = splashScreenView.view
            
            // Safely access iconView to avoid NullPointerException on some API 31+ devices
            // This fixes a known bug in the splashscreen library where getIconView() can throw NPE.
            val iconView = try {
                splashScreenView.iconView
            } catch (e: Exception) {
                null
            }

            // The "wink" animation in avd_wink.xml ends at 800ms.
            // We want to ensure it finishes before we fade out.
            val elapsed = System.currentTimeMillis() - splashScreenView.iconAnimationStartMillis
            val waitTime = 1000L // Ensure at least 1 second for the wink to complete
            val remaining = (waitTime - elapsed).coerceIn(0L, waitTime)

            splashView.postDelayed({
                val animators = mutableListOf<Animator>()
                
                // Always fade out the main splash view
                animators.add(ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f))
                
                // Only apply scale animation to icon if it's available
                iconView?.let {
                    animators.add(ObjectAnimator.ofFloat(it, View.SCALE_X, 1f, 0.9f))
                    animators.add(ObjectAnimator.ofFloat(it, View.SCALE_Y, 1f, 0.9f))
                }
                
                AnimatorSet().apply {
                    playTogether(animators)
                    duration = 400L
                    interpolator = AccelerateDecelerateInterpolator()
                    doOnEnd { splashScreenView.remove() }
                    start()
                }
            }, remaining)
        }

        // Keep splash screen on until app is ready AND we've shown the animation for at least 1.5s
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - startTime < 1500
        }

        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = systemDark)
            
            HighTechAiTheme(darkTheme = isDarkMode) {
                AiAssistantApp()
            }
        }
    }
}
