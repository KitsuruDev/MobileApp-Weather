package com.example.appweather.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                // Язык
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = language == "ru", onClick = { language = "ru" })
                    Text(stringResource(R.string.russian))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = language == "en", onClick = { language = "en" })
                    Text(stringResource(R.string.english))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Температура
                Text(stringResource(R.string.temperature), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tempUnit == "C", onClick = { tempUnit = "C" })
                    Text(stringResource(R.string.celsius))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = tempUnit == "F", onClick = { tempUnit = "F" })
                    Text(stringResource(R.string.fahrenheit))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Скорость ветра
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

                // Давление
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

                // Формат времени
                Text(stringResource(R.string.time_format), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = timeFormat == "24h", onClick = { timeFormat = "24h" })
                    Text(stringResource(R.string.time_24h))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = timeFormat == "12h", onClick = { timeFormat = "12h" })
                    Text(stringResource(R.string.time_12h))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ежедневный прогноз в уведомлении
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.daily_notification),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
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
                                        notificationTime = "%02d:%02d".format(pickedH, pickedM)
                                    },
                                    h, m, true
                                ).show()
                            }
                        ) {
                            Text(notificationTime)
                        }
                    }
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
                        notificationTime = notificationTime
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