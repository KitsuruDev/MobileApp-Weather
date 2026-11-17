package com.example.appweather.data

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val location: LocationData,
    val current: CurrentWeather,
    val forecast: ForecastData
)

data class LocationData(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

data class CurrentWeather(
    // связь temp_c с переменной temperature (по умолчанию названия переменной и поля должно совпадать)
    @SerializedName("temp_c") val temperature: Double,
    @SerializedName("feelslike_c") val feelsLike: Double,
    val humidity: Int,
    @SerializedName("wind_kph") val windSpeedKmh: Double,
    @SerializedName("pressure_mb") val pressure: Double,
    @SerializedName("vis_km") val visibility: Double,
    @SerializedName("last_updated") val lastUpdated: String,
    val uv: Double,
    @SerializedName("air_quality") val airQuality: AirQuality?,
    val condition: WeatherCondition
)

data class AirQuality(
    @SerializedName("pm2_5") val pm25: Double,
    @SerializedName("us-epa-index") val epaIndex: Int // 1 - хорошо, 6 - опасно
)

data class ForecastData(
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    val date: String,
    val day: DayData,
    val astro: AstroData,
    val hour: List<HourData>
)

data class DayData(
    @SerializedName("maxtemp_c") val tempMax: Double,
    @SerializedName("mintemp_c") val tempMin: Double,
    val condition: WeatherCondition,
    @SerializedName("daily_chance_of_rain") val chanceOfRain: Int,
    @SerializedName("avghumidity") val avgHumidity: Int?,
    @SerializedName("maxwind_kph") val maxWindKmh: Double?
)

data class AstroData(
    val sunrise: String,
    val sunset: String,
    @SerializedName("moon_phase") val moonPhase: String,
    @SerializedName("moon_illumination") val moonIllumination: Int
)

data class HourData(
    val time: String,
    @SerializedName("temp_c") val temperature: Double,
    @SerializedName("pressure_mb") val pressure: Double,
    val condition: WeatherCondition
)

data class WeatherCondition(
    val text: String,
    val code: Int
)

data class SearchLocation(
    val id: Int,
    val name: String,
    val region: String,
    val country: String
)

data class FavoriteCity(
    val name: String,
    val temperature: Double,
    val weatherCode: Int
)