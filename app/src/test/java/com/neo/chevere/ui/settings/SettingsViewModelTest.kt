package com.neo.chevere.ui.settings

import android.app.Application
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ImageAspectRatio
import com.neo.chevere.domain.WeatherUnitSystem
import com.neo.chevere.ui.designsystem.AtmosphericTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {

    private lateinit var application: Application
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        application = mock()
        preferenceManager = mock()

        // Stub the flows required by the init block
        whenever(preferenceManager.themePreference).doReturn(flowOf(true))
        whenever(preferenceManager.atmosphericThemePreference).doReturn(flowOf(AtmosphericTheme.MATRIX_GREEN))
        whenever(preferenceManager.weatherUnitPreference).doReturn(flowOf(WeatherUnitSystem.METRIC))
        whenever(preferenceManager.biometricLockPreference).doReturn(flowOf(false))
        whenever(preferenceManager.defaultImageAspectRatioPreference).doReturn(flowOf("LANDSCAPE_16_9"))
        whenever(preferenceManager.defaultImageStepsPreference).doReturn(flowOf(15))
        whenever(preferenceManager.defaultImageGuidanceScalePreference).doReturn(flowOf(8.5f))
        whenever(preferenceManager.defaultImageNegativePromptPreference).doReturn(flowOf("blurry"))

        viewModel = SettingsViewModel(application, preferenceManager)
    }

    @Test
    fun init_loadsInitialStateFromPreferences() {
        val state = viewModel.currentState
        assertTrue(state.isDarkMode)
        assertEquals(AtmosphericTheme.MATRIX_GREEN, state.atmosphericTheme)
        assertEquals(WeatherUnitSystem.METRIC, state.weatherUnitSystem)
        assertFalse(state.isBiometricLockEnabled)
        assertEquals(ImageAspectRatio.LANDSCAPE_16_9, state.defaultImageAspectRatio)
        assertEquals(15, state.defaultImageSteps)
        assertEquals(8.5f, state.defaultImageGuidanceScale)
        assertEquals("blurry", state.defaultImageNegativePrompt)
    }

    @Test
    fun onIntent_updateDefaultImageAspectRatio_callsPreferenceManager() = runTest {
        viewModel.onIntent(SettingsIntent.UpdateDefaultImageAspectRatio(ImageAspectRatio.PORTRAIT_9_16))
        verify(preferenceManager).updateDefaultImageAspectRatio("PORTRAIT_9_16")
    }

    @Test
    fun onIntent_updateDefaultImageSteps_callsPreferenceManager() = runTest {
        viewModel.onIntent(SettingsIntent.UpdateDefaultImageSteps(25))
        verify(preferenceManager).updateDefaultImageSteps(25)
    }

    @Test
    fun onIntent_updateDefaultImageGuidanceScale_callsPreferenceManager() = runTest {
        viewModel.onIntent(SettingsIntent.UpdateDefaultImageGuidanceScale(12.5f))
        verify(preferenceManager).updateDefaultImageGuidanceScale(12.5f)
    }

    @Test
    fun onIntent_updateDefaultImageNegativePrompt_callsPreferenceManager() = runTest {
        viewModel.onIntent(SettingsIntent.UpdateDefaultImageNegativePrompt("bad quality"))
        verify(preferenceManager).updateDefaultImageNegativePrompt("bad quality")
    }
}
