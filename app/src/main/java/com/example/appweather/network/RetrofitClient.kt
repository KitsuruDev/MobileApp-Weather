package com.example.appweather.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.weatherapi.com/"

    // lazy - код выполнится при обращении к RetrofitClient.api
    val api: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // превращает JSON-текст сервера в Kotlin-классы
            .build()
            .create(WeatherApi::class.java) // позволяет вызывать api.getWeather()
    }
}