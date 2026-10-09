package com.neo.chevere.data.agent.tools

import android.content.Context
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.location.LocationProvider
import com.neo.chevere.data.location.LocationResolution
import com.neo.chevere.domain.WeatherUnitSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.*

class WeatherLocationPermissionTest {
    @Test fun localWeatherWithoutPermissionRequestsAccessBeforeNetworkUse() = runTest {
        val location = mock<LocationProvider>()
        val preferences = mock<PreferenceManager> {
            on { weatherUnitPreference } doReturn flowOf(WeatherUnitSystem.IMPERIAL)
        }
        val http = mock<HttpClient>()
        whenever(location.resolveLocation()).thenReturn(LocationResolution.PermissionRequired)
        val result = WeatherTool(http, preferences, location, mock<Context>()).execute(emptyMap())
        assertEquals(ToolResult.Error("LOCATION_PERMISSION_REQUIRED"), result)
        verifyNoInteractions(http)
    }
}
