package com.neo.chevere.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.robolectric.Robolectric
import com.github.takahirom.roborazzi.captureRoboImage
import android.net.Uri
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.ui.chat.AgeVerificationRequest
import com.neo.chevere.data.datasource.local.TaskEntity
import com.neo.chevere.data.datasource.local.TaskStatus
import com.neo.chevere.domain.*
import com.neo.chevere.ui.chat.ChatContent
import com.neo.chevere.ui.chat.ChatState
import com.neo.chevere.ui.designsystem.AtmosphericTheme
import com.neo.chevere.ui.designsystem.HighTechAiTheme
import com.neo.chevere.ui.marketplace.MarketplaceState
import com.neo.chevere.ui.marketplace.ModelMarketplaceContent
import com.neo.chevere.ui.marketplace.details.ModelDetailsContent
import com.neo.chevere.ui.marketplace.details.ModelDetailsState
import com.neo.chevere.ui.radar.RadarUiState
import com.neo.chevere.ui.radar.SensorMode
import com.neo.chevere.ui.radar.SensorsRadarContent
import com.neo.chevere.ui.settings.*
import com.neo.chevere.ui.tasks.TasksContent
import com.neo.chevere.ui.tasks.TasksState
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureChatScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ChatContent(
                    state = ChatState(
                        messages = listOf(
                            ChatMessage(text = "Hello! Ask me about sensors, system thermals, or compass heading.", isUser = false),
                            ChatMessage(text = "How loud is it in here?", isUser = true),
                            ChatMessage(text = "The room is around forty decibels SPL — Quiet, library level.", isUser = false)
                        )
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onModelsClick = {},
                    onSettingsClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/chat_screen.png")
    }

    @Test
    fun captureChatScreen_withImageAttachment() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ChatContent(
                    state = ChatState(
                        messages = listOf(
                            ChatMessage(text = "Hello! Ask me about sensors, system thermals, or compass heading.", isUser = false),
                            ChatMessage(text = "Can you check this image for me?", isUser = true)
                        ),
                        selectedImageUri = Uri.parse("content://dummy/image.jpg")
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onModelsClick = {},
                    onSettingsClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/chat_screen_with_image.png")
    }

    @Test
    fun captureChatScreen_withAgeVerification() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ChatContent(
                    state = ChatState(
                        messages = listOf(
                            ChatMessage(text = "Please generate an image of a futuristic laboratory.", isUser = true)
                        ),
                        ageVerificationRequest = AgeVerificationRequest("Generate a futuristic laboratory", null)
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onModelsClick = {},
                    onSettingsClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/chat_screen_with_age_verification.png")
    }

    @Test
    fun captureChatScreen_withActionConfirmation() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ChatContent(
                    state = ChatState(
                        messages = listOf(
                            ChatMessage(text = "Draft an email to Bob saying we are ready to build.", isUser = true)
                        ),
                        agentState = AgentState.WaitingForConfirmation(
                            toolName = "draft_email",
                            message = "Chevere AI wants to draft an email to bob@example.com."
                        )
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onModelsClick = {},
                    onSettingsClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/chat_screen_with_action_confirmation.png")
    }

    @Test
    fun captureChatScreen_withWebSearchToolRunning() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ChatContent(
                    state = ChatState(
                        messages = listOf(
                            ChatMessage(text = "What is the current temperature in Seattle?", isUser = true)
                        ),
                        agentState = AgentState.ExecutingTool(
                            toolName = "get_weather"
                        )
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onModelsClick = {},
                    onSettingsClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/chat_screen_with_tool_running.png")
    }

    @Test
    fun captureSettingsScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                SettingsContent(
                    state = SettingsState(
                        isDarkMode = true,
                        atmosphericTheme = AtmosphericTheme.CLASSIC_CYAN,
                        weatherUnitSystem = WeatherUnitSystem.METRIC,
                        isBiometricLockEnabled = false,
                        downloadOnWifiOnly = true
                    ),
                    onIntent = {},
                    onBackClick = {},
                    onBenchmarkClick = {},
                    onRadarClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/settings_screen.png")
    }

    @Test
    fun captureMarketplaceScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ModelMarketplaceContent(
                    state = MarketplaceState(
                        remoteModels = listOf(
                            ModelEntry(
                                name = "Gemma 2B",
                                description = "Lightweight local language model optimized for mobile devices.",
                                sizeBytes = 1600000000L,
                                runtimeType = "LiteRT",
                                provider = "Google",
                                fileName = "gemma-2b.litertlm"
                            ),
                            ModelEntry(
                                name = "Stable Diffusion Turbo",
                                description = "Ultra-fast local text-to-image generator.",
                                sizeBytes = 2100000000L,
                                runtimeType = "ONNX",
                                provider = "StabilityAI",
                                fileName = "sd-turbo.zip"
                            )
                        ),
                        localModels = listOf(
                            InstalledModel(
                                id = "gemma-2b.litertlm",
                                displayName = "Gemma 2B",
                                fileName = "gemma-2b.litertlm",
                                filePath = "/data/user/0/dev.neo.chevereai/files/gemma-2b.litertlm",
                                source = ModelSource.LOCAL,
                                format = ModelFormat.LITERTLM,
                                runtime = ModelRuntime.LITERT,
                                taskType = ModelTaskType.CHAT,
                                capabilities = setOf(ModelCapability.TEXT),
                                installStatus = InstallStatus.INSTALLED,
                                sizeBytes = 1600000000L
                            )
                        ),
                        activeModelId = "gemma-2b.litertlm"
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onBack = {},
                    onModelClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/marketplace_screen.png")
    }

    @Test
    fun captureModelDetailsScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                ModelDetailsContent(
                    state = ModelDetailsState(
                        modelId = "gemma-2b.litertlm",
                        modelEntry = ModelEntry(
                            name = "Gemma 2B",
                            description = "Lightweight local language model optimized for mobile devices.",
                            sizeBytes = 1600000000L,
                            runtimeType = "LiteRT",
                            provider = "Google",
                            fileName = "gemma-2b.litertlm"
                        ),
                        isLoading = false
                    ),
                    effects = emptyFlow(),
                    onIntent = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/model_details_screen.png")
    }

    @Test
    fun captureTasksScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                TasksContent(
                    state = TasksState(
                        tasks = listOf(
                            TaskEntity(id = 1, title = "Integrate camera permission check", description = "Use rememberLauncherForActivityResult to prompt camera access flow.", status = TaskStatus.COMPLETED),
                            TaskEntity(id = 2, title = "Implement ambient sound sensor retry", description = "Automatically retry query when mic permission is granted.", status = TaskStatus.PENDING)
                        )
                    ),
                    effects = emptyFlow(),
                    onIntent = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/tasks_screen.png")
    }

    @Test
    fun captureBenchmarkScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                BenchmarkContent(
                    state = BenchmarkState(
                        isRunning = false,
                        modelName = "gemma-2b.litertlm",
                        result = BenchmarkMetrics(
                            loadTimeMs = 120L,
                            ttftMs = 380L,
                            inputTokenCount = 10,
                            outputTokenCount = 128,
                            totalTimeMs = 3200L,
                            systemRamText = "12.4 GB Available",
                            accelText = "GPU (NNAPI) Acceleration"
                        )
                    ),
                    onIntent = {},
                    onBackClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/benchmark_screen.png")
    }

    @Test
    fun captureSensorsRadarScreen() {
        composeTestRule.setContent {
            HighTechAiTheme(darkTheme = true) {
                SensorsRadarContent(
                    uiState = RadarUiState(
                        mode = SensorMode.ALL,
                        magneticMagnitude = 45.2f,
                        magneticCalibrated = 12.5f,
                        baseline = 32.7f,
                        lightLevel = 150f,
                        proximityDistance = 8f,
                        proximityNear = false,
                        pitch = 1.2f,
                        roll = -0.5f,
                        audioEnabled = false,
                        vibrationEnabled = false,
                        statusLabel = "SCANNING FOR STUDS / METALS..."
                    ),
                    onMicPermissionGranted = {},
                    calibrate = {},
                    resetCalibration = {},
                    toggleAudio = {},
                    toggleVibration = {},
                    onBackClick = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("screenshots/sensors_radar_screen.png")
    }
}
