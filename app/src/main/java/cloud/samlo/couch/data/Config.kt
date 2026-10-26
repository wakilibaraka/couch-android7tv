package cloud.samlo.couch.data

import java.io.File

/**
 * Everything the user can customise, stored in `Android/data/cloud.samlo.couch/files/config.json`
 * so it can also be edited or backed up over ADB. Custom images go next to it in
 * `icons/<key>.png` (an app's package name, or an input's slug such as `hdmi4`) and
 * `wallpaper.jpg`.
 */
class Config {
    // Apps and inputs
    var pinnedApps: List<String> = DEFAULT_PINNED
    var hiddenApps: Set<String> = DEFAULT_HIDDEN
    var hiddenInputs: Set<String> = emptySet()
    /** Custom names, keyed like icons. */
    var names: Map<String, String> = emptyMap()
    /** Input key to an [InputIcon] id. */
    var inputIcons: Map<String, String> = emptyMap()

    // Rows
    var sectionOrder: List<String> = emptyList()
    var hiddenSections: Set<String> = emptySet()
    /** Channel id to shown or not, overriding what the stock launcher had enabled. */
    var channels: Map<String, Boolean> = emptyMap()

    // Look
    /**
     * "sky" (Apple TV style: big clock, inputs on top, a glass dock over a weather photo), "rows"
     * (cards in rows) or "index" (a list of apps beside what the focused one offers).
     */
    var layout: String = LAYOUT_SKY
    var cardSize: String = "medium"
    var style: String = STYLE_NEUMORPHIC
    var appStorePkg: String = ""
    var accent: String = Accents.default
    var wallpaper: String = "gradient"
    var dim: String = "medium"
    var showLabels = false
    var showDescriptions = true
    var clock24h = true
    var showDate = true
    /** The sky layout's photo collection: "sky" follows the weather, any other is a folder in `themes/`. */
    /** A soft glow under the sky dock's app cards. */
    var dockGlow = true
    var photoTheme = SkyPhotos.WEATHER_THEME

    // Wallpaper: "sky" (photos matched to the weather, from sky/), "daily" (Bing photo of the day),
    // "reddit", "gradient" or "custom" (wallpaper.jpg)
    var subreddit = DEFAULT_SUBREDDIT
    var wallpaperHours = 3

    // Weather
    var weather = true
    /** Empty means locate by IP address. This fork's TV lives in Ogembo, Kenya. */
    var weatherCity = DEFAULT_CITY
    var fahrenheit = false

    /** Minutes idle on the home screen before ambient mode; 0 turns it off. */
    var ambientMinutes = 5

    /** Once a day, look for a newer release on GitHub. */
    var checkUpdates = true

    val cardScale: Float
        get() = when (cardSize) {
            "small" -> 0.85f
            "large" -> 1.18f
            else -> 1f
        }

    /** Changes whenever a setting that affects how cards are drawn changes. */
    val look: String get() = "$cardSize|$accent|$showLabels|$style"

    /** Written atomically so a half-written file never wipes the user's setup. */
    fun save(dir: File?) {
        dir ?: return
        val tmp = File(dir, "config.json.tmp")
        tmp.writeText(toJson())
        tmp.renameTo(File(dir, FILE))
    }

    fun toJson(): String = ConfigJson.encode(this).toString(2)

    companion object {
        private const val FILE = "config.json"
        const val DEFAULT_SUBREDDIT = "WidescreenWallpaper"
        const val LAYOUT_ROWS = "rows"
        const val LAYOUT_INDEX = "index"
        const val LAYOUT_SKY = "sky"
        const val STYLE_NEUMORPHIC = "neumorphic"
        const val STYLE_GLASS = "glass"
        const val WALLPAPER_SKY = "sky"
        const val DEFAULT_CITY = "Ogembo, KE"

        /** Defaults stay generic: the user pins their own apps. */
        /**
         * Most-used first, as the owner asked: YouTube, SmartTube, the browser, the file manager,
         * then media and sharing. Apps that aren't installed are skipped; the rest follow by name.
         */
        val DEFAULT_PINNED = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv",
            "com.phlox.tvwebbrowser",
            "com.lonelycatgames.Xplore",
            "org.videolan.vlc",
            "org.localsend.localsend_app",
            "com.aurora.store",
        )

        // Vendor extras that are noise on a home screen. Reachable again by un-hiding them.
        val DEFAULT_HIDDEN = setOf(
            "com.sony.dtv.promos",
            "com.sony.dtv.sonyselect",
            "com.sony.dtv.seeds.iot",
            "com.sony.dtv.notificationcenter",
            "com.sony.dtv.smartmediaapp",
            "com.sony.dtv.timers",
            "com.sony.dtv.osat.music",
            "com.sony.dtv.tvx",
            "com.spocky.projengmenu",
        )

        fun load(dir: File?): Config {
            val text = try {
                dir?.let { File(it, FILE) }?.takeIf { it.isFile }?.readText()
            } catch (e: Exception) {
                null
            } ?: return Config()
            return fromJson(text)
        }

        /** Unreadable or malformed text gives the defaults, never a crash. */
        fun fromJson(text: String): Config {
            val config = Config()
            val json = try {
                org.json.JSONObject(text)
            } catch (e: Exception) {
                return config
            }
            ConfigJson.decode(json, config)
            return config
        }
    }
}
