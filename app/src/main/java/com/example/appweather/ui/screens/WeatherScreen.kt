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

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity // чтобы пересоздать Activity при смене языка

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentCity by viewModel.currentCity.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val favoriteCities by viewModel.favoriteCities.collectAsState()
    val favoriteLimitReached by viewModel.favoriteLimitReached.collectAsState()
    val isGpsLoading by viewModel.isGpsLoading.collectAsState()
    val lastLoadWasGps by viewModel.lastLoadWasGps.collectAsState()
    val historyData by viewModel.historyData.collectAsState()
    val historyRequested by viewModel.historyRequested.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    // запрос GPS во время работы приложения
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val favoriteLimitMessage = stringResource(R.string.favorite_limit_reached)
    val mapAppNotFoundMessage = stringResource(R.string.map_app_not_found)

    // пересоздание Activity при смене языка
    LaunchedEffect(Unit) {
        viewModel.languageChanged.collect {
            activity?.recreate() // viewmodel не теряет данные при пересоздании
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
            Toast.makeText(context, favoriteLimitMessage, Toast.LENGTH_SHORT).show()
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.my_location))
                        }
                    }

                    // поиск
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }

                    // детальная информация
                    IconButton(onClick = { showDetailsSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.details_content_desc))
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
                                AssistChip(
                                    onClick = { viewModel.loadWeatherByCity(favCity.name) },
                                    label = {
                                        Text(
                                            "${favCity.name} " +
                                            "${WeatherUtils.formatTemp(favCity.temperature, settings.tempUnit)}"
                                        )
                                    },
                                    leadingIcon = {
                                        WeatherUtils.WeatherIcon(code = favCity.weatherCode, modifier = Modifier.size(16.dp))
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
                        // иконка, показывающая, что город был получен по GPS
                        if (lastLoadWasGps) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(text = currentCity, style = MaterialTheme.typography.titleLarge)

                        // индикатор загрузки (спиннер)
                        if (isGpsLoading) {
                            Spacer(modifier = Modifier.width(8.dp))

                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }

                        // звёздочка для добавления в избранное
                        IconButton(onClick = { viewModel.toggleFavorite(currentCity) }) {
                            val isFavorite = favoriteCities.any { it.name == currentCity }

                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.favorite_content_desc),
                                tint = if (isFavorite) Color(0xFFFFB300) else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // температура
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

                    // погода
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherUtils.WeatherIcon(code = current.condition.code, modifier = Modifier.size(32.dp))

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(text = current.condition.text, style = MaterialTheme.typography.titleMedium)
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

                    // давление + видимость
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

                    // восход + закат
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

                    // min/max температура + вероятность осадков
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

                    // почасовой прогноз - первые 24 часа
                    Text(
                        text = stringResource(R.string.hourly_forecast),
                        modifier = Modifier.align(Alignment.Start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // remember - пересчитывает значение только если изменилcя lastUpdated/прогноз
                    val hoursToShow = remember(weather.current.lastUpdated, weather.forecast.forecastday) {
                        val allHours = weather.forecast.forecastday.flatMap { it.hour } // почасовые массивы 7 дней в один список
                        val currentHour = weather.current.lastUpdated.take(13) // обрезка времени - на выходе "yyyy-MM-dd HH"
                        allHours.filter { it.time.take(13) > currentHour }.take(24) // первые 24 часа
                    }

                    // LazyRow поддерживает горизонтальную прокрутку
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hoursToShow) { hourData ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val timeString = hourData.time.substringAfter(" ")

                                // время
                                Text(
                                    text = WeatherUtils.formatTime(timeString, settings.timeFormat),
                                    style = MaterialTheme.typography.bodySmall
                                )

                                // погода
                                WeatherUtils.WeatherIcon(
                                    code = hourData.condition.code,
                                    modifier = Modifier.size(24.dp)
                                )

                                // температура
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
                        // дата
                        Text(
                            text = stringResource(R.string.forecast_date),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.weight(0.8f)
                        )

                        // погода
                        Text(
                            text = stringResource(R.string.forecast_weather),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray,
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center
                        )

                        // температура
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
                        // drop(1) - удаление текущего дня из списка
                        // take(7) - взятие следующих 7 дней
                        weather.forecast.forecastday.drop(1).take(7).forEach { day ->
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
                                    WeatherUtils.WeatherIcon(code = day.day.condition.code, modifier = Modifier.size(24.dp))

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

    // поиск
    if (showSearchDialog) {
        SearchDialog(
            searchResults = searchResults,
            searchHistory = searchHistory,
            onSearch = { query -> viewModel.searchCities(query) },
            onCitySelected = { cityName -> viewModel.loadWeatherByCity(cityName, addToHistory = true) },
            onClearHistory = { viewModel.clearSearchHistory() },
            onDismiss = { showSearchDialog = false }
        )
    }

    // настройки
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            onSettingsChanged = { newSettings -> viewModel.updateSettings(newSettings) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // дополнительная информация
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
                Text(
                    stringResource(R.string.details_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // УФ-индекс (интенсивность ультрафиолета)
                    DetailItem(
                        stringResource(R.string.uv_index),
                        "${current.uv} (${WeatherUtils.getUvDescription(context, current.uv)})",
                        modifier = Modifier.weight(1f)
                    )

                    // уровень загрязнения воздуха
                    DetailItem(
                        stringResource(R.string.air_quality),
                        "${current.airQuality?.pm25 ?: 0.0} " +
                                "(${WeatherUtils.getAqiDescription(context, current.airQuality?.epaIndex ?: 1)})",
                        modifier = Modifier.weight(1f)
                    )

                    // фаза Луны
                    DetailItem(
                        stringResource(R.string.moon_phase),
                        "${WeatherUtils.getMoonPhaseString(context, today?.astro?.moonPhase ?: "")} " +
                                "(${today?.astro?.moonIllumination ?: 0}%)",
                        modifier = Modifier.weight(1f)
                    )
                }

                // карта осадков (yandex.ru)
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
                            Toast.makeText(context, mapAppNotFoundMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_precipitation_map))
                }

                // история погоды (7 предыдущих дней)
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

                    Text(if (isHistoryLoading) stringResource(R.string.loading) else stringResource(R.string.yesterday_weather))
                }

                if (historyRequested) {
                    val histDays = historyData
                    if (!histDays.isNullOrEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.history_week_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    histDays.forEach { histDay ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = histDay.date.substring(5),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.width(50.dp)
                                            )

                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    WeatherUtils.WeatherIcon(
                                                        code = histDay.day.condition.code,
                                                        modifier = Modifier.size(24.dp)
                                                    )

                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    Text(
                                                        text = histDay.day.condition.text,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${WeatherUtils.formatTemp(histDay.day.tempMin, settings.tempUnit)} " +
                                                        "/ ${WeatherUtils.formatTemp(histDay.day.tempMax, settings.tempUnit)}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    } else if (!isHistoryLoading) {
                        Text(stringResource(R.string.history_unavailable), color = Color.Gray)
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