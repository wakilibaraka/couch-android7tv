package cloud.samlo.couch.data

import org.json.JSONObject
import kotlin.math.roundToInt

/** Current conditions at [place], plus today's range. */
class WeatherNow(
    val place: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val isDay: Boolean,
    /** Today's sunrise and sunset in local minutes since midnight; -1 when unknown. */
    val sunrise: Int = -1,
    val sunset: Int = -1,
) {
    val condition: Condition get() = Condition.of(code)
    val icon: Int get() = condition.icon(isDay)

    /** The preferences cache form. */
    fun serialize(): String = JSONObject()
        .put("place", place).put("t", temperature).put("hi", high).put("lo", low)
        .put("code", code).put("day", isDay).put("rise", sunrise).put("set", sunset).toString()

    companion object {
        fun parse(text: String): WeatherNow? = try {
            val o = JSONObject(text)
            WeatherNow(
                o.getString("place"), o.getInt("t"), o.getInt("hi"), o.getInt("lo"), o.getInt("code"), o.getBoolean("day"),
                o.optInt("rise", -1), o.optInt("set", -1),
            )
        } catch (e: Exception) {
            null
        }

        /** An Open-Meteo forecast answer; null when it isn't shaped as expected. */
        fun fromForecast(place: String, json: String): WeatherNow? = try {
            val o = JSONObject(json)
            val current = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            WeatherNow(
                place = place,
                temperature = current.getDouble("temperature_2m").roundToInt(),
                high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
                sunrise = minutes(daily.optJSONArray("sunrise")?.optString(0)),
                sunset = minutes(daily.optJSONArray("sunset")?.optString(0)),
            )
        } catch (e: Exception) {
            null
        }

        /** "2026-10-25T06:24" (local time) to 384; -1 when absent or malformed. */
        fun minutes(iso: String?): Int {
            val time = iso?.substringAfter('T', "") ?: return -1
            val h = time.substringBefore(':').toIntOrNull() ?: return -1
            val m = time.substringAfter(':', "").take(2).toIntOrNull() ?: return -1
            return h * 60 + m
        }
    }
}
