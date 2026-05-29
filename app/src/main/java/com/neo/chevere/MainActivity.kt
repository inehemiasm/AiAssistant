package com.neo.chevere

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.ui.LaunchAnimatedApp
import com.neo.chevere.ui.chat.ChatViewModel
import com.neo.chevere.ui.chat.RuntimeState
import com.neo.chevere.ui.designsystem.AtmosphericTheme
import com.neo.chevere.ui.designsystem.HighTechAiTheme
import com.neo.chevere.ui.designsystem.Typography
import com.neo.chevere.ui.navigation.ChevereApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The main activity of the AI Assistant application.
 *
 * Handles biometric app security, splash screen transitions, theme management, and edge-to-edge layout.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var telemetry: AppTelemetry

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = systemDark)
            val activeTheme by preferenceManager.atmosphericThemePreference.collectAsState(initial = AtmosphericTheme.CLASSIC_CYAN)
            val isBiometricEnabled by preferenceManager.biometricLockPreference.collectAsState(initial = false)
            var isUnlocked by remember { mutableStateOf(false) }

            // Trigger biometric prompt on launch if enabled and not unlocked yet
            LaunchedEffect(isBiometricEnabled, isUnlocked) {
                if (isBiometricEnabled && !isUnlocked) {
                    showBiometricPrompt(
                        onSuccess = { isUnlocked = true },
                        onError = {}
                    )
                }
            }

            HighTechAiTheme(darkTheme = isDarkMode, themeStyle = activeTheme) {
                if (isBiometricEnabled && !isUnlocked) {
                    LockedScreen(
                        onAuthenticate = {
                            showBiometricPrompt(
                                onSuccess = { isUnlocked = true },
                                onError = {}
                            )
                        },
                        onExit = { finish() }
                    )
                } else {
                    // Access ChatViewModel here to observe the global initialization state
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    val chatState by chatViewModel.uiState.collectAsState()

                    // Wrap the main app entry with the launch animation
                    // Only cover the app while a model is actively warming up. A fresh install
                    // starts uninitialized and must be able to show the model download screen.
                    LaunchAnimatedApp(
                        isInitializing = chatState.runtimeState is RuntimeState.Initializing,
                        statusMessage = chatState.loadingMessage ?: "INITIALIZING..."
                    ) {
                        ChevereApp(
                            chatViewModel = chatViewModel,
                            telemetry = telemetry,
                            onExitConfirmed = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or taps negative button, invoke onError
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Chevere AI")
            .setSubtitle("Authenticate to access your local workspace")
            .setNegativeButtonText("Exit")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
private fun LockedScreen(
    onAuthenticate: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "SYSTEM SECURED",
                style = Typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Chevere AI is locked for safety & privacy.",
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onAuthenticate,
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("UNLOCK SYSTEM", style = Typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text(
                    "EXIT",
                    style = Typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
