package cloud.samlo.couch

import cloud.samlo.couch.data.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WeatherNowTest {
    private val answer = """
        {"current":{"temperature_2m":17.4,"weather_code":3,"is_day":0},
         "daily":{"time":["2026-10-25","2026-10-26","2026-10-27"],
                  "weather_code":[3,61,null],
                  "temperature_2m_max":[24.2,22.6,23.0],
                  "temperature_2m_min":[14.1,13.5,13.9],
                  "precipitation_probability_max":[20,80,null],
                  "sunrise":["2026-10-25T06:24","2026-10-26T06:24","2026-10-27T06:24"],
                  "sunset":["2026-10-25T18:36","2026-10-26T18:36","2026-10-27T18:36"]}}
    """.trimIndent()

    @Test
    fun readsTheDaysAhead() {
        val now = assertNotNullAndGet(WeatherNow.fromForecast("Ogembo", answer))
        assertEquals(17, now.temperature)
        assertEquals(24, now.high)
        // A day with a missing value is left out rather than shown wrong.
        assertEquals(listOf("2026-10-25", "2026-10-26"), now.days.map { it.date })
        assertEquals(61, now.days[1].code)
        assertEquals(23, now.days[1].high)
        assertEquals(80, now.days[1].rainChance)
    }

    @Test
    fun cacheKeepsTheDays() {
        val now = assertNotNullAndGet(WeatherNow.fromForecast("Ogembo", answer))
        val copy = assertNotNullAndGet(WeatherNow.parse(now.serialize()))
        assertEquals(now.days.map { it.date to it.low }, copy.days.map { it.date to it.low })
        assertEquals(now.sunset, copy.sunset)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
