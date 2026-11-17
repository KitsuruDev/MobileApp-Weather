package com.example.appweather.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.appweather.R
import kotlin.math.roundToInt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object WeatherUtils {

    private val format24 = DateTimeFormatter.ofPattern("HH:mm")
    private val format12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    // коды WeatherAPI, по которым выводятся определённые иконки
    @Composable
    fun WeatherIcon(code: Int, modifier: Modifier = Modifier) {
        // tint - перекрас иконки
        val (icon, tint) = when (code) {
            1000 -> Icons.Default.WbSunny to Color(0xFFFFB300)
            1003 -> Icons.Default.Cloud to Color.Gray
            1006, 1009 -> Icons.Default.CloudQueue to Color.DarkGray
            1030, 1135, 1147 -> Icons.Default.Dehaze to Color.Gray
            1063, 1150, 1153, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243, 1246 -> Icons.Default.Umbrella to Color.Blue
            1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> Icons.Default.AcUnit to Color.LightGray
            1087, 1273, 1276, 1279, 1282 -> Icons.Default.FlashOn to Color(0xFFFF9800)
            else -> Icons.Default.Cloud to Color.Gray
        }
        Icon(imageVector = icon, contentDescription = "Погода", tint = tint, modifier = modifier)
    }

    fun formatTemp(celsius: Double, unit: String): String {
        return if (unit == "F") "${((celsius * 9 / 5) + 32).roundToInt()}°" else "${celsius.roundToInt()}°"
    }

    fun formatWind(context: Context, kmh: Double, unit: String): String {
        return when (unit) {
            "ms" -> context.getString(R.string.unit_ms, (kmh / 3.6).roundToInt())
            "mph" -> context.getString(R.string.unit_mph, (kmh * 0.621371).roundToInt()) // мили
            else -> context.getString(R.string.unit_kmh, kmh.roundToInt())
        }
    }

    fun formatPressure(context: Context, hPa: Double, unit: String): String {
        return when (unit) {
            "mmHg" -> context.getString(R.string.unit_mmhg, (hPa * 0.750062).roundToInt())
            "inHg" -> context.getString(R.string.unit_inhg, (hPa * 0.02953)) // дюймы
            else -> context.getString(R.string.unit_hpa, hPa.roundToInt())
        }
    }

    fun formatVisibility(context: Context, km: Double): String {
        return when {
            // видимость на максимуме
            km > 10.0 -> context.getString(R.string.visibility_more, 10)
            // видимость на минимуме
            km < 2.0 -> context.getString(R.string.visibility_less, 2)
            // точное значение между максимумом и минимумом
            else -> context.getString(R.string.unit_km, km.roundToInt())
        }
    }

    // форматирование времени почасового прогноза
    fun formatTime(time24: String, format: String): String {
        if (format != "12h") return time24
        return try {
            LocalTime.parse(time24, format24).format(format12)
        } catch (e: Exception) {
            time24
        }
    }

    // форматирование времени восхода/заката
    fun formatSunTime(timeString: String, format: String): String {
        if (format != "24h") return timeString
        return try {
            LocalTime.parse(timeString, format12).format(format24)
        } catch (e: Exception) {
            timeString
        }
    }

    fun getUvDescription(uv: Double): String {
        return when {
            uv < 3 -> "Низкий"
            uv < 6 -> "Средний"
            uv < 8 -> "Высокий"
            uv < 11 -> "Очень высокий"
            else -> "Экстремальный"
        }
    }

    fun getAqiDescription(index: Int): String {
        return when (index) {
            1 -> "Хорошее"
            2 -> "Умеренное"
            3 -> "Вредное для чувствительных"
            4 -> "Вредное"
            5 -> "Очень вредное"
            else -> "Опасно"
        }
    }

    // Перевод фазы луны
    fun getMoonPhaseString(context: Context, phase: String): String {
        return when (phase) {
            "New Moon" -> context.getString(R.string.moon_new)
            "Waxing Crescent" -> context.getString(R.string.moon_waxing_crescent)
            "First Quarter" -> context.getString(R.string.moon_first_quarter)
            "Waxing Gibbous" -> context.getString(R.string.moon_waxing_gibbous)
            "Full Moon" -> context.getString(R.string.moon_full)
            "Waning Gibbous" -> context.getString(R.string.moon_waning_gibbous)
            "Last Quarter" -> context.getString(R.string.moon_last_quarter)
            "Waning Crescent" -> context.getString(R.string.moon_waning_crescent)
            else -> phase
        }
    }
}