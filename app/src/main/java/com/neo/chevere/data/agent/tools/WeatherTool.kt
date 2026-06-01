package com.neo.chevere.data.agent.tools

import com.neo.chevere.core.NumberUtils
import com.neo.chevere.core.PiiUtils
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.location.LocationProvider
import com.neo.chevere.domain.WeatherUnitSystem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WeatherTool"

/**
 * A tool that allows the agent to get real-time weather information for any location.
 * Uses the Open-Meteo API (free, no API key required).
 */
@Singleton
class WeatherTool @Inject constructor(
    private val httpClient: HttpClient,
    private val preferenceManager: PreferenceManager,
    private val locationProvider: LocationProvider,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : AgentTool {
    override val name: String = "get_weather"
    override val description: String =
        "Fetches the current outdoor weather and forecast. Can fetch for a specified city name, or for the user's current location. CRITICAL: Do NOT call this tool for indoor temperature, room temperature, or device ambient conditions; use 'read_sensors' instead."
    override val inputSchema: String =
        "location: [Optional] The name of the city or place to get weather for (e.g. 'Paris'). If the user is asking about the weather at their current location, or does not specify a location, omit this parameter or pass 'current'."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val rawLocation = args["location"]?.trim()
        val isCurrent = rawLocation.isNullOrBlank() || isCurrentLocationRequest(rawLocation)

        return try {
            val units = WeatherUnits.from(preferenceManager.weatherUnitPreference.first())
            val lat: Double
            val lon: Double
            val cityName: String

            if (isCurrent) {
                if (!locationProvider.isPermissionGranted()) {
                    return ToolResult.Error("LOCATION_PERMISSION_REQUIRED")
                }
                val location = locationProvider.getCurrentLocation()
                    ?: return ToolResult.Error("Could not retrieve current location. Please ensure location services are enabled.")
                lat = location.latitude
                lon = location.longitude
                cityName = try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    addresses?.firstOrNull()?.locality ?: addresses?.firstOrNull()?.subAdminArea ?: "Current Location"
                } catch (e: Exception) {
                    "Current Location"
                }
            } else {
                // Scrub PII from location to ensure privacy before sending to external geocoding API
                val location = PiiUtils.scrub(rawLocation)

                // 1. Geocoding: Convert location name to coordinates
                val geocodeResponse: GeocodeResponse =
                    httpClient.get("https://geocoding-api.open-meteo.com/v1/search") {
                        parameter("name", location)
                        parameter("count", "1")
                        parameter("language", "en")
                        parameter("format", "json")
                    }.body()

                val city = geocodeResponse.results?.firstOrNull()
                    ?: return ToolResult.Error("Could not find location: $location")

                lat = city.latitude
                lon = city.longitude
                cityName = city.name
            }

            // 2. Fetch Weather using coordinates
            val weatherResponse: WeatherResponse =
                httpClient.get("https://api.open-meteo.com/v1/forecast") {
                    parameter("latitude", lat)
                    parameter("longitude", lon)
                    parameter("current_weather", "true")
                    parameter("timezone", "auto")
                    parameter("daily", "weathercode,temperature_2m_max,temperature_2m_min")
                    parameter("temperature_unit", units.temperatureApiValue)
                    parameter("wind_speed_unit", units.windSpeedApiValue)
                }.body()

            val current = weatherResponse.current_weather
            val result = buildString {
                append("Current weather in $cityName:\n")
                append("- Temperature: ${formatWeatherValue(current.temperature, units.temperatureLabel)}\n")
                append("- Condition: ${getWeatherCondition(current.weathercode)}\n")
                append("- Wind Speed: ${formatWeatherValue(current.windspeed, units.windSpeedLabel)}\n")

                weatherResponse.daily?.let { daily ->
                    append("\nForecast for today:\n")
                    append("- High: ${formatWeatherValue(daily.temperature_2m_max.firstOrNull(), units.temperatureLabel)}\n")
                    append("- Low: ${formatWeatherValue(daily.temperature_2m_min.firstOrNull(), units.temperatureLabel)}\n")
                }
            }

            ToolResult.Success(result)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get weather for $rawLocation")
            ToolResult.Error("Failed to fetch weather: ${e.message}")
        }
    }

    private fun formatWeatherValue(value: Double?, unitLabel: String): String {
        if (value == null) return "N/A"
        val rounded = Math.round(value).toInt()
        val words = NumberUtils.toWords(rounded)
        val expandedUnit = when (unitLabel) {
            "F" -> "degrees Fahrenheit"
            "C" -> "degrees Celsius"
            "mph" -> "miles per hour"
            "km/h" -> "kilometers per hour"
            else -> unitLabel
        }
        return "$words $expandedUnit"
    }

    private fun isCurrentLocationRequest(location: String): Boolean {
        val normalized = location.lowercase(java.util.Locale.ROOT).trim()
        return normalized.isEmpty() ||
                normalized == "current" ||
                normalized == "current location" ||
                normalized == "my location" ||
                normalized == "here" ||
                normalized == "device" ||
                normalized == "local"
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow fall"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with slight and heavy hail"
            else -> "Unknown"
        }
    }

    @Serializable
    data class GeocodeResponse(val results: List<GeocodeResult>? = null)

    @Serializable
    data class GeocodeResult(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null
    )

    @Serializable
    data class WeatherResponse(
        val current_weather: CurrentWeather,
        val daily: DailyForecast? = null
    )

    @Serializable
    data class CurrentWeather(
        val temperature: Double,
        val windspeed: Double,
        val weathercode: Int
    )

    @Serializable
    data class DailyForecast(
        val temperature_2m_max: List<Double>,
        val temperature_2m_min: List<Double>,
        val weathercode: List<Int>
    )

    private data class WeatherUnits(
        val temperatureApiValue: String,
        val windSpeedApiValue: String,
        val temperatureLabel: String,
        val windSpeedLabel: String
    ) {
        companion object {
            fun from(unitSystem: WeatherUnitSystem): WeatherUnits {
                return when (unitSystem) {
                    WeatherUnitSystem.METRIC -> WeatherUnits(
                        temperatureApiValue = "celsius",
                        windSpeedApiValue = "kmh",
                        temperatureLabel = "C",
                        windSpeedLabel = "km/h"
                    )

                    WeatherUnitSystem.IMPERIAL -> WeatherUnits(
                        temperatureApiValue = "fahrenheit",
                        windSpeedApiValue = "mph",
                        temperatureLabel = "F",
                        windSpeedLabel = "mph"
                    )
                }
            }
        }
    }
}
