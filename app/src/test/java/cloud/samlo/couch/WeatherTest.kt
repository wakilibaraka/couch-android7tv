package cloud.samlo.couch

import android.content.SharedPreferences
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.Weather
import cloud.samlo.couch.data.WeatherDay
import cloud.samlo.couch.data.WeatherHour
import cloud.samlo.couch.data.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WeatherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class Editor(private val prefs: FakePrefs) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { pending[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { pending[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { removes.add(key); return this }
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) prefs.map.clear()
                for (r in removes) prefs.map.remove(r)
                prefs.map.putAll(pending)
            }
        }
    }

    private class OfflineFetcher : Fetcher(null) {
        override fun text(url: String): String? = null
    }

    private class MockFetcher(val responses: Map<String, String>) : Fetcher(null) {
        override fun text(url: String): String? = responses.entries.firstOrNull { url.contains(it.key) }?.value
    }

    @Test
    fun returnsDefaultOgemboWhenCompletelyOfflineOnFirstBoot() {
        val prefs = FakePrefs()
        val weather = Weather(OfflineFetcher(), prefs)
        val config = Config().apply { weatherCity = "Ogembo, KE" }

        val result = weather.now(config)
        assertNotNull(result)
        assertEquals("Ogembo", result!!.place)
        assertTrue(result.hours.isNotEmpty())
        assertTrue(result.days.isNotEmpty())
    }

    @Test
    fun cachesToPrefsAndFilesAndSurvivesOffline() {
        val cacheDir = tempFolder.newFolder("internal")
        val userDir = tempFolder.newFolder("external")
        val prefs = FakePrefs()

        val forecastJson = """
            {"current":{"temperature_2m":19.0,"weather_code":1,"is_day":1},
             "daily":{"time":["2026-10-26","2026-10-27"],"weather_code":[1,2],"temperature_2m_max":[25.0,26.0],"temperature_2m_min":[15.0,16.0],"precipitation_probability_max":[0,10],"sunrise":["2026-10-26T06:24"],"sunset":["2026-10-26T18:36"]},
             "hourly":{"time":["2026-10-26T02:00","2026-10-26T04:00"],"temperature_2m":[18.0,17.0],"weather_code":[1,1],"precipitation_probability":[0,0],"is_day":[0,0]}}
        """.trimIndent()

        val onlineFetcher = MockFetcher(mapOf("open-meteo.com" to forecastJson))
        val onlineWeather = Weather(onlineFetcher, prefs, cacheDir, userDir)
        val config = Config().apply { weatherCity = "Ogembo, KE" }

        // Fetch online
        val onlineResult = onlineWeather.now(config)
        assertNotNull(onlineResult)
        assertEquals(19, onlineResult!!.temperature)

        // Verify files were written
        val cacheFile = File(cacheDir, "weather_cache.json")
        val userFile = File(userDir, "weather.json")
        assertTrue(cacheFile.exists())
        assertTrue(userFile.exists())

        // Clear prefs to simulate wipe / reboot / cache eviction
        prefs.map.clear()

        // Create an offline Weather instance
        val offlineWeather = Weather(OfflineFetcher(), prefs, cacheDir, userDir)
        val cachedResult = offlineWeather.now(config)
        assertNotNull(cachedResult)
        assertEquals(19, cachedResult!!.temperature)
        assertEquals("Ogembo", cachedResult.place)
    }

    @Test
    fun returnsLastValidWhenNetworkFailsLater() {
        val prefs = FakePrefs()
        val cacheDir = tempFolder.newFolder("internal2")
        val validNow = WeatherNow("Ogembo", 22, 26, 16, 2, true)
        val weather = Weather(OfflineFetcher(), prefs, cacheDir)
        val config = Config().apply { weatherCity = "Ogembo, KE" }

        // Pre-populate SharedPreferences with stale timestamp
        val key = "weather4:${config.weatherCity}:${config.fahrenheit}"
        prefs.edit().putString(key, validNow.serialize()).putLong("$key:at", 1000L).apply()

        // Calling now() offline returns the cached data despite timestamp being ancient
        val result = weather.now(config)
        assertNotNull(result)
        assertEquals(22, result!!.temperature)
        assertEquals("Ogembo", result.place)
    }
}
