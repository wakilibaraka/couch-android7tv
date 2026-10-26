package cloud.samlo.couch.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Picks the wallpaper for the configured source. Called from the loader thread; every network
 * answer is cached in preferences so a flaky connection keeps the last good picture.
 */
class Wallpapers(private val fetcher: Fetcher, private val prefs: SharedPreferences) {

    fun current(config: Config, userDir: File?, weather: WeatherNow? = null): String? {
        val artPath = art(userDir).firstOrNull()
        return when (config.wallpaper) {
            "gradient" -> artPath
            "art" -> artPath ?: daily()
            Config.WALLPAPER_SKY -> skyList(config, userDir, weather).let { SkyPhotos.ofHour(it) }?.path ?: artPath ?: daily()
            "custom" -> userDir?.let { File(it, "wallpaper.jpg") }?.takeIf { it.isFile }?.path ?: artPath ?: daily()
            "reddit" -> reddit(config) ?: artPath ?: daily()
            else -> artPath ?: daily()
        }
    }

    /** Every candidate for ambient mode (screensaver) to cycle through: uses default sources, not the artwork. */
    fun all(config: Config, userDir: File?, weather: WeatherNow? = null): List<String> =
        sources(config, userDir, weather).ifEmpty { listOfNotNull(daily()) }

    /** Paintings saved in files/art, shown by ambient mode before any other source. */
    private fun art(userDir: File?): List<String> =
        userDir?.let { File(it, "art").listFiles() }.orEmpty()
            .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            .map { it.path }.shuffled()

    private fun sources(config: Config, userDir: File?, weather: WeatherNow?): List<String> = when (config.wallpaper) {
        "reddit" -> redditList(config.subreddit).ifEmpty { listOfNotNull(daily()) }
        Config.WALLPAPER_SKY -> skyList(config, userDir, weather).map { it.path }.ifEmpty { listOfNotNull(daily()) }
        else -> listOfNotNull(current(config, userDir, weather))
    }

    /** The chosen collection: the weather-matched sky, or a theme folder (the sky again when that is empty). */
    private fun skyList(config: Config, userDir: File?, weather: WeatherNow?): List<File> =
        if (config.photoTheme == SkyPhotos.WEATHER_THEME) SkyPhotos.photos(userDir, weather)
        else SkyPhotos.themePhotos(userDir, config.photoTheme).ifEmpty { SkyPhotos.photos(userDir, weather) }

    /** Bing's photo of the day, looked up once a day. */
    private fun daily(): String? {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        if (prefs.getString("wallpaperDay", null) == today) return prefs.getString("wallpaperUrl", null)
        val json = fetcher.text("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=fr-CA")
            ?: return prefs.getString("wallpaperUrl", null)
        val url = try {
            "https://www.bing.com" + JSONObject(json).getJSONArray("images").getJSONObject(0).getString("url")
        } catch (e: Exception) {
            return prefs.getString("wallpaperUrl", null)
        }
        prefs.edit().putString("wallpaperDay", today).putString("wallpaperUrl", url).apply()
        return url
    }

    /** One picture per [Config.wallpaperHours] slot, walking down the subreddit's best posts. */
    private fun reddit(config: Config): String? {
        val list = redditList(config.subreddit)
        if (list.isEmpty()) return null
        val slot = System.currentTimeMillis() / (config.wallpaperHours.coerceAtLeast(1) * 3_600_000L)
        return list[(slot % list.size).toInt()]
    }

    /**
     * Recent image posts from the subreddit, best first. Reddit refuses anonymous API calls, so
     * this reads the public Arctic Shift archive instead. Refreshed every six hours.
     */
    private fun redditList(subreddit: String): List<String> {
        val name = subreddit.trim().removePrefix("r/").ifEmpty { return emptyList() }
        val key = "reddit:$name"
        val cachedAt = prefs.getLong("$key:at", 0L)
        val cached = prefs.getString(key, null)?.let { parse(it) }
        if (cached != null && System.currentTimeMillis() - cachedAt < 6 * 3_600_000L) return cached

        val json = fetcher.text(
            "https://arctic-shift.photon-reddit.com/api/posts/search?subreddit=$name&limit=100&sort=desc",
        ) ?: return cached ?: emptyList()
        val urls = try {
            RedditPosts.wallpaperUrls(json)
        } catch (e: Exception) {
            return cached ?: emptyList()
        }
        if (urls.isNotEmpty()) {
            prefs.edit().putString(key, JSONArray(urls).toString()).putLong("$key:at", System.currentTimeMillis()).apply()
        }
        return urls.ifEmpty { cached ?: emptyList() }
    }

    private fun parse(text: String): List<String> = try {
        val array = JSONArray(text)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}
