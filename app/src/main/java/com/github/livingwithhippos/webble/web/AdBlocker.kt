package com.github.livingwithhippos.webble.web

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks ads and trackers with Ghostery's adblocker (assets/adblock, built by tools/adblock).
 *
 * The library is JavaScript, so it runs in a JavaScriptSandbox: a V8 isolate provided by the
 * WebView in its own process. One isolate is shared by every bubble. While it is starting, or on
 * devices without the sandbox, every request is allowed.
 */
class AdBlocker private constructor(context: Context) {

    companion object {
        private const val TAG = "AdBlocker"
        private const val ENGINE_DATA = "engine"
        private const val MATCH_TIMEOUT_MS = 200L
        private const val COSMETICS_TIMEOUT_MS = 2_000L
        private const val START_TIMEOUT_S = 30L
        private const val MAX_HEAP_BYTES = 128L * 1024 * 1024
        private const val CACHE_SIZE = 2_000
        private const val MAX_START_FAILURES = 3

        private val REQUIRED_FEATURES = listOf(
            JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN,
            JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER,
            JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT,
        )

        @Volatile
        private var instance: AdBlocker? = null

        fun get(context: Context): AdBlocker = instance ?: synchronized(this) {
            instance ?: AdBlocker(context.applicationContext).also { instance = it }
        }
    }

    private val appContext = context

    /** Starts the sandbox and computes cosmetic CSS, in order: jobs queued during start wait. */
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AdBlocker").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var sandbox: JavaScriptSandbox? = null

    @Volatile
    private var isolate: JavaScriptIsolate? = null

    private val starting = AtomicBoolean(false)

    @Volatile
    private var startFailures = 0

    @Volatile
    private var unsupported = false

    /** Recent decisions, keyed by type, page host and URL. */
    private val cache = object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
            size > CACHE_SIZE
    }

    /** Starts the sandbox in the background if it isn't running. Cheap to call repeatedly. */
    fun warmUp() {
        if (isolate != null || unsupported || startFailures >= MAX_START_FAILURES) return
        if (starting.compareAndSet(false, true)) worker.execute { start() }
    }

    private fun start() {
        var box: JavaScriptSandbox? = null
        var iso: JavaScriptIsolate? = null
        try {
            if (isolate != null) return
            if (!JavaScriptSandbox.isSupported()) {
                unsupported = true
                Log.i(TAG, "JavaScriptSandbox not supported: ad blocking off")
                return
            }
            box = JavaScriptSandbox.createConnectedInstanceAsync(appContext)
                .get(START_TIMEOUT_S, TimeUnit.SECONDS)
            val missing = REQUIRED_FEATURES.filterNot(box::isFeatureSupported)
            if (missing.isNotEmpty()) {
                unsupported = true
                Log.i(TAG, "JavaScriptSandbox lacks $missing: ad blocking off")
                box.close()
                return
            }
            val params = IsolateStartupParameters()
            if (box.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                params.maxHeapSizeBytes = MAX_HEAP_BYTES
            }
            val created = box.createIsolate(params)
            iso = created
            if (box.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION)) {
                created.addOnTerminatedCallback(worker) { info ->
                    Log.w(TAG, "Isolate terminated: $info")
                    reset(created)
                }
            }

            val startedAt = System.currentTimeMillis()
            val script = appContext.assets.open("adblock/adblocker.js")
                .bufferedReader().use { it.readText() }
            iso.evaluateJavaScriptAsync(script).get(START_TIMEOUT_S, TimeUnit.SECONDS)
            val engine = appContext.assets.open("adblock/engine.bin").use { it.readBytes() }
            iso.provideNamedData(ENGINE_DATA, engine)
            iso.evaluateJavaScriptAsync(
                "android.consumeNamedDataAsArrayBuffer('$ENGINE_DATA').then(b => webble.load(b))",
            ).get(START_TIMEOUT_S, TimeUnit.SECONDS)

            sandbox = box
            isolate = iso
            startFailures = 0
            Log.d(TAG, "Ready in ${System.currentTimeMillis() - startedAt} ms")
        } catch (e: Exception) {
            startFailures++
            Log.w(TAG, "Could not start ad blocker", e)
            runCatching { iso?.close() }
            runCatching { box?.close() }
        } finally {
            starting.set(false)
        }
    }

    /** Drops a dead isolate (and its sandbox) so the next request starts a new one. */
    private fun reset(dead: JavaScriptIsolate?) {
        synchronized(this) {
            if (dead != null && isolate !== dead) return
            val box = sandbox
            isolate = null
            sandbox = null
            runCatching { dead?.close() }
            runCatching { box?.close() }
        }
    }

    /** Runs [js] in the isolate, or returns null when it isn't ready or doesn't answer in time. */
    private fun evaluate(js: String, timeoutMs: Long): String? {
        val iso = isolate ?: run {
            warmUp()
            return null
        }
        val future = iso.evaluateJavaScriptAsync(js)
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: Exception) {
            // Usually the sandbox process died: start a new one for later requests.
            Log.w(TAG, "Evaluation failed", e)
            reset(iso)
            warmUp()
            null
        }
    }

    /**
     * Returns the response to give the WebView instead of [request] when it's an ad or tracker
     * loaded by the page at [pageUrl], or null to load it normally.
     *
     * Blocks the calling thread: call it from WebViewClient.shouldInterceptRequest only.
     */
    fun intercept(request: WebResourceRequest, pageUrl: String): WebResourceResponse? {
        if (request.isForMainFrame || pageUrl.isEmpty()) return null
        val url = request.url
        if (url.scheme != "http" && url.scheme != "https") return null

        val type = requestType(request)
        val urlString = url.toString()
        val key = "$type|${Uri.parse(pageUrl).host}|$urlString"
        val decision = synchronized(cache) { cache[key] }
            ?: evaluate(
                "webble.match(${quote(urlString)},${quote(pageUrl)},${quote(type)})",
                MATCH_TIMEOUT_MS,
            )?.also { synchronized(cache) { cache[key] = it } }
            ?: return null

        // Enable with: adb shell setprop log.tag.AdBlocker VERBOSE
        if (decision != "allow" && Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "${decision.substringBefore('\n')} $type $urlString")
        }
        return when {
            decision == "block" -> blockedResponse()
            decision.startsWith("redirect:") -> redirectResponse(decision.removePrefix("redirect:"))
            else -> null
        }
    }

    /**
     * Computes the CSS hiding ad slots on [pageUrl] in the background, then calls [onResult] on
     * the main thread. Not called when there's nothing to hide.
     */
    fun cosmeticCss(pageUrl: String, onResult: (String) -> Unit) {
        warmUp()
        worker.execute {
            val css = evaluate("webble.cosmetics(${quote(pageUrl)})", COSMETICS_TIMEOUT_MS)
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "css ${css?.length} for $pageUrl")
            if (!css.isNullOrBlank()) mainHandler.post { onResult(css) }
        }
    }

    /** WebView doesn't tell what a request is for: guess it like the filter lists expect. */
    private fun requestType(request: WebResourceRequest): String {
        val accept = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        val path = request.url.path.orEmpty().lowercase()
        val extension = path.substringAfterLast('.', "")
        return when {
            accept.contains("text/css") || extension == "css" -> "stylesheet"
            accept.startsWith("image/") ||
                extension in setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico") ->
                "image"

            extension == "js" || extension == "mjs" -> "script"
            extension in setOf("woff", "woff2", "ttf", "otf", "eot") -> "font"
            extension in setOf("mp4", "webm", "m3u8", "mp3", "ogg") -> "media"
            accept.startsWith("text/html") -> "sub_frame"
            else -> "xmlhttprequest"
        }
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )

    /** Serves a filter list's harmless replacement resource ("<content type>\n<body>"). */
    private fun redirectResponse(redirect: String): WebResourceResponse {
        val contentType = redirect.substringBefore('\n')
        val body = redirect.substringAfter('\n', "")
        val base64 = contentType.endsWith(";base64")
        val mimeType = contentType.substringBefore(';').ifEmpty { "text/plain" }
        val bytes = if (base64) {
            runCatching { Base64.decode(body, Base64.DEFAULT) }.getOrNull()
                ?: return blockedResponse()
        } else {
            body.toByteArray()
        }
        return WebResourceResponse(
            mimeType,
            "utf-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(bytes),
        )
    }

    /** A JS string literal. */
    private fun quote(value: String): String = JSONObject.quote(value)
}
