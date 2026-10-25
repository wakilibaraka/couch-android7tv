package cloud.samlo.couch.data

import java.io.File
import java.util.Calendar

/**
 * The "sky" wallpaper: real photos sorted by time of day and weather, in
 * `files/sky/<time>-<weather>/` (`dawn-clear`, `day-rain`, `night-cloudy`…). The photo always
 * shows a sky like the one outside: the current slot's folder first, then gentler weather at the
 * same time of day, then the same weather at any time. Within a folder the photo changes hourly.
 */
object SkyPhotos {
    const val DIR = "sky"
    val TIMES = listOf("dawn", "day", "dusk", "night")
    val WEATHERS = listOf("clear", "cloudy", "rain", "storm", "fog")
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** At the equator the sun keeps nearly the same hours all year: used until weather arrives. */
    private const val SUNRISE = 6 * 60 + 30
    private const val SUNSET = 18 * 60 + 40

    /** The photo for now, or null when the folders are empty. */
    fun pick(userDir: File?, weather: WeatherNow?, now: Long = System.currentTimeMillis()): File? {
        val photos = photos(userDir, weather, now)
        if (photos.isEmpty()) return null
        return photos[((now / HOUR_MS) % photos.size).toInt()]
    }

    /** Every photo of the best-matching folder, for ambient mode to cycle through. */
    fun photos(userDir: File?, weather: WeatherNow?, now: Long = System.currentTimeMillis()): List<File> {
        val root = userDir?.let { File(it, DIR) }?.takeIf { it.isDirectory } ?: return emptyList()
        for (slot in slots(timeOf(minuteOfDay(now), weather), weatherOf(weather))) {
            val files = File(root, slot).listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                ?.sortedBy { it.name }
            if (!files.isNullOrEmpty()) return files
        }
        return emptyList()
    }

    /** The photo's credit, from a text file beside it (`1.jpg` → `1.txt`), first line only. */
    fun credit(photo: File): String? = try {
        File(photo.parentFile, photo.nameWithoutExtension + ".txt").takeIf { it.isFile }
            ?.readLines()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    /** Folder names to try, best first. */
    fun slots(time: String, weather: String): List<String> {
        val weathers = listOf(weather) + when (weather) {
            "storm" -> listOf("rain", "cloudy", "clear")
            "rain", "fog" -> listOf("cloudy", "clear")
            "cloudy" -> listOf("clear")
            else -> listOf("cloudy")
        }
        // Dawn and dusk look alike; night and day stand in for each other last.
        val times = listOf(time) + when (time) {
            "dawn" -> listOf("dusk", "day")
            "dusk" -> listOf("dawn", "day")
            "night" -> listOf("dusk")
            else -> listOf("dawn", "dusk")
        }
        val out = LinkedHashSet<String>()
        for (t in times) for (w in weathers) out += "$t-$w"
        return out.toList()
    }

    /** dawn: 40 minutes either side of sunrise; dusk: an hour before sunset to half an hour after. */
    fun timeOf(minute: Int, weather: WeatherNow?): String {
        val rise = weather?.sunrise?.takeIf { it >= 0 } ?: SUNRISE
        val set = weather?.sunset?.takeIf { it >= 0 } ?: SUNSET
        return when {
            minute in (rise - 40)..(rise + 40) -> "dawn"
            minute in (set - 60)..(set + 30) -> "dusk"
            minute in rise..set -> "day"
            else -> "night"
        }
    }

    fun weatherOf(weather: WeatherNow?): String = when (weather?.condition) {
        null, Condition.CLEAR, Condition.MOSTLY_CLEAR -> "clear"
        Condition.FOG -> "fog"
        Condition.DRIZZLE, Condition.RAIN, Condition.SNOW -> "rain"
        Condition.STORM -> "storm"
        else -> "cloudy"
    }

    private fun minuteOfDay(now: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private const val HOUR_MS = 3_600_000L
}
