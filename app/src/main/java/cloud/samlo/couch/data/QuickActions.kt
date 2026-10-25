package cloud.samlo.couch.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast
import cloud.samlo.couch.R

/**
 * What the sky layout's quick-settings tiles open. Each tries the TV's own screen first (CVTE
 * boards have a picture and sound menu of their own) and falls back to Android's settings.
 */
object QuickActions {
    private const val CVTE_SETTINGS = "com.cvte.tv.androidsetting"

    fun wifi(context: Context) = open(context, Intent(Settings.ACTION_WIFI_SETTINGS))

    /** Picture mode: cycles TV modes on CVTE boards (P.MODE), or opens safe TV settings. */
    fun picture(context: Context, onModeChanged: ((String) -> Unit)? = null) {
        if (CvteTv.available(context)) {
            CvteTv.cyclePicture(context) { mode ->
                if (mode != null) {
                    onModeChanged?.invoke(mode)
                    Toast.makeText(context, mode, Toast.LENGTH_SHORT).show()
                } else {
                    open(context, CvteTv.settingIntent(), Intent(Settings.ACTION_DISPLAY_SETTINGS))
                }
            }
            return
        }
        open(context, CvteTv.settingIntent(), Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    /** Sound mode: cycles TV modes on CVTE boards (S.MODE), or opens safe TV settings. */
    fun sound(context: Context, onModeChanged: ((String) -> Unit)? = null) {
        if (CvteTv.available(context)) {
            CvteTv.cycleSound(context) { mode ->
                if (mode != null) {
                    onModeChanged?.invoke(mode)
                    Toast.makeText(context, mode, Toast.LENGTH_SHORT).show()
                } else {
                    open(context, CvteTv.settingIntent(), Intent(Settings.ACTION_SOUND_SETTINGS))
                }
            }
            return
        }
        open(context, CvteTv.settingIntent(), Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    /** Starts the screensaver now (Android's own "start now" activity), or opens its settings. */
    fun screensaver(context: Context) = open(
        context,
        Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator"),
        Intent(Settings.ACTION_DREAM_SETTINGS),
    )

    fun settings(context: Context) = open(context, Intent(Settings.ACTION_SETTINGS))

    /** The TV's own file browser, else Android's Files. */
    fun files(context: Context) = open(
        context,
        Intent(Intent.ACTION_MAIN).setClassName("com.cvte.filebrowser.activity", "com.cvte.filebrowser.activity.Guide1Activity"),
        Intent(Intent.ACTION_MAIN).setClassName("com.android.documentsui", "com.android.documentsui.LauncherActivity"),
    )

    /** The TV's own settings menu on CVTE boards, else Android's. */
    fun tvSettings(context: Context) = open(context, CvteTv.settingIntent(), Intent(Settings.ACTION_SETTINGS))

    /** Media volume as a percentage. */
    fun volume(context: Context): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    /** Mutes or unmutes media; returns the level now heard (0 while muted). */
    fun toggleMute(context: Context): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        } catch (e: SecurityException) {
            return volume(context)
        }
        return if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0 else volume(context)
    }

    /** One step up or down, as the remote's volume keys do; returns the new percentage. */
    fun adjustVolume(context: Context, up: Boolean): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        try {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
        } catch (e: SecurityException) {
            // Do Not Disturb can refuse changes; the level shown stays truthful.
        }
        return volume(context)
    }

    /** The connected network's name, or null when not on Wi-Fi. */
    fun wifiName(context: Context): String? = try {
        @Suppress("DEPRECATION")
        val info = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        info?.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && info.networkId != -1 }
    } catch (e: SecurityException) {
        null
    }

    private fun open(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next one.
            } catch (e: SecurityException) {
                // Not exported on this TV: try the next one.
            }
        }
        Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
    }
}
