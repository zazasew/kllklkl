package com.morningdigest.app.data.remote

import com.morningdigest.app.data.model.DayPartForecast
import com.morningdigest.app.data.model.WeatherDayForecast
import com.morningdigest.app.data.model.WeatherToday
import com.morningdigest.app.data.model.WeatherTomorrow
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * MET Norway / Norwegian Meteorological Institute forecast client.
 * Locationforecast provides up to nine days. We expose the next seven days.
 * A short in-memory cache prevents repeated screen opens from fetching a new
 * model run every few minutes. The displayed MET update timestamp remains the
 * source-of-truth freshness indicator.
 */
class MetWeatherFetcher(private val client: OkHttpClient) {
    data class Forecast(
        val today: WeatherToday,
        val tomorrow: WeatherTomorrow,
        val nextDays: List<WeatherDayForecast>,
        val updatedAtMillis: Long,
        val crossCheck: String = "Unavailable"
    )

    private data class Point(
        val instant: Instant,
        val temp: Double,
        val humidity: Double?,
        val wind: Double?,
        val symbol: String?,
        val pop: Double?
    )

    @Volatile private var cached: Pair<String, Pair<Long, Forecast>>? = null

    suspend fun fetch(city: String, country: String): Forecast? = withContext(Dispatchers.IO) {
        val key = "${city.trim().lowercase(Locale.ROOT)},${country.trim().lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        cached?.takeIf { it.first == key && now - it.second.first < CACHE_MS }?.let { return it.second.second }

        val coords = geocode(city, country) ?: return null
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${"%.4f".format(Locale.US, coords.first)}&lon=${"%.4f".format(Locale.US, coords.second)}"
        val json = request(url)
        val root = JSONObject(json)
        val props = root.optJSONObject("properties") ?: return null
        val meta = props.optJSONObject("meta")
        val updated = meta?.optString("updated_at")?.let { parseInstant(it)?.toEpochMilli() } ?: now
        val timeseries = props.optJSONArray("timeseries") ?: return null

        val points = buildList {
            for (i in 0 until timeseries.length()) {
                val item = timeseries.optJSONObject(i) ?: continue
                val time = parseInstant(item.optString("time")) ?: continue
                val instant = item.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details") ?: continue
                val temp = instant.optDouble("air_temperature", Double.NaN)
                if (temp.isNaN()) continue
                val next1 = item.optJSONObject("data")?.optJSONObject("next_1_hours")
                val next6 = item.optJSONObject("data")?.optJSONObject("next_6_hours")
                val symbol = next1?.optJSONObject("summary")?.optString("symbol_code")
                    ?.takeIf { it.isNotBlank() }
                    ?: next6?.optJSONObject("summary")?.optString("symbol_code")
                val pop = next1?.optJSONObject("details")?.optDouble("probability_of_precipitation", Double.NaN)
                    ?.takeIf { !it.isNaN() }
                    ?: next6?.optJSONObject("details")?.optDouble("probability_of_precipitation", Double.NaN)?.takeIf { !it.isNaN() }
                add(Point(time, temp, instant.optDouble("relative_humidity", Double.NaN).takeIf { !it.isNaN() }, instant.optDouble("wind_speed", Double.NaN).takeIf { !it.isNaN() }, symbol, pop))
            }
        }.sortedBy { it.instant }

        if (points.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val today = points.first().instant.atZone(zone).toLocalDate()
        val days = points.groupBy { it.instant.atZone(zone).toLocalDate() }.toSortedMap()
        val dayList = days.entries.take(7)

        fun description(symbol: String?): String? = symbol?.replace('_', ' ')?.replace(Regex("\\b\\w")) { it.value.uppercase() }
        fun icon(symbol: String?): String? = symbol
        fun dayForecast(date: java.time.LocalDate, pts: List<Point>): WeatherDayForecast {
            val midday = pts.minByOrNull { abs(it.instant.atZone(zone).hour - 13) } ?: pts[pts.size / 2]
            return WeatherDayForecast(
                dayLabel = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                minTemp = pts.minOf { it.temp },
                maxTemp = pts.maxOf { it.temp },
                description = description(midday.symbol),
                icon = icon(midday.symbol),
                rainChancePercent = pts.mapNotNull { it.pop }.maxOrNull()?.roundToInt(),
                dateLabel = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                humidity = pts.mapNotNull { it.humidity }.average().takeIf { !it.isNaN() }?.roundToInt(),
                windSpeed = pts.mapNotNull { it.wind }.average().takeIf { !it.isNaN() },
                forecastUpdatedAtMillis = updated
            )
        }

        val todayPts = days[today].orEmpty()
        val todayMid = todayPts.minByOrNull { abs(it.instant.atZone(zone).hour - java.time.ZonedDateTime.now(zone).hour) } ?: todayPts.firstOrNull() ?: points.first()
        val todayModel = WeatherToday(
            temp = todayMid.temp,
            feelsLike = null,
            humidity = todayMid.humidity?.roundToInt(),
            windSpeed = todayMid.wind,
            description = description(todayMid.symbol),
            icon = todayMid.symbol,
            available = true
        )

        val tomorrowDate = dayList.getOrNull(1)?.key
        val tomorrowPts = tomorrowDate?.let { days[it].orEmpty() }.orEmpty()
        val tomorrowModel = if (tomorrowDate != null && tomorrowPts.isNotEmpty()) {
            val d = dayForecast(tomorrowDate, tomorrowPts)
            val morning = tomorrowPts.minByOrNull { abs(it.instant.atZone(zone).hour - 9) }
            val afternoon = tomorrowPts.minByOrNull { abs(it.instant.atZone(zone).hour - 15) }
            val evening = tomorrowPts.minByOrNull { abs(it.instant.atZone(zone).hour - 21) }
            WeatherTomorrow(
                avgTemp = tomorrowPts.map { it.temp }.average(), minTemp = d.minTemp, maxTemp = d.maxTemp,
                humidity = d.humidity, windSpeed = d.windSpeed, description = d.description, icon = d.icon,
                rainChancePercent = d.rainChancePercent,
                parts = listOfNotNull(morning?.let { DayPartForecast("Morning", it.temp, description(it.symbol), it.symbol) }, afternoon?.let { DayPartForecast("Afternoon", it.temp, description(it.symbol), it.symbol) }, evening?.let { DayPartForecast("Evening", it.temp, description(it.symbol), it.symbol) }),
                forecastUpdatedAtMillis = updated, available = true
            )
        } else WeatherTomorrow(available = false)

        val next = dayList.drop(1).take(6).map { dayForecast(it.key, it.value) }
        val crossCheck = openMeteoCrossCheck(coords.first, coords.second, tomorrowModel)
        val tomorrowWithConfidence = tomorrowModel.copy(forecastConfidence = crossCheck)
        val result = Forecast(todayModel, tomorrowWithConfidence, next, updated, crossCheck)
        cached = key to (now to result)
        result
    }

    private fun openMeteoCrossCheck(lat: Double, lon: Double, tomorrow: WeatherTomorrow): String {
        if (!tomorrow.available || tomorrow.maxTemp == null || tomorrow.minTemp == null) return "Unavailable"
        return runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${"%.4f".format(Locale.US, lat)}&longitude=${"%.4f".format(Locale.US, lon)}&daily=temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto"
            val root = JSONObject(request(url))
            val daily = root.optJSONObject("daily") ?: return@runCatching "Unavailable"
            val max = daily.optJSONArray("temperature_2m_max")?.optDouble(1, Double.NaN) ?: Double.NaN
            val min = daily.optJSONArray("temperature_2m_min")?.optDouble(1, Double.NaN) ?: Double.NaN
            if (max.isNaN() || min.isNaN()) return@runCatching "Unavailable"
            val diff = maxOf(abs(max - tomorrow.maxTemp), abs(min - tomorrow.minTemp))
            when {
                diff <= 2.0 -> "High agreement"
                diff <= 4.0 -> "Moderate agreement"
                else -> "Forecasts differ"
            }
        }.getOrDefault("Unavailable")
    }

    private fun geocode(city: String, country: String): Pair<Double, Double>? {
        val q = java.net.URLEncoder.encode("$city, $country", "UTF-8")
        val body = request("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=1&countrycodes=no")
        val a = org.json.JSONArray(body).optJSONObject(0) ?: return null
        val lat = a.optString("lat").toDoubleOrNull() ?: return null
        val lon = a.optString("lon").toDoubleOrNull() ?: return null
        return lat to lon
    }

    private fun request(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "TheBrief/1.3 (weather; personal Android app)")
            .header("Accept", "application/json")
            .build()
        return client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty response")
        }
    }

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    companion object { private const val CACHE_MS = 30 * 60 * 1000L }
}
