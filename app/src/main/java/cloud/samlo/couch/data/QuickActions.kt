package cloud.samlo.couch.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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

    /** The TV's picture and sound menu, the one its remote's menu key shows. */
    fun picture(context: Context) = open(
        context,
        Intent("com.cvte.intent.action.TV_MENU").setPackage(CVTE_SETTINGS),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
    )

    fun sound(context: Context) = open(context, Intent(Settings.ACTION_SOUND_SETTINGS))

    /** Starts the screensaver now (Android's own "start now" activity), or opens its settings. */
    fun screensaver(context: Context) = open(
        context,
        Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator"),
        Intent(Settings.ACTION_DREAM_SETTINGS),
    )

    fun settings(context: Context) = open(context, Intent(Settings.ACTION_SETTINGS))

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
