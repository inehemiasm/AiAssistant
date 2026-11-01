package com.neo.chevere.ui

import android.app.Application
import android.content.res.Configuration
import com.neo.chevere.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.tools.McpTool
import com.neo.chevere.data.agent.tools.DeviceControlTool
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor

/** Verifies real Android resource selection rather than comparing translation tables to themselves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalizationResourcesTest {
    @Test fun supportedLocalesResolveNavigationAndCapabilityCopyWithoutEnglishFallback() {
        val expected = mapOf(
            "en" to "Models Library",
            "es" to "Biblioteca de modelos",
            "fr" to "Bibliothèque de modèles",
            "de" to "Modellbibliothek",
        )
        expected.forEach { (language, label) ->
            val resources = localizedResources(language)
            assertEquals(label, resources.getString(R.string.ui_models_library))
            val capability = resources.getString(R.string.capability_image)
            assertTrue(capability.isNotBlank())
            if (language != "en") {
                assertNotEquals(localizedResources("en").getString(R.string.capability_image), capability)
                assertNotEquals(localizedResources("en").getString(R.string.model_guidance_title), resources.getString(R.string.model_guidance_title))
            }
        }
    }

    @Test fun messageCountsAndNotificationPlaceholdersUseLocaleResources() {
        mapOf("en" to ("1 message" to "2 messages"), "es" to ("1 mensaje" to "2 mensajes"),
            "fr" to ("1 message" to "2 messages"), "de" to ("1 Nachricht" to "2 Nachrichten")).forEach { (language, expected) ->
            val resources = localizedResources(language)
            assertEquals(expected.first, resources.getQuantityString(R.plurals.history_message_count, 1, 1))
            assertEquals(expected.second, resources.getQuantityString(R.plurals.history_message_count, 2, 2))
            assertTrue(resources.getString(R.string.notification_downloading_model, "Spark-X2.6").contains("Spark-X2.6"))
            assertTrue(resources.getString(R.string.notification_download_progress, 25).contains("25%"))
        }
    }

    @Test fun remoteConfirmationIsTranslatedWithoutChangingToolArguments() = runTest {
        val context = localizedContext("de")
        val result = McpTool(context).execute(mapOf(
            "endpoint" to "https://example.com/mcp", "action" to "call",
            "tool" to "test_tool", "arguments" to "{\"name\":\"My task\"}",
        )) as ToolResult.NeedsConfirmation
        assertTrue(result.message.contains("Diese Argumente"))
        assertTrue(result.message.contains("test_tool"))
        assertTrue(result.message.contains("{\"name\":\"My task\"}"))
    }

    @Test fun deviceConfirmationUsesSpanishWithoutExecutingTheAction() = runTest {
        val executor = mock<AndroidAppActionExecutor>()
        val result = DeviceControlTool(executor, localizedContext("es")).execute(
            mapOf("control" to "volume", "action" to "set", "value" to "42")
        ) as ToolResult.NeedsConfirmation
        assertTrue(result.message.contains("volumen multimedia"))
        assertTrue(result.message.contains("42%"))
        verifyNoInteractions(executor)
    }

    private fun localizedContext(language: String) = RuntimeEnvironment.getApplication().createConfigurationContext(
        Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
    )

    private fun localizedResources(language: String) = localizedContext(language).resources
}
