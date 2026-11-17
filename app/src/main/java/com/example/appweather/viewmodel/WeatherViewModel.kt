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
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

        // если есть разрешение GPS, то выведется город по GPS, иначе первый из избранных/по умолчанию
        if (hasGpsPermission) {
            fetchLocationAndWeather(context)
        } else {
            loadFallbackWeather()
        }
    }

    // вывод города при открытии приложения
    fun loadFallbackWeather() {
        // первый избранный город из памяти
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("favorite_cities", "") ?: ""
        val firstFavorite = saved.split(",").firstOrNull { it.isNotBlank() }

        // он есть избранный - загрузится, иначе Москва по умолчанию
        loadWeatherByCity(firstFavorite ?: "Москва")
    }

    fun updateSettings(newSettings: AppSettings) {
        val languageChanged = _settings.value.language != newSettings.language
        _settings.value = newSettings
        newSettings.save(getApplication())

        if (languageChanged) {
            loadWeatherByCity(currentCity.value)
        }
    }

    fun loadWeatherByCity(cityName: String) {
        lastLoadWasGps = false
        _currentCity.value = cityName

        _historyRequested.value = false
        _historyData.value = null

        // запуск фоновой задачи
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val response = RetrofitClient.api.getWeather(
                    query = cityName,
                    lang = _settings.value.language
                )
                _currentCity.value = response.location.name
                addToSearchHistory(response.location.name)
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
                _currentCity.value = response.location.name // API сам скажет, что это за город
                _uiState.value = WeatherUiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error(
                    getApplication<Application>().getString(R.string.network_error, e.message ?: "")
                )
            }
        }
    }

    // Получение GPS-локации
    @SuppressLint("MissingPermission")
    fun fetchLocationAndWeather(context: Context) {
        // включение круговой загрузки на кнопке GPS
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
                // выключение круговой загрузки на кнопке GPS
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

    // загрузка истории поиска из памяти (теперь в IO-потоке)
    private suspend fun loadSearchHistory() {
        val prefs = getApplication<Application>().getSharedPreferences("weather_settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("search_history", "") ?: ""
        if (saved.isNotEmpty()) {
            _searchHistory.value = saved.split("|") // | как разделитель, т.к. в названиях может быть запятая
        }
    }

    // добавление города в историю поиска
    fun addToSearchHistory(cityName: String) {
        val current = _searchHistory.value.toMutableList()
        // город уже есть в истории - его удаление, чтобы переместить его на первое место
        current.remove(cityName)
        // добавление в начало
        current.add(0, cityName)

        // только 5 последних
        if (current.size > 5) {
            current.removeAt(5)
        }
        _searchHistory.value = current

        // сохранение
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
                    // погода для каждого города
                    val response = RetrofitClient.api.getWeather(query = city, lang = _settings.value.language)
                    loadedCities.add(
                        FavoriteCity(
                            name = response.location.name,
                            temperature = response.current.temperature,
                            weatherCode = response.current.condition.code
                        )
                    )
                } catch (e: Exception) {
                    // не удалось загрузить - пропуск
                }
            }
            _favoriteCities.value = loadedCities
        }
    }

    fun toggleFavorite(cityName: String) {
        val currentFavorites = _favoriteCities.value.toMutableList()
        val existingIndex = currentFavorites.indexOfFirst { it.name == cityName }

        if (existingIndex >= 0) {
            // удаление из избранного
            currentFavorites.removeAt(existingIndex)
            _favoriteCities.value = currentFavorites
            saveFavoritesToPrefs()
        } else {
            // добавление в избранное
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