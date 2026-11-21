package com.nextgen.caros.weather

import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class Weather(
    val temperatureC: Double,
    val apparentC: Double?,
    val code: Int,
    val isDay: Boolean,
    val windKmh: Double?,
    val highC: Double?,
    val lowC: Double?,
    val place: LatLon,
    val fetchedAt: Long,
) {
    val description: String get() = describe(code)
    fun temp(unit: SpeedUnit, c: Double = temperatureC) =
        if (unit == SpeedUnit.MPH) "${(c * 9 / 5 + 32).toInt()}°" else "${Math.round(c)}°"

    companion object {
        /** WMO weather interpretation codes as used by Open-Meteo. */
        fun describe(code: Int) = when (code) {
            0 -> "Clear"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm, hail"
            else -> "—"
        }
    }
}

sealed interface WeatherState {
    data object NoLocation : WeatherState
    data object Loading : WeatherState
    data class Ready(val weather: Weather) : WeatherState
    data class Error(val message: String) : WeatherState
}

/** Current conditions from Open-Meteo (free, no API key, CC-BY 4.0). */
class WeatherRepository(
    private val scope: CoroutineScope,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.NoLocation)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    @Serializable private data class Current(
        val temperature_2m: Double, val apparent_temperature: Double? = null,
        val weather_code: Int = 0, val is_day: Int = 1, val wind_speed_10m: Double? = null,
    )
    @Serializable private data class Daily(
        val temperature_2m_max: List<Double?> = emptyList(), val temperature_2m_min: List<Double?> = emptyList(),
    )
    @Serializable private data class Resp(val current: Current, val daily: Daily? = null)

    init {
        scope.launch {
            while (true) {
                val here = location.state.value.latLon
                val last = (_state.value as? WeatherState.Ready)?.weather
                val needs = here != null && (
                    last == null ||
                        System.currentTimeMillis() - last.fetchedAt > 15 * 60_000 ||
                        Geo.distance(last.place, here) > 10_000
                    )
                if (needs) refresh()
                delay(if (here == null) 5_000 else 30_000)
            }
        }
    }

    fun refresh() {
        val here = location.state.value.latLon ?: run { _state.value = WeatherState.NoLocation; return }
        scope.launch {
            if (_state.value !is WeatherState.Ready) _state.value = WeatherState.Loading
            runCatching {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${here.lat}&longitude=${here.lon}" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"
                val r = Http.json.decodeFromString(Resp.serializer(), Http.get(url))
                Weather(
                    temperatureC = r.current.temperature_2m,
                    apparentC = r.current.apparent_temperature,
                    code = r.current.weather_code,
                    isDay = r.current.is_day == 1,
                    windKmh = r.current.wind_speed_10m,
                    highC = r.daily?.temperature_2m_max?.firstOrNull(),
                    lowC = r.daily?.temperature_2m_min?.firstOrNull(),
                    place = here,
                    fetchedAt = System.currentTimeMillis(),
                )
            }.onSuccess { _state.value = WeatherState.Ready(it) }
                .onFailure { e ->
                    if (_state.value !is WeatherState.Ready) _state.value = WeatherState.Error(e.message ?: "Weather unavailable")
                }
        }
    }
}
