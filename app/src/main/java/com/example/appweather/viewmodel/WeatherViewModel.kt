package com.example.appweather.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appweather.data.AppSettings
import com.example.appweather.data.SearchLocation
import com.example.appweather.data.WeatherResponse
import com.example.appweather.network.RetrofitClient
import com.example.appweather.notifications.NotificationScheduler
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.example.appweather.R
import com.example.appweather.data.FavoriteCity
import androidx.core.content.edit

// sealed - cписок наследников ограничен теми, которые обозначены ниже
sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val data: WeatherResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    // состояние UI
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // текущий город
    private val _currentCity = MutableStateFlow("Moscow")
    val currentCity = _currentCity.asStateFlow()

    // результат поиска
    private val _searchResults = MutableStateFlow<List<SearchLocation>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    // состояние загрузки GPS (для прокрутки индикатора на кнопке)
    private val _isGpsLoading = MutableStateFlow(false)
    val isGpsLoading = _isGpsLoading.asStateFlow()

    // для правильного отображения последнего открытого города при обновлении состояния интерфейса
    private var lastLoadWasGps = false

    // чтобы не запускать GPS повторно при пересоздании экрана (например, при смене языка)
    private var isInitialLoadDone = false

    // настройки
    private val _settings = MutableStateFlow(AppSettings.load(application))
    val settings = _settings.asStateFlow()

    // избранные города
    private val _favoriteCities = MutableStateFlow<List<FavoriteCity>>(emptyList())
    val favoriteCities = _favoriteCities.asStateFlow()

    // ограничение избранных городов
    private val _favoriteLimitReached = MutableStateFlow(false)
    val favoriteLimitReached = _favoriteLimitReached.asStateFlow()

    // история поиска
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    // истории погоды
    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading = _isHistoryLoading.asStateFlow()

    private val _historyData = MutableStateFlow<WeatherResponse?>(null)
    val historyData = _historyData.asStateFlow()

    private val _historyRequested = MutableStateFlow(false)
    val historyRequested = _historyRequested.asStateFlow()

    // событие "сменился язык" — Activity по нему пересоздастся,
    // чтобы attachBaseContext применил новый Locale ко всем stringResource
    private val _languageChanged = MutableSharedFlow<Unit>()
    val languageChanged = _languageChanged.asSharedFlow()

    init {
        // список избранного и история поиска загружаются в фоновом потоке, чтобы не блокировать UI
        viewModelScope.launch(Dispatchers.IO) {
            loadFavoriteCities()
            loadSearchHistory()
        }
    }

    // вывод города при первом открытии приложения
    fun initializeApp(hasGpsPermission: Boolean, context: Context) {
        // приложение уже загружено (например, эта функция вызвалась при смене языка)
        if (isInitialLoadDone) return
        isInitialLoadDone = true

        if (hasGpsPermission) {
            fetchLocationAndWeather(context)
        } else {
            loadFallbackWeather()
        }
    }

    // загрузка города по клику на уведомление.
    // Устанавливаем isInitialLoadDone = true, чтобы initializeApp не перезаписал город GPS-ом
    fun loadWeatherFromNotification(cityName: String) {
        isInitialLoadDone = true
        loadWeatherByCity(cityName)
    }

    // вывод города при открытии приложения
    fun loadFallbackWeather() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("favorite_cities", "") ?: ""
        val firstFavorite = saved.split(",").firstOrNull { it.isNotBlank() }

        loadWeatherByCity(firstFavorite ?: "Москва")
    }

    fun updateSettings(newSettings: AppSettings) {
        val prev = _settings.value
        val languageChanged = prev.language != newSettings.language
        _settings.value = newSettings
        newSettings.save(getApplication())

        if (prev.notificationsEnabled != newSettings.notificationsEnabled ||
            prev.notificationTime != newSettings.notificationTime
        ) {
            NotificationScheduler.reschedule(
                getApplication(),
                newSettings.notificationsEnabled,
                newSettings.notificationTime
            )
        }

        // расписание алертов включается, если включён хотя бы один из двух типов
        val alertsShouldRun = newSettings.weatherChangeNotificationsEnabled ||
                newSettings.severeWeatherNotificationsEnabled
        val alertsWereOn = prev.weatherChangeNotificationsEnabled ||
                prev.severeWeatherNotificationsEnabled
        if (alertsShouldRun != alertsWereOn) {
            NotificationScheduler.rescheduleAlerts(getApplication(), alertsShouldRun)
        }

        if (languageChanged) {
            viewModelScope.launch {
                _languageChanged.emit(Unit)
            }
        }
        loadWeatherByCity(currentCity.value)
    }

    // addToHistory = true только при выборе города из результатов поиска
    fun loadWeatherByCity(cityName: String, addToHistory: Boolean = false) {
        lastLoadWasGps = false
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

                // сохраняем город для DailyForecastWorker
                getApplication<Application>()
                    .getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
                    .edit {
                        putString("current_city", response.location.name)
                        putFloat("last_weather_temp", response.current.temperature.toFloat())
                        putInt("last_weather_condition", response.current.condition.code)
                    }

                if (addToHistory) {
                    addToSearchHistory(response.location.name)
                }
                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.network_error, e.message ?: "")
                )
            }
        }
    }

    // Загрузка по координатам (для GPS)
    fun loadWeatherByCoords(lat: Double, lon: Double) {
        lastLoadWasGps = true

        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val response = RetrofitClient.api.getWeather(
                    query = "$lat,$lon",
                    lang = _settings.value.language
                )
                val newCity = response.location.name

                // детект перемещения: сравниваем с предыдущим GPS-городом
                detectMovement(newCity, response.current.temperature, response.current.condition.text)

                _currentCity.value = newCity

                val prefs = getApplication<Application>()
                    .getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
                prefs.edit {
                    putString("current_city", newCity)
                    putFloat("last_weather_temp", response.current.temperature.toFloat())
                    putInt("last_weather_condition", response.current.condition.code)
                    putString("last_gps_city", newCity)  // ← отдельный ключ
                }

                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.network_error, e.message ?: "")
                )
            }
        }
    }

    // показывает уведомление о смене города, если GPS-город отличается от предыдущего GPS-города
    private fun detectMovement(newCity: String, temperature: Double, conditionText: String) {
        if (!_settings.value.movementNotificationsEnabled) return

        val prefs = getApplication<Application>()
            .getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val lastGpsCity = prefs.getString("last_gps_city", null) ?: return // первый запуск — не уведомляем

        if (lastGpsCity == newCity) return

        // контекст с нужной локалью для строк уведомления
        val locale = java.util.Locale(_settings.value.language)
        val config = android.content.res.Configuration(
            getApplication<Application>().resources.configuration
        ).apply { setLocale(locale) }
        val localizedContext = getApplication<Application>().createConfigurationContext(config)

        com.example.appweather.notifications.NotificationHelper.showMovement(
            localizedContext,
            newCity,
            temperature,
            conditionText,
            _settings.value.tempUnit
        )
    }

    // Получение GPS-локации
    @SuppressLint("MissingPermission")
    fun fetchLocationAndWeather(context: Context) {
        _isGpsLoading.value = true

        viewModelScope.launch {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location = suspendCancellableCoroutine { continuation ->
                    fusedLocationClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).addOnSuccessListener { loc ->
                        continuation.resume(loc)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
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

    fun searchCities(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val results = RetrofitClient.api.searchCities(query = query)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    // загрузка истории поиска из памяти (в IO-потоке)
    private fun loadSearchHistory() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("search_history", "") ?: ""
        if (saved.isNotEmpty()) {
            _searchHistory.value = saved.split("|")
        }
    }

    // добавление города в историю поиска
    fun addToSearchHistory(cityName: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(cityName)
        current.add(0, cityName)

        if (current.size > 5) {
            current.removeAt(5)
        }
        _searchHistory.value = current

        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        prefs.edit { putString("search_history", current.joinToString("|")) }
    }

    // очистка истории поиска
    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        prefs.edit { remove("search_history") }
    }

    fun retryLastLoad(context: Context) {
        if (lastLoadWasGps) {
            fetchLocationAndWeather(context)
        } else {
            loadWeatherByCity(currentCity.value)
        }
    }

    private suspend fun loadFavoriteCities() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("favorite_cities", "") ?: ""

        if (saved.isNotEmpty()) {
            val cityNames = saved.split(",")
            val loadedCities = mutableListOf<FavoriteCity>()
            for (city in cityNames) {
                try {
                    val response = RetrofitClient.api.getWeather(query = city, lang = _settings.value.language)
                    loadedCities.add(
                        FavoriteCity(
                            name = response.location.name,
                            temperature = response.current.temperature,
                            weatherCode = response.current.condition.code
                        )
                    )
                } catch (e: Exception) {
                }
            }
            _favoriteCities.value = loadedCities
        }
    }

    fun toggleFavorite(cityName: String) {
        val currentFavorites = _favoriteCities.value.toMutableList()
        val existingIndex = currentFavorites.indexOfFirst { it.name == cityName }

        if (existingIndex >= 0) {
            currentFavorites.removeAt(existingIndex)
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
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val cityNames = _favoriteCities.value.joinToString(",") { it.name }

        prefs.edit {
            putString("favorite_cities", cityNames)
        }
    }

    // загрузка погоды за вчерашний день
    fun fetchHistory(cityName: String) {
        viewModelScope.launch {
            _isHistoryLoading.value = true
            _historyRequested.value = true
            _historyData.value = null
            try {
                val yesterday = java.time.LocalDate.now().minusDays(1)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

                val response = RetrofitClient.api.getHistory(
                    query = cityName,
                    date = yesterday,
                    lang = _settings.value.language
                )
                _historyData.value = response
            } catch (e: Exception) {
                _historyData.value = null
            } finally {
                _isHistoryLoading.value = false
            }
        }
    }
}