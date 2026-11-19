package com.github.livingwithhippos.webble.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import android.webkit.WebView
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Finds a good icon for a site: the largest icon its page declares (preferring apple-touch-icon,
 * which is big and opaque), or /favicon.ico. WebView's own onReceivedIcon is often missing or
 * only 16px, too small for a bubble.
 */
object Favicons {

    private const val TAG = "Favicons"
    private const val TIMEOUT_MS = 5_000
    private const val MAX_BYTES = 1024 * 1024
    private const val GOOD_SIZE = 48
    private const val MAX_SIZE = 192

    /** Icons by host, shared by every bubble. */
    private val cache = LruCache<String, Bitmap>(32)

    /** Hosts where no icon could be loaded, so they aren't retried on every page. */
    private val missing = LruCache<String, Boolean>(64)

    /** Lists the page's icon links as [{href, rel, sizes, type}]. */
    private val ICON_JS = """
        (function() {
          var out = [];
          document.querySelectorAll('link[rel][href]').forEach(function(l) {
            var rel = l.rel.toLowerCase().split(/\s+/);
            if (rel.indexOf('icon') < 0 && rel.indexOf('apple-touch-icon') < 0 &&
                rel.indexOf('apple-touch-icon-precomposed') < 0) return;
            out.push({href: l.href, rel: rel.join(' '), sizes: l.getAttribute('sizes') || '',
                      type: l.type || ''});
          });
          return out;
        })();
    """.trimIndent()

    private data class Candidate(val url: String, val score: Int)

    fun cached(host: String): Bitmap? = cache.get(host)

    /** The best icon for the page shown in [webView], or null when none could be loaded. */
    suspend fun find(webView: WebView, pageUrl: String): Bitmap? {
        val host = pageUrl.toUri().host ?: return null
        cache.get(host)?.let { return it }
        if (missing.get(host) == true) return null

        val links = suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(ICON_JS) { cont.resume(it) }
        }
        val userAgent = webView.settings.userAgentString
        val icon = withContext(Dispatchers.IO) { load(candidates(links, pageUrl), userAgent) }
        if (icon == null) missing.put(host, true) else cache.put(host, icon)
        return icon
    }

    /** Candidate URLs, best first, ending with /favicon.ico. */
    private fun candidates(links: String?, pageUrl: String): List<String> {
        val declared = runCatching { JSONArray(links) }.getOrNull()?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val link = array.optJSONObject(i) ?: return@mapNotNull null
                val href = link.optString("href")
                val type = link.optString("type")
                if (!href.startsWith("http")) return@mapNotNull null
                // BitmapFactory can't decode SVG.
                if (type.contains("svg") || href.substringBefore('?').endsWith(".svg")) {
                    return@mapNotNull null
                }
                val size = Regex("""(\d+)x\d+""").findAll(link.optString("sizes"))
                    .maxOfOrNull { it.groupValues[1].toInt() } ?: 32
                val appleBonus = if (link.optString("rel").contains("apple-touch-icon")) 1000 else 0
                Candidate(href, appleBonus + size)
            }
        }.orEmpty()

        val uri = pageUrl.toUri()
        val fallback = "${uri.scheme}://${uri.host}/favicon.ico"
        return (declared.sortedByDescending { it.score }.map { it.url } + fallback).distinct()
    }

    /** The first candidate at least [GOOD_SIZE] wide, else the largest one that loaded. */
    private fun load(candidates: List<String>, userAgent: String): Bitmap? {
        var best: Bitmap? = null
        for (url in candidates.take(5)) {
            val bitmap = download(url, userAgent) ?: continue
            if (best == null || bitmap.width > best.width) best = bitmap
            if (bitmap.width >= GOOD_SIZE) break
        }
        best ?: return null
        Log.d(TAG, "Icon ${best.width}x${best.height} for ${candidates.first()}")
        return if (best.width > MAX_SIZE || best.height > MAX_SIZE) {
            val ratio = MAX_SIZE.toFloat() / maxOf(best.width, best.height)
            best.scale((best.width * ratio).toInt(), (best.height * ratio).toInt())
        } else {
            best
        }
    }

    private fun download(url: String, userAgent: String): Bitmap? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val bytes = connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > MAX_BYTES) return null
                    }
                    out.toByteArray()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.d(TAG, "No icon at $url: $e")
        null
    }
}
