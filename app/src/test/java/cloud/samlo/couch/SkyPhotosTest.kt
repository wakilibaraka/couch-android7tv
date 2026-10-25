package cloud.samlo.couch

import cloud.samlo.couch.data.SkyPhotos
import cloud.samlo.couch.data.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Test

class SkyPhotosTest {
    private fun weather(code: Int) = WeatherNow("Ogembo", 21, 25, 14, code, true, 6 * 60 + 24, 18 * 60 + 36)

    @Test
    fun timeOfDayFollowsSunriseAndSunset() {
        val w = weather(0)
        assertEquals("dawn", SkyPhotos.timeOf(6 * 60 + 30, w))
        assertEquals("day", SkyPhotos.timeOf(12 * 60, w))
        assertEquals("dusk", SkyPhotos.timeOf(18 * 60 + 10, w))
        assertEquals("night", SkyPhotos.timeOf(22 * 60, w))
        assertEquals("night", SkyPhotos.timeOf(3 * 60, null))
    }

    @Test
    fun weatherCodesGroupIntoSkies() {
        assertEquals("clear", SkyPhotos.weatherOf(weather(1)))
        assertEquals("cloudy", SkyPhotos.weatherOf(weather(3)))
        assertEquals("rain", SkyPhotos.weatherOf(weather(61)))
        assertEquals("storm", SkyPhotos.weatherOf(weather(95)))
        assertEquals("fog", SkyPhotos.weatherOf(weather(45)))
        assertEquals("clear", SkyPhotos.weatherOf(null))
    }

    @Test
    fun slotsFallBackToGentlerWeatherThenNearbyTimes() {
        val slots = SkyPhotos.slots("night", "storm")
        assertEquals(listOf("night-storm", "night-rain", "night-cloudy", "night-clear", "dusk-storm"), slots.take(5))
    }

    @Test
    fun sunTimesParseFromLocalIso() {
        assertEquals(384, WeatherNow.minutes("2026-10-25T06:24"))
        assertEquals(-1, WeatherNow.minutes(null))
        assertEquals(-1, WeatherNow.minutes("garbage"))
    }
}
