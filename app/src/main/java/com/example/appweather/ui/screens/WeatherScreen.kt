package com.example.appweather.ui.screens

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.appweather.R
import com.example.appweather.data.WeatherUtils
import com.example.appweather.viewmodel.WeatherUiState
import com.example.appweather.viewmodel.WeatherViewModel
import com.example.appweather.ui.components.SearchDialog
import com.example.appweather.ui.components.SettingsDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val context = LocalContext.current
    // используется, чтобы пересоздать Activity при смене языка
    val activity = context as? AppCompatActivity

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentCity by viewModel.currentCity.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val favoriteCities by viewModel.favoriteCities.collectAsState()
    val favoriteLimitReached by viewModel.favoriteLimitReached.collectAsState()
    val isGpsLoading by viewModel.isGpsLoading.collectAsState()
    val historyData by viewModel.historyData.collectAsState()
    val historyRequested by viewModel.historyRequested.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    // запрос GPS во время работы приложения
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Пересоздание Activity при смене языка.
    // ViewModel при recreate() переживает пересоздание, поэтому данные не потеряются,
    // а attachBaseContext в MainActivity применит новую локаль ко всем строкам.
    LaunchedEffect(Unit) {
        viewModel.languageChanged.collect {
            activity?.recreate()
        }
    }

    // при первом запуске экрана проверяется, есть ли разрешение на GPS
    LaunchedEffect(Unit) {
        if (locationPermissionState.status.isGranted) {
            val hasPermission = locationPermissionState.status.isGranted
            viewModel.initializeApp(hasPermission, context)
        }
    }

    // toast при переполнении количества избранных городов
    LaunchedEffect(favoriteLimitReached) {
        if (favoriteLimitReached) {
            Toast.makeText(
                context,
                "Нельзя добавить более 10 городов в избранное",
                Toast.LENGTH_SHORT
            ).show()
            viewModel.resetFavoriteLimitFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // GPS
                    IconButton(
                        onClick = {
                            if (locationPermissionState.status.isGranted) {
                                viewModel.fetchLocationAndWeather(context)
                            } else {
                                locationPermissionState.launchPermissionRequest()
                            }
                        },
                        enabled = !isGpsLoading
                    ) {
                        if (isGpsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = stringResource(R.string.my_location)
                            )
                        }
                    }

                    // поиск
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }

                    // детальная информация
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Подробная информация")
                    }

                    // настройки
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is WeatherUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is WeatherUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.error_loading),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.retryLastLoad(context) },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is WeatherUiState.Success -> {
                val weather = state.data
                val current = weather.current
                val today = weather.forecast.forecastday.firstOrNull()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (favoriteCities.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            favoriteCities.forEach { favCity ->
                                // favCity не должно попадать в историю поиска — оставляем addToHistory = false
                                AssistChip(
                                    onClick = { viewModel.loadWeatherByCity(favCity.name) },
                                    label = {
                                        Text(
                                            "${favCity.name} ${WeatherUtils.formatTemp(
                                                favCity.temperature, settings.tempUnit
                                            )}"
                                        )
                                    },
                                    leadingIcon = {
                                        WeatherUtils.WeatherIcon(
                                            code = favCity.weatherCode,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // текущий город
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(text = currentCity, style = MaterialTheme.typography.titleLarge)

                        IconButton(onClick = { viewModel.toggleFavorite(currentCity) }) {
                            val isFavorite = favoriteCities.any { it.name == currentCity }
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "В избранное",
                                tint = if (isFavorite) Color(0xFFFFB300) else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // детали
                    Text(
                        text = WeatherUtils.formatTemp(current.temperature, settings.tempUnit),
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(
                            R.string.feels_like,
                            WeatherUtils.formatTemp(current.feelsLike, settings.tempUnit)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherUtils.WeatherIcon(code = current.condition.code, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = current.condition.text,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // влажность + ветер
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailItem(
                            stringResource(R.string.humidity),
                            "${current.humidity} %",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            stringResource(R.string.wind),
                            WeatherUtils.formatWind(context, current.windSpeedKmh, settings.windUnit),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailItem(
                            stringResource(R.string.pressure),
                            WeatherUtils.formatPressure(context, current.pressure, settings.pressureUnit),
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            stringResource(R.string.visibility),
                            WeatherUtils.formatVisibility(context, current.visibility),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailItem(
                            stringResource(R.string.sunrise),
                            today?.astro?.sunrise?.let {
                                WeatherUtils.formatSunTime(it, settings.timeFormat)
                            } ?: "--:--",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            stringResource(R.string.sunset),
                            today?.astro?.sunset?.let {
                                WeatherUtils.formatSunTime(it, settings.timeFormat)
                            } ?: "--:--",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // --- НОВОЕ: min/max температура + вероятность осадков ---
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailItem(
                            stringResource(R.string.temp_min),
                            today?.day?.tempMin?.let {
                                WeatherUtils.formatTemp(it, settings.tempUnit)
                            } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            stringResource(R.string.temp_max),
                            today?.day?.tempMax?.let {
                                WeatherUtils.formatTemp(it, settings.tempUnit)
                            } ?: "--",
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            stringResource(R.string.chance_of_rain),
                            "${today?.day?.chanceOfRain ?: 0} %",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- БЛОК ПОЧАСОВОГО ПРОГНОЗА (24 часа + горизонтальный скролл) ---
                    Text(
                        text = stringResource(R.string.hourly_forecast),
                        modifier = Modifier.align(Alignment.Start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Надёжный расчёт 24 часов: сравниваем только "yyyy-MM-dd HH"
                    val hoursToShow = remember(weather.current.lastUpdated, weather.forecast.forecastday) {
                        val allHours = weather.forecast.forecastday.flatMap { it.hour }
                        val currentHour = weather.current.lastUpdated.take(13) // "2026-11-18 14"
                        allHours.filter { it.time.take(13) >= currentHour }.take(24)
                    }

                    // LazyRow по умолчанию поддерживает горизонтальную прокрутку
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hoursToShow) { hourData ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val timeString = hourData.time.substringAfter(" ")
                                Text(
                                    text = WeatherUtils.formatTime(timeString, settings.timeFormat),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                WeatherUtils.WeatherIcon(
                                    code = hourData.condition.code,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = WeatherUtils.formatTemp(hourData.temperature, settings.tempUnit),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // прогноз на 7 дней
                    Text(
                        text = stringResource(R.string.daily_forecast),
                        modifier = Modifier.align(Alignment.Start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // шапка таблицы
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.forecast_date),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            text = stringResource(R.string.forecast_weather),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.forecast_temp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                    }

                    HorizontalDivider(thickness = 2.dp)

                    // строки таблицы
                    Column(modifier = Modifier.fillMaxWidth()) {
                        weather.forecast.forecastday.forEach { day ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateString = day.date.substring(5)

                                Text(
                                    text = dateString,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(0.8f)
                                )

                                Row(
                                    modifier = Modifier.weight(2f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WeatherUtils.WeatherIcon(
                                        code = day.day.condition.code,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = day.day.condition.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "${WeatherUtils.formatTemp(day.day.tempMin, settings.tempUnit)} " +
                                            "/ ${WeatherUtils.formatTemp(day.day.tempMax, settings.tempUnit)}",
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showSearchDialog) {
        SearchDialog(
            searchResults = searchResults,
            searchHistory = searchHistory,
            onSearch = { query -> viewModel.searchCities(query) },
            // ВАЖНО: addToHistory = true — в историю попадают только города из поиска
            onCitySelected = { cityName ->
                viewModel.loadWeatherByCity(cityName, addToHistory = true)
            },
            onClearHistory = { viewModel.clearSearchHistory() },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            onSettingsChanged = { newSettings ->
                viewModel.updateSettings(newSettings)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showDetailsSheet && uiState is WeatherUiState.Success) {
        val weather = (uiState as WeatherUiState.Success).data
        val current = weather.current
        val today = weather.forecast.forecastday.firstOrNull()
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Детали погоды", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailItem(
                        "УФ-индекс",
                        "${current.uv} (${WeatherUtils.getUvDescription(current.uv)})",
                        modifier = Modifier.weight(1f)
                    )
                    DetailItem(
                        "AQI",
                        "${current.airQuality?.pm25 ?: 0.0} (${WeatherUtils.getAqiDescription(current.airQuality?.epaIndex ?: 1)})",
                        modifier = Modifier.weight(1f)
                    )
                    DetailItem(
                        "Фаза луны",
                        "${WeatherUtils.getMoonPhaseString(context, today?.astro?.moonPhase ?: "")} (${today?.astro?.moonIllumination ?: 0}%)",
                        modifier = Modifier.weight(1f)
                    )
                }

                // КАРТА ОСАДКОВ (Яндекс Погода)
                Button(
                    onClick = {
                        showDetailsSheet = false
                        val lat = weather.location.lat
                        val lon = weather.location.lon
                        val url = "https://yandex.ru/pogoda/maps/nowcast?ll=${lon}_${lat}&z=10"
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не найдено приложение для открытия карты", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Открыть карту осадков")
                }

                // ИСТОРИЯ ПОГОДЫ
                Button(
                    onClick = { viewModel.fetchHistory(currentCity) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isHistoryLoading
                ) {
                    if (isHistoryLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isHistoryLoading) "Загрузка..." else "Узнать погоду за вчера")
                }

                if (historyRequested) {
                    if (historyData != null) {
                        val histDay = historyData?.forecast?.forecastday?.firstOrNull()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Погода вчера", fontWeight = FontWeight.Bold)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WeatherUtils.WeatherIcon(
                                        code = histDay?.day?.condition?.code ?: 1000,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${WeatherUtils.formatTemp(histDay?.day?.tempMax ?: 0.0, settings.tempUnit)} / ${WeatherUtils.formatTemp(histDay?.day?.tempMin ?: 0.0, settings.tempUnit)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Text("Погода: ${histDay?.day?.condition?.text ?: "--"}")
                                Text("Влажность: ${histDay?.day?.avgHumidity ?: "--"} %")
                                Text("Ветер: ${histDay?.day?.maxWindKmh?.let { WeatherUtils.formatWind(context, it, settings.windUnit) } ?: "--"}")

                                val histPressure = historyData?.forecast?.forecastday?.firstOrNull()?.hour?.firstOrNull()?.pressure
                                Text("Давление: ${histPressure?.let { WeatherUtils.formatPressure(context, it, settings.pressureUnit) } ?: "--"}")
                            }
                        }
                    } else if (!isHistoryLoading) {
                        Text("Данные об истории недоступны", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DetailItem(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}