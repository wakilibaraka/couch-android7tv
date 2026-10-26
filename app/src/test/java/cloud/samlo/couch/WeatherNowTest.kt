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

    @Test
    fun hourlyForecastIsSpacedByTwoHours() {
        val hourlyJson = org.json.JSONObject(
            """{"time":["2026-10-26T01:00","2026-10-26T02:00","2026-10-26T03:00","2026-10-26T04:00","2026-10-26T05:00","2026-10-26T06:00","2026-10-26T07:00","2026-10-26T08:00","2026-10-26T09:00","2026-10-26T10:00","2026-10-26T11:00","2026-10-26T12:00","2026-10-26T13:00","2026-10-26T14:00"],""" +
                """"temperature_2m":[15.0,16.0,17.0,18.0,19.0,20.0,21.0,22.0,23.0,24.0,25.0,26.0,27.0,28.0],""" +
                """"weather_code":[0,1,2,3,0,1,2,3,0,1,2,3,0,1],""" +
                """"precipitation_probability":[0,10,20,30,0,10,20,30,0,10,20,30,0,10]}"""
        )
        val hours = WeatherNow.hours(hourlyJson, "2026-10-26T01:30", count = 7)
        assertEquals(7, hours.size)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14), hours.map { it.hour })
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
