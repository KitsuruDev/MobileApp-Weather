package com.example.appweather.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appweather.R
import com.example.appweather.data.AppSettings
import com.example.appweather.data.FavoriteCity
import com.example.appweather.data.ForecastDay
import com.example.appweather.data.SearchLocation
import com.example.appweather.data.WeatherResponse
import com.example.appweather.network.RetrofitClient
import com.example.appweather.notifications.NotificationHelper
import com.example.appweather.notifications.NotificationScheduler
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val data: WeatherResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentCity = MutableStateFlow("Moscow")
    val currentCity = _currentCity.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchLocation>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isGpsLoading = MutableStateFlow(false)
    val isGpsLoading = _isGpsLoading.asStateFlow()

    private val _lastLoadWasGps = MutableStateFlow(false)
    val lastLoadWasGps = _lastLoadWasGps.asStateFlow()

    private var isInitialLoadDone = false

    private val _settings = MutableStateFlow(AppSettings.load(application))
    val settings = _settings.asStateFlow()

    private val _favoriteCities = MutableStateFlow<List<FavoriteCity>>(emptyList())
    val favoriteCities = _favoriteCities.asStateFlow()

    private val _favoriteLimitReached = MutableStateFlow(false)
    val favoriteLimitReached = _favoriteLimitReached.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    // история погоды
    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading = _isHistoryLoading.asStateFlow()

    private val _historyData = MutableStateFlow<List<ForecastDay>?>(null)
    val historyData = _historyData.asStateFlow()

    private val _historyRequested = MutableStateFlow(false)
    val historyRequested = _historyRequested.asStateFlow()

    private val _languageChanged = MutableSharedFlow<Unit>()
    val languageChanged = _languageChanged.asSharedFlow()

    private var refreshJob: Job? = null


    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadFavoriteCities()
            loadSearchHistory()
        }
    }


    fun startAutoRefresh() {
        refreshJob?.cancel()
        val interval = _settings.value.refreshIntervalMinutes
        if (interval <= 0) // если вдруг 0 и меньше - обновление не запускается
            return

        refreshJob = viewModelScope.launch {
            while (true) {
                delay((interval * 60_000L).milliseconds)
                if (_uiState.value is WeatherUiState.Success) {
                    loadWeatherByCity(_currentCity.value)
                }
            }
        }
    }

    fun initializeApp(hasGpsPermission: Boolean, context: Context) {
        if (isInitialLoadDone)
            return

        isInitialLoadDone = true

        startAutoRefresh()

        if (hasGpsPermission) {
            fetchLocationAndWeather(context)
        } else {
            loadFallbackWeather()
        }
    }

    // цепочка при отсутствии GPS: последний открытый город -> первый избранный -> Москва
    fun loadFallbackWeather() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)

        val lastOpened = prefs.getString("last_weather_city", null)
        if (!lastOpened.isNullOrBlank()) {
            loadWeatherByCity(lastOpened)
            return
        }

        val saved = prefs.getString("favorite_cities", "") ?: ""
        val firstFavorite = saved.split(",").firstOrNull { it.isNotBlank() }

        loadWeatherByCity(firstFavorite ?: "Москва")
    }

    fun loadWeatherFromNotification(cityName: String) {
        isInitialLoadDone = true
        loadWeatherByCity(cityName)
    }

    fun updateSettings(newSettings: AppSettings) {
        val prev = _settings.value
        val languageChanged = prev.language != newSettings.language

        _settings.value = newSettings
        newSettings.save(getApplication())

        if (prev.refreshIntervalMinutes != newSettings.refreshIntervalMinutes) {
            startAutoRefresh()
        }

        if (prev.notificationsEnabled != newSettings.notificationsEnabled ||
            prev.notificationTime != newSettings.notificationTime
        ) {
            NotificationScheduler.reschedule(
                getApplication(),
                newSettings.notificationsEnabled,
                newSettings.notificationTime
            )
        }

        val alertsShouldRun = newSettings.weatherChangeNotificationsEnabled || newSettings.severeWeatherNotificationsEnabled
        val alertsWereOn = prev.weatherChangeNotificationsEnabled || prev.severeWeatherNotificationsEnabled
        if (alertsShouldRun != alertsWereOn) {
            NotificationScheduler.rescheduleAlerts(getApplication(), alertsShouldRun)
        }

        // фиксация статуса GPS, чтобы он не сбросился при обновлении экрана
        val wasGps = _lastLoadWasGps.value

        if (languageChanged) {
            viewModelScope.launch {
                _languageChanged.emit(Unit) // оповещение, что состояние языка поменялось, все подписанные элементы обновятся
            }
        }

        loadWeatherByCity(currentCity.value)

        _lastLoadWasGps.value = wasGps
    }

    fun loadWeatherByCity(cityName: String, addToHistory: Boolean = false) {
        _lastLoadWasGps.value = false
        _currentCity.value = cityName

        _historyRequested.value = false
        _historyData.value = null

        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            try {
                val response = RetrofitClient.api.getWeather(
                    query = cityName,
                    lang = _settings.value.language
                )

                _currentCity.value = response.location.name

                getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE).edit {
                    putString("current_city", response.location.name)
                    putString("last_weather_city", response.location.name)
                    putFloat("last_weather_temp", response.current.temperature.toFloat())
                    putInt("last_weather_condition", response.current.condition.code)
                }

                if (addToHistory)
                    addToSearchHistory(response.location.name)

                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.network_error, e.message ?: "")
                )
            }
        }
    }

    fun loadWeatherByCoords(lat: Double, lon: Double) {
        _lastLoadWasGps.value = true

        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            try {
                val response = RetrofitClient.api.getWeather(query = "$lat,$lon", lang = _settings.value.language)
                val newCity = response.location.name

                detectMovement(newCity, response.current.temperature, response.current.condition.text)

                _currentCity.value = newCity

                getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE).edit {
                    putString("current_city", newCity)
                    putString("last_weather_city", newCity)
                    putFloat("last_weather_temp", response.current.temperature.toFloat())
                    putInt("last_weather_condition", response.current.condition.code)
                    putString("last_gps_city", newCity)
                    putFloat("last_gps_lat", lat.toFloat())
                    putFloat("last_gps_lon", lon.toFloat())
                }

                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.network_error, e.message ?: "")
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocationAndWeather(context: Context) {
        _isGpsLoading.value = true

        viewModelScope.launch {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                // выбор точности по настройке: высокая (GPS) или экономичная (Wi-Fi/сеть)
                val priority =
                    if (_settings.value.gpsHighAccuracy)
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                    else
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY

                // suspendCancellableCoroutine:
                // 1) приостанавливает текущую корутину
                // 2) передаёт объект continuation, через который вручную возобновляется корутина, когда результат готов
                // 3) возвращает continuation.resume
                val location = suspendCancellableCoroutine { continuation ->
                    // возвращает Task<Location>, который нельзя использовать напрямую в корутине - нужны слушатели
                    fusedLocationClient.getCurrentLocation(priority, null)
                        .addOnSuccessListener { loc -> continuation.resume(loc) } // есть координаты - возврат их
                        .addOnFailureListener { continuation.resume(null) }       // если нет - возврат null
                }

                if (location != null) {
                    loadWeatherByCoords(location.latitude, location.longitude)
                } else {
                    _uiState.value = WeatherUiState.Error(
                        getApplication<Application>().getString(R.string.gps_error)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.gps_exception, e.message ?: "")
                )
            } finally {
                _isGpsLoading.value = false
            }
        }
    }

    private fun detectMovement(newCity: String, temperature: Double, conditionText: String) {
        if (!_settings.value.movementNotificationsEnabled)
            return

        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val lastGpsCity = prefs.getString("last_gps_city", null) ?: return
        if (lastGpsCity == newCity)
            return

        val locale = java.util.Locale(_settings.value.language)
        val config = Configuration(getApplication<Application>().resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = getApplication<Application>().createConfigurationContext(config)

        NotificationHelper.showMovement(
            localizedContext, newCity, temperature, conditionText, _settings.value.tempUnit
        )
    }

    fun searchCities(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _searchResults.value = RetrofitClient.api.searchCities(query = query)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    private fun loadSearchHistory() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("search_history", "") ?: ""
        if (saved.isNotEmpty())
            _searchHistory.value = saved.split("|")
    }

    fun addToSearchHistory(cityName: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(cityName)
        current.add(0, cityName)
        if (current.size > 5)
            current.removeAt(5)
        _searchHistory.value = current

        getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE).edit {
            putString("search_history", current.joinToString("|"))
        }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE).edit {
            remove("search_history")
        }
    }

    fun retryLastLoad(context: Context) {
        if (_lastLoadWasGps.value) {
            fetchLocationAndWeather(context)
        } else {
            loadWeatherByCity(currentCity.value)
        }
    }

    private suspend fun loadFavoriteCities() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("favorite_cities", "") ?: ""
        if (saved.isNotEmpty()) {
            val loadedCities = mutableListOf<FavoriteCity>()
            for (city in saved.split(",")) {
                try {
                    val response = RetrofitClient.api.getWeather(query = city, lang = _settings.value.language)
                    loadedCities.add(
                        FavoriteCity(
                            name = response.location.name,
                            temperature = response.current.temperature,
                            weatherCode = response.current.condition.code
                        )
                    )
                } catch (e: Exception) { }
            }
            _favoriteCities.value = loadedCities
        }
    }

    fun toggleFavorite(cityName: String) {
        val currentFavorites = _favoriteCities.value.toMutableList()
        val idx = currentFavorites.indexOfFirst { it.name == cityName }
        if (idx >= 0) {
            currentFavorites.removeAt(idx)
            _favoriteCities.value = currentFavorites
            saveFavoritesToPrefs()
        } else {
            if (currentFavorites.size >= 10) {
                _favoriteLimitReached.value = true
                return
            }
            viewModelScope.launch {
                try {
                    val response = RetrofitClient.api.getWeather(query = cityName, lang = _settings.value.language)
                    currentFavorites.add(
                        FavoriteCity(
                            name = response.location.name,
                            temperature = response.current.temperature,
                            weatherCode = response.current.condition.code
                        )
                    )
                    _favoriteCities.value = currentFavorites
                    saveFavoritesToPrefs()
                } catch (e: Exception) { }
            }
        }
    }

    fun resetFavoriteLimitFlag() {
        _favoriteLimitReached.value = false
    }

    private fun saveFavoritesToPrefs() {
        getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE).edit {
            putString("favorite_cities", _favoriteCities.value.joinToString(",") { it.name })
        }
    }

    fun fetchHistory(cityName: String) {
        viewModelScope.launch {
            _isHistoryLoading.value = true
            _historyRequested.value = true
            _historyData.value = null

            try {
                val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                val days = mutableListOf<ForecastDay>()

                // 7 дней назад (со вчерашнего по 7-й день включительно)
                for (i in 1..7) {
                    val date = java.time.LocalDate.now().minusDays(i.toLong()).format(formatter)
                    try {
                        val response = RetrofitClient.api.getHistory(
                            query = cityName,
                            date = date,
                            lang = _settings.value.language
                        )
                        response.forecast.forecastday.firstOrNull()?.let { days.add(it) }
                    } catch (e: Exception) { }
                }
                _historyData.value = days
            } catch (e: Exception) {
                _historyData.value = null
            } finally {
                _isHistoryLoading.value = false
            }
        }
    }
}