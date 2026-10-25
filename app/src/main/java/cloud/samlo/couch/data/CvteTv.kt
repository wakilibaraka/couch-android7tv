package cloud.samlo.couch.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The TV API of CVTE boards (Vision, many Android 7 MediaTek TVs). Their tuner and AV inputs are
 * not Android TV inputs, and their HDMI inputs don't open from a passthrough intent: the stock
 * launcher switches through this service instead, and so does Couch.
 *
 * The client classes (`com.cvte.tv.api.aidl.*`) are on the boot classpath of these TVs, so they
 * are reached by reflection; elsewhere [available] is false and nothing here runs.
 */
object CvteTv {
    private const val PACKAGE = "com.cvte.tv.api.impl"
    private const val ACTION = "com.cvte.tv.api.TV_API_SERVICE"
    private const val STUB = "com.cvte.tv.api.aidl.ITvApiManager\$Stub"
    private const val CONNECT_MS = 3_000L

    /** One switchable source: [category] is the API's name without its prefix (`HDMI`, `AV`, `ATV`…). */
    class Source(val id: Int, val name: String, val category: String)

    @Volatile
    private var api: Any? = null
    private var latch: CountDownLatch? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            api = try {
                Class.forName(STUB).getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            } catch (e: Throwable) {
                null
            }
            latch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            api = null
        }
    }

    fun available(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        Class.forName(STUB)
        true
    } catch (e: Throwable) {
        false
    }

    /** Every source but app ones, in the TV's order. Blocking (binds on first use): call off the main thread. */
    fun sources(context: Context): List<Source> = try {
        val input = inputApi(context) ?: return emptyList()
        val list = input.javaClass.getMethod("eventSystemInputSourceGetList").invoke(input) as? List<*> ?: return emptyList()
        list.mapNotNull { entity ->
            entity ?: return@mapNotNull null
            val cls = entity.javaClass
            val category = cls.getField("category").get(entity)?.toString().orEmpty().removePrefix("INPUTSOURCE_")
            if (category == "APP") return@mapNotNull null
            Source(cls.getField("id").getInt(entity), cls.getField("name").get(entity)?.toString().orEmpty(), category)
        }
    } catch (e: Throwable) {
        emptyList()
    }

    /**
     * Switches to [id] and shows it full screen, as the stock launcher does: set the source, give
     * the video window the whole screen, then open the TV player. Binder work runs on a thread.
     */
    fun show(context: Context, id: Int) {
        val app = context.applicationContext
        Thread {
            try {
                val tv = connect(app) ?: return@Thread
                val input = tv.javaClass.getMethod("getTVApiSystemInputSource").invoke(tv) ?: return@Thread
                input.javaClass.getMethod("eventSystemInputSourceSetInputSource", Int::class.javaPrimitiveType).invoke(input, id)
                tv.javaClass.getMethod("getTVApiScreenWindow").invoke(tv)?.let { window ->
                    window.javaClass.getMethod("eventScreenWindowSetFull").invoke(window)
                }
            } catch (e: Throwable) {
                // The player still opens on whatever source was last shown.
            }
            app.startActivity(playerIntent())
        }.start()
    }

    /** The CVTE TV player: it shows the current source full screen. */
    fun playerIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory("android.intent.category.TV_HOME")
        .putExtra("isLauncherGoToTv", true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun inputApi(context: Context): Any? {
        val tv = connect(context) ?: return null
        return tv.javaClass.getMethod("getTVApiSystemInputSource").invoke(tv)
    }

    @Synchronized
    private fun connect(context: Context): Any? {
        api?.let { return it }
        val wait = CountDownLatch(1)
        latch = wait
        val intent = Intent(ACTION).setPackage(PACKAGE)
        val bound = try {
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            false
        }
        if (!bound) return null
        // The connection is delivered on the main thread: waiting there would only time out.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        wait.await(CONNECT_MS, TimeUnit.MILLISECONDS)
        return api
    }
}
