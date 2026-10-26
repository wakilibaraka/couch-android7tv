package cloud.samlo.couch.data

import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Current conditions from Open-Meteo (free, no key). The resolved place name is always shown so
 * a wrong guess is easy to spot. Called from the loader thread; the last good answer is kept in
 * preferences and persistent local files so a flaky connection or offline boot still shows something.
 */
class Weather(
    private val fetcher: Fetcher,
    private val prefs: SharedPreferences,
    private val cacheDir: File? = null,
    private val userDir: File? = null,
) {
    private val places = WeatherPlaces(fetcher, prefs)

    fun now(config: Config): WeatherNow? {
        if (!config.weather) return null
        val cacheKey = "weather4:${config.weatherCity}:${config.fahrenheit}"
        val cachedAt = prefs.getLong("$cacheKey:at", 0L)
        val fresh = System.currentTimeMillis() - cachedAt < FRESH_MS

        if (fresh) {
            cached(cacheKey)?.let { return it }
        }

        val place = places.find(config.weatherCity)
        val json = if (place != null) fetcher.text(forecastUrl(place, config.fahrenheit)) else null
        val fetched = if (place != null && json != null) WeatherNow.fromForecast(place.name, json) else null

        if (fetched != null) {
            persist(cacheKey, fetched)
            return fetched
        }

        return cached(cacheKey) ?: defaultOgembo()
    }

    private fun persist(key: String, now: WeatherNow) {
        val serialized = now.serialize()
        try {
            prefs.edit()
                .putString(key, serialized)
                .putString(FALLBACK_KEY, serialized)
                .putLong("$key:at", System.currentTimeMillis())
                .apply()
        } catch (e: Throwable) {
        }
        if (cacheDir != null) {
            try {
                File(cacheDir, CACHE_FILE).writeText(serialized)
            } catch (e: Throwable) {
            }
        }
        if (userDir != null) {
            try {
                File(userDir, USER_FILE).writeText(serialized)
            } catch (e: Throwable) {
            }
        }
    }

    private fun cached(key: String): WeatherNow? {
        // 1. Specific preferences key
        prefs.getString(key, null)?.let { WeatherNow.parse(it)?.let { w -> return w } }
        // 2. Fallback preferences key
        prefs.getString(FALLBACK_KEY, null)?.let { WeatherNow.parse(it)?.let { w -> return w } }
        // 3. Internal file cache
        if (cacheDir != null) {
            try {
                val f = File(cacheDir, CACHE_FILE)
                if (f.exists()) {
                    WeatherNow.parse(f.readText())?.let { return it }
                }
            } catch (e: Throwable) {
            }
        }
        // 4. External user directory cache
        if (userDir != null) {
            try {
                val f = File(userDir, USER_FILE)
                if (f.exists()) {
                    WeatherNow.parse(f.readText())?.let { return it }
                }
            } catch (e: Throwable) {
            }
        }
        return null
    }

    private fun defaultOgembo(): WeatherNow {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isDay = hour in 6..18
        val hours = (1..14 step 2).map { offset ->
            val h = (hour + offset) % 24
            val hIsDay = h in 6..18
            WeatherHour(h, 2, if (hIsDay) 23 else 16, 10, hIsDay)
        }.take(7)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = (0..7).map { dayOffset ->
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            WeatherDay(dateFmt.format(c.time), 2, 24, 15, 20)
        }
        return WeatherNow("Ogembo", 21, 24, 15, 2, isDay, 6 * 60 + 24, 18 * 60 + 36, days, hours)
    }

    private fun forecastUrl(place: Place, fahrenheit: Boolean): String {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        return "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,sunrise,sunset&forecast_days=8" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day&forecast_hours=48&timezone=auto$unit"
    }

    private companion object {
        const val FRESH_MS = 20 * 60_000L
        const val FALLBACK_KEY = "weather4:fallback"
        const val CACHE_FILE = "weather_cache.json"
        const val USER_FILE = "weather.json"
    }
}
