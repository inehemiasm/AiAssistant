package com.neo.chevere.data.agent.tools

import android.net.Uri
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ImageAspectRatio
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageGenerationToolTest {

    private lateinit var imageGenerationManager: ImageGenerationManager
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var tool: ImageGenerationTool

    @Before
    fun setup() {
        imageGenerationManager = mock()
        preferenceManager = mock()
        tool = ImageGenerationTool(imageGenerationManager, preferenceManager)
    }

    @Test
    fun execute_whenPromptIsBlank_returnsError() = runTest {
        val args = mapOf("prompt" to "")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Error)
        assertEquals("Missing image prompt.", (result as ToolResult.Error).message)
    }

    @Test
    fun execute_whenModelNotInstalled_returnsError() = runTest {
        whenever(imageGenerationManager.isImageGenerationAvailable()).doReturn(false)

        val args = mapOf("prompt" to "sunset")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Error)
        assertEquals(
            "No compatible image generation model is installed. Download an ONNX Diffusion model from the Marketplace first.",
            (result as ToolResult.Error).message
        )
    }

    @Test
    fun execute_appliesPreferences_whenArgsAreOmitted() = runTest {
        whenever(imageGenerationManager.isImageGenerationAvailable()).doReturn(true)
        whenever(preferenceManager.defaultImageAspectRatioPreference).doReturn(flowOf("LANDSCAPE_16_9"))
        whenever(preferenceManager.defaultImageStepsPreference).doReturn(flowOf(15))
        whenever(preferenceManager.defaultImageGuidanceScalePreference).doReturn(flowOf(8.5f))
        whenever(preferenceManager.defaultImageNegativePromptPreference).doReturn(flowOf("blurry"))

        val mockUri = mock<Uri>()
        whenever(mockUri.toString()).doReturn("content://mctest")
        
        val successResult = ImageGenerationResult.Success(
            imageUri = mockUri,
            prompt = "sunset",
            width = 640,
            height = 360,
            seed = 12345L
        )

        var capturedRequest: ImageGenerationRequest? = null
        whenever(imageGenerationManager.generate(any())).thenAnswer { invocation ->
            capturedRequest = invocation.arguments[0] as ImageGenerationRequest
            successResult
        }

        val args = mapOf("prompt" to "sunset")
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Success)
        val expectedData = "CHEVERE_IMAGE_GENERATION_RESULT:|uri=content://mctest|prompt=sunset|width=640;height=360;seed=12345".replace(';', '|')
        assertEquals(expectedData, (result as ToolResult.Success).data)

        val request = capturedRequest
        assertTrue(request != null)
        assertEquals("sunset", request?.prompt)
        assertEquals("blurry", request?.negativePrompt)
        assertEquals(640, request?.width)
        assertEquals(360, request?.height)
        assertEquals(15, request?.steps)
        assertEquals(8.5f, request?.guidanceScale)
    }

    @Test
    fun execute_usesExplicitArgs_whenProvided() = runTest {
        whenever(imageGenerationManager.isImageGenerationAvailable()).doReturn(true)
        whenever(preferenceManager.defaultImageAspectRatioPreference).doReturn(flowOf("SQUARE_1_1"))
        whenever(preferenceManager.defaultImageStepsPreference).doReturn(flowOf(20))
        whenever(preferenceManager.defaultImageGuidanceScalePreference).doReturn(flowOf(7.5f))
        whenever(preferenceManager.defaultImageNegativePromptPreference).doReturn(flowOf(""))

        val mockUri = mock<Uri>()
        whenever(mockUri.toString()).doReturn("content://mctest")
        
        val successResult = ImageGenerationResult.Success(
            imageUri = mockUri,
            prompt = "sunset",
            width = 360,
            height = 640,
            seed = 999L
        )

        var capturedRequest: ImageGenerationRequest? = null
        whenever(imageGenerationManager.generate(any())).thenAnswer { invocation ->
            capturedRequest = invocation.arguments[0] as ImageGenerationRequest
            successResult
        }

        val args = mapOf(
            "prompt" to "sunset",
            "negativePrompt" to "noisy",
            "width" to "360",
            "height" to "640",
            "steps" to "30",
            "guidanceScale" to "12.0"
        )
        val result = tool.execute(args)

        assertTrue(result is ToolResult.Success)

        val request = capturedRequest
        assertTrue(request != null)
        assertEquals("sunset", request?.prompt)
        assertEquals("noisy", request?.negativePrompt)
        assertEquals(360, request?.width)
        assertEquals(640, request?.height)
        assertEquals(30, request?.steps)
        assertEquals(12.0f, request?.guidanceScale)
    }
}
