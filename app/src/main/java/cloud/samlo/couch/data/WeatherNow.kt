package cloud.samlo.couch.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** One day of the forecast: [date] is the local ISO date ("2026-10-26"). */
class WeatherDay(val date: String, val code: Int, val high: Int, val low: Int, val rainChance: Int) {
    val condition: Condition get() = Condition.of(code)
}

/** One hour ahead: [hour] is the local hour of day, 0 to 23. */
class WeatherHour(val hour: Int, val code: Int, val temperature: Int, val rainChance: Int, val isDay: Boolean) {
    val icon: Int get() = Condition.of(code).icon(isDay)
}

/** Current conditions at [place], plus today's range and the days ahead. */
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
    /** Today first, then the days ahead; empty when the answer had no forecast. */
    val days: List<WeatherDay> = emptyList(),
    /** The next hours, soonest first; empty when the answer had none. */
    val hours: List<WeatherHour> = emptyList(),
) {
    val condition: Condition get() = Condition.of(code)
    val icon: Int get() = condition.icon(isDay)

    /** The preferences cache form. */
    fun serialize(): String = JSONObject()
        .put("place", place).put("t", temperature).put("hi", high).put("lo", low)
        .put("code", code).put("day", isDay).put("rise", sunrise).put("set", sunset)
        .put("days", JSONArray(days.map {
            JSONObject().put("d", it.date).put("c", it.code).put("hi", it.high).put("lo", it.low).put("p", it.rainChance)
        }))
        .put("hours", JSONArray(hours.map {
            JSONObject().put("h", it.hour).put("c", it.code).put("t", it.temperature).put("p", it.rainChance).put("d", it.isDay)
        })).toString()

    companion object {
        fun parse(text: String): WeatherNow? = try {
            val o = JSONObject(text)
            WeatherNow(
                o.getString("place"), o.getInt("t"), o.getInt("hi"), o.getInt("lo"), o.getInt("code"), o.getBoolean("day"),
                o.optInt("rise", -1), o.optInt("set", -1),
                o.optJSONArray("days")?.let { a ->
                    (0 until a.length()).map { i ->
                        val d = a.getJSONObject(i)
                        WeatherDay(d.getString("d"), d.getInt("c"), d.getInt("hi"), d.getInt("lo"), d.optInt("p", -1))
                    }
                }.orEmpty(),
                o.optJSONArray("hours")?.let { a ->
                    (0 until a.length()).map { i ->
                        val h = a.getJSONObject(i)
                        WeatherHour(h.getInt("h"), h.getInt("c"), h.getInt("t"), h.optInt("p", -1), h.optBoolean("d", true))
                    }
                }.orEmpty(),
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
                days = days(daily),
                hours = o.optJSONObject("hourly")?.let { hours(it, current.optString("time")) }.orEmpty(),
            )
        } catch (e: Exception) {
            null
        }

        /** Up to [count] whole hours after the current one. */
        fun hours(hourly: JSONObject, now: String?, count: Int = 7): List<WeatherHour> {
            val times = hourly.optJSONArray("time") ?: return emptyList()
            val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
            val codes = hourly.optJSONArray("weather_code") ?: return emptyList()
            val rain = hourly.optJSONArray("precipitation_probability")
            val day = hourly.optJSONArray("is_day")
            // ISO local times sort as text: the first hour after "now" is where the list starts.
            val from = (0 until times.length()).firstOrNull { now.isNullOrEmpty() || times.getString(it) > now } ?: return emptyList()
            return (from until minOf(times.length(), from + count)).mapNotNull { i ->
                if (temps.isNull(i) || codes.isNull(i)) return@mapNotNull null
                val hour = times.getString(i).substringAfter('T').substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                WeatherHour(
                    hour, codes.getInt(i), temps.getDouble(i).roundToInt(),
                    if (rain == null || rain.isNull(i)) -1 else rain.getInt(i),
                    day == null || day.isNull(i) || day.getInt(i) == 1,
                )
            }
        }

        private fun days(daily: JSONObject): List<WeatherDay> {
            val dates = daily.optJSONArray("time") ?: return emptyList()
            val codes = daily.optJSONArray("weather_code") ?: return emptyList()
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val rain = daily.optJSONArray("precipitation_probability_max")
            return (0 until dates.length()).mapNotNull { i ->
                if (highs.isNull(i) || lows.isNull(i) || codes.isNull(i)) return@mapNotNull null
                WeatherDay(
                    dates.getString(i), codes.getInt(i), highs.getDouble(i).roundToInt(), lows.getDouble(i).roundToInt(),
                    if (rain == null || rain.isNull(i)) -1 else rain.getInt(i),
                )
            }
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
