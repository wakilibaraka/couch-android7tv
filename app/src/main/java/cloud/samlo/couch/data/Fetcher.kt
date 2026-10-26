package cloud.samlo.couch.data

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Opens http(s) URLs and content URIs alike. Blocking: call from a worker thread. */
open class Fetcher(private val context: Context? = null) {
    fun open(url: String): InputStream = when (Uri.parse(url).scheme) {
        "http", "https" -> {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.inputStream
        }
        else -> context?.contentResolver?.openInputStream(Uri.parse(url))
            ?: throw IllegalStateException("No stream for $url")
    }

    /** The body as text, or null on any failure: callers fall back to their cache. */
    open fun text(url: String): String? = try {
        open(url).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TIMEOUT_MS = 8000
    }
}
