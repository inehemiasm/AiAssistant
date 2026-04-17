package com.neo.chevere.data.agent.tools

import android.util.Log
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tool that allows the agent to get real-time weather information for any location.
 * Uses the Open-Meteo API (free, no API key required).
 */
@Singleton
class WeatherTool @Inject constructor(
    private val httpClient: HttpClient
) : AgentTool {
    override val name: String = "get_weather"
    override val description: String = "Fetches the current weather and forecast for a given location (city, region, or coordinates)."
    override val inputSchema: String = "location: The name of the city or place to get weather for."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val location = args["location"]?.trim() ?: return ToolResult.Error("Missing 'location' argument")

        return try {
            // 1. Geocoding: Convert location name to coordinates
            val geocodeResponse: GeocodeResponse = httpClient.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", location)
                parameter("count", "1")
                parameter("language", "en")
                parameter("format", "json")
            }.body()

            val city = geocodeResponse.results?.firstOrNull() 
                ?: return ToolResult.Error("Could not find location: $location")

            // 2. Fetch Weather using coordinates
            val weatherResponse: WeatherResponse = httpClient.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", city.latitude)
                parameter("longitude", city.longitude)
                parameter("current_weather", "true")
                parameter("timezone", "auto")
                parameter("daily", "weathercode,temperature_2m_max,temperature_2m_min")
            }.body()

            val current = weatherResponse.current_weather
            val result = buildString {
                append("Current weather in ${city.name}, ${city.country ?: ""}:\n")
                append("- Temperature: ${current.temperature}°C\n")
                append("- Condition: ${getWeatherCondition(current.weathercode)}\n")
                append("- Wind Speed: ${current.windspeed} km/h\n")
                
                weatherResponse.daily?.let { daily ->
                    append("\nForecast for today:\n")
                    append("- High: ${daily.temperature_2m_max.firstOrNull()}°C\n")
                    append("- Low: ${daily.temperature_2m_min.firstOrNull()}°C\n")
                }
            }

            ToolResult.Success(result)
        } catch (e: Exception) {
            Log.e("WeatherTool", "Failed to get weather for $location", e)
            ToolResult.Error("Failed to fetch weather: ${e.message}")
        }
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
}
