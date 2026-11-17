package com.example.appweather.network

import com.example.appweather.data.WeatherResponse
import com.example.appweather.data.SearchLocation
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    // suspend - асинхронная функция
    @GET("v1/forecast.json")
    suspend fun getWeather(
        // @Query - добавление параметра в URL-запрос
        @Query("key") apiKey: String = "74e6ef453e4c4cada0e114026261211",
        @Query("q") query: String = "Москва",
        @Query("days") days: Int = 7,
        @Query("lang") lang: String = "ru",
        @Query("aqi") aqi: String = "yes"
    ): WeatherResponse

    @GET("v1/search.json")
    suspend fun searchCities(
        @Query("key") apiKey: String = "74e6ef453e4c4cada0e114026261211",
        @Query("q") query: String
    ): List<SearchLocation>

    @GET("v1/history.json")
    suspend fun getHistory(
        @Query("key") apiKey: String = "74e6ef453e4c4cada0e114026261211",
        @Query("q") query: String,
        @Query("dt") date: String, // формат "yyyy-MM-dd"
        @Query("lang") lang: String = "ru",
        @Query("aqi") aqi: String = "yes"
    ): WeatherResponse
}