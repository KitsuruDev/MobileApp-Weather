package com.example.appweather.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.appweather.R
import com.example.appweather.data.AppSettings
import androidx.compose.foundation.layout.FlowRow

@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var tempUnit by remember { mutableStateOf(currentSettings.tempUnit) }
    var windUnit by remember { mutableStateOf(currentSettings.windUnit) }
    var pressureUnit by remember { mutableStateOf(currentSettings.pressureUnit) }
    var language by remember { mutableStateOf(currentSettings.language) }
    var timeFormat by remember { mutableStateOf(currentSettings.timeFormat) }
    var notificationsEnabled by remember { mutableStateOf(currentSettings.notificationsEnabled) }
    var notificationTime by remember { mutableStateOf(currentSettings.notificationTime) }
    var refreshInterval by remember { mutableIntStateOf(currentSettings.refreshIntervalMinutes) }
    var gpsHighAccuracy by remember { mutableStateOf(currentSettings.gpsHighAccuracy) }
    var weatherChangeEnabled by remember { mutableStateOf(currentSettings.weatherChangeNotificationsEnabled) }
    var severeWeatherEnabled by remember { mutableStateOf(currentSettings.severeWeatherNotificationsEnabled) }
    var movementEnabled by remember { mutableStateOf(currentSettings.movementNotificationsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            // verticalScroll - окно будет прокручиваться при нехватке высоты
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // язык
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = language == "ru", onClick = { language = "ru" })
                    Text(stringResource(R.string.russian))

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(selected = language == "en", onClick = { language = "en" })
                    Text(stringResource(R.string.english))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // температура
                Text(stringResource(R.string.temperature), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tempUnit == "C", onClick = { tempUnit = "C" })
                    Text(stringResource(R.string.celsius))

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(selected = tempUnit == "F", onClick = { tempUnit = "F" })
                    Text(stringResource(R.string.fahrenheit))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // скорость ветра
                // FlowRow - чтобы дочерние элементы не ужимались, а переносились на новую строку
                Text(stringResource(R.string.wind_speed), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = windUnit == "kmh", onClick = { windUnit = "kmh" })
                        Text(stringResource(R.string.label_kmh))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = windUnit == "ms", onClick = { windUnit = "ms" })
                        Text(stringResource(R.string.label_ms))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = windUnit == "mph", onClick = { windUnit = "mph" })
                        Text(stringResource(R.string.label_mph))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // давление
                Text(stringResource(R.string.pressure), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pressureUnit == "hPa", onClick = { pressureUnit = "hPa" })
                        Text(stringResource(R.string.label_hpa))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pressureUnit == "mmHg", onClick = { pressureUnit = "mmHg" })
                        Text(stringResource(R.string.label_mmhg))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pressureUnit == "inHg", onClick = { pressureUnit = "inHg" })
                        Text(stringResource(R.string.label_inhg))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // формат времени
                Text(stringResource(R.string.time_format), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = timeFormat == "24h", onClick = { timeFormat = "24h" })
                    Text(stringResource(R.string.time_24h))

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(selected = timeFormat == "12h", onClick = { timeFormat = "12h" })
                    Text(stringResource(R.string.time_12h))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // частота обновления погоды
                Text(stringResource(R.string.refresh_frequency), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = refreshInterval == 15, onClick = { refreshInterval = 15 })
                        Text(stringResource(R.string.interval_15))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = refreshInterval == 30, onClick = { refreshInterval = 30 })
                        Text(stringResource(R.string.interval_30))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = refreshInterval == 60, onClick = { refreshInterval = 60 })
                        Text(stringResource(R.string.interval_60))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Точность GPS
                Text(stringResource(R.string.gps_accuracy), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = gpsHighAccuracy, onClick = { gpsHighAccuracy = true })
                        Text(stringResource(R.string.accuracy_high))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !gpsHighAccuracy, onClick = { gpsHighAccuracy = false })
                        Text(stringResource(R.string.accuracy_eco))
                    }
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // ежедневный прогноз в уведомлении
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.daily_notification), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }

                if (notificationsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.notification_time))
                        OutlinedButton(
                            onClick = {
                                val parts = notificationTime.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                TimePickerDialog(
                                    context,
                                    { _, pickedH, pickedM ->
                                        // "%02d:%02d" - добавление ведущего нуля, если число меньше 10
                                        notificationTime = "%02d:%02d".format(pickedH, pickedM)
                                    },
                                    h, m,
                                    timeFormat != "12h"
                                ).show()
                            }
                        ) {
                            Text(formatNotificationTime(notificationTime, timeFormat))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // Расширенные уведомления
                Text(stringResource(R.string.smart_notifications), style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.weather_change_toggle))
                    Switch(checked = weatherChangeEnabled, onCheckedChange = { weatherChangeEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.severe_weather_toggle))
                    Switch(checked = severeWeatherEnabled, onCheckedChange = { severeWeatherEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // weight(1f) - текст занимает всё доступное место, switch жмётся вправо
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.movement_toggle))
                        Text(
                            stringResource(R.string.movement_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = movementEnabled,onCheckedChange = { movementEnabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChanged(
                    AppSettings(
                        tempUnit = tempUnit,
                        windUnit = windUnit,
                        pressureUnit = pressureUnit,
                        language = language,
                        timeFormat = timeFormat,
                        notificationsEnabled = notificationsEnabled,
                        notificationTime = notificationTime,
                        weatherChangeNotificationsEnabled = weatherChangeEnabled,
                        severeWeatherNotificationsEnabled = severeWeatherEnabled,
                        movementNotificationsEnabled = movementEnabled,
                        refreshIntervalMinutes = refreshInterval,
                        gpsHighAccuracy = gpsHighAccuracy
                    )
                )
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// форматирование времени на кнопке
private fun formatNotificationTime(time24: String, timeFormat: String): String {
    if (timeFormat != "12h")
        return time24
    return try {
        val parts = time24.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val amPm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        "%02d:%02d %s".format(h12, m, amPm)
    } catch (e: Exception) {
        time24
    }
}