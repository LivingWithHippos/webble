package com.github.livingwithhippos.webble.web

import android.content.Context
import android.util.Log
import com.github.livingwithhippos.webble.Feature
import com.github.livingwithhippos.webble.FeaturePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Removes tracking and referral parameters from links, and unwraps redirect links such as
 * google.com/url?q=…, using the ClearURLs rules (assets/clearurls, see tools/clearurls).
 *
 * Plain Kotlin on strings (no android.net.Uri) so it can be unit tested on the JVM.
 */
class LinkCleaner private constructor(private val providers: List<Provider>) {

    companion object {
        private const val MAX_REDIRECTS = 5
        private val WEB_URL = Regex("^https?://", RegexOption.IGNORE_CASE)

        /** Builds a cleaner from the ClearURLs `data.min.json`. [onSkipped] gets bad patterns. */
        fun parse(json: String, onSkipped: (String) -> Unit = {}): LinkCleaner {
            val map = JSONObject(json).getJSONObject("providers")
            val providers = map.keys().asSequence().mapNotNull { name ->
                val p = map.getJSONObject(name)
                val urlPattern = compile(p.optString("urlPattern"), onSkipped)
                    ?: return@mapNotNull null
                Provider(
                    urlPattern = urlPattern,
                    rules = p.strings("rules") + p.strings("referralMarketing"),
                    rawRules = p.strings("rawRules"),
                    exceptions = p.strings("exceptions"),
                    redirections = p.strings("redirections"),
                    onSkipped = onSkipped,
                )
            }.toList()
            return LinkCleaner(providers)
        }

        private fun JSONObject.strings(key: String): List<String> {
            val array: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.getString(it) }
        }

        private fun compile(regex: String, onSkipped: (String) -> Unit): Pattern? = try {
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        } catch (_: Exception) {
            onSkipped(regex)
            null
        }

        private fun decode(value: String): String =
            runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    /** One site's rules. Everything but [urlPattern] is compiled on the first matching link. */
    private class Provider(
        val urlPattern: Pattern,
        rules: List<String>,
        rawRules: List<String>,
        exceptions: List<String>,
        redirections: List<String>,
        onSkipped: (String) -> Unit,
    ) {
        val rules by lazy { rules.mapNotNull { compile("^(?:$it)$", onSkipped) } }
        val rawRules by lazy { rawRules.mapNotNull { compile(it, onSkipped) } }
        val exceptions by lazy { exceptions.mapNotNull { compile(it, onSkipped) } }
        val redirections by lazy { redirections.mapNotNull { compile(it, onSkipped) } }

        fun appliesTo(url: String) = urlPattern.matcher(url).find() &&
            exceptions.none { it.matcher(url).find() }

        fun isTracking(key: String) = rules.any {
            it.matcher(key).matches() || it.matcher(decode(key)).matches()
        }
    }

    /** [url] without tracking parameters, or [url] itself when there's nothing to remove. */
    fun clean(url: String): String = try {
        var current = url
        var redirects = 0
        var restart = true
        while (restart) {
            restart = false
            for (provider in providers) {
                if (!provider.appliesTo(current)) continue
                val target = redirectTarget(provider, current)
                if (target != null && redirects < MAX_REDIRECTS) {
                    redirects++
                    current = target
                    restart = true
                    break
                }
                current = removeParams(provider, current)
            }
        }
        if (WEB_URL.containsMatchIn(current)) current else url
    } catch (_: Exception) {
        url
    }

    private fun redirectTarget(provider: Provider, url: String): String? {
        for (redirection in provider.redirections) {
            val matcher = redirection.matcher(url)
            if (matcher.find() && matcher.groupCount() >= 1) {
                val target = matcher.group(1)?.let(::decode) ?: continue
                if (WEB_URL.containsMatchIn(target)) return target
            }
        }
        return null
    }

    private fun removeParams(provider: Provider, url: String): String {
        var result = url
        for (raw in provider.rawRules) result = raw.matcher(result).replaceAll("")

        val hashAt = result.indexOf('#')
        val beforeHash = if (hashAt < 0) result else result.substring(0, hashAt)
        val fragment = if (hashAt < 0) null else result.substring(hashAt + 1)
        val queryAt = beforeHash.indexOf('?')
        val base = if (queryAt < 0) beforeHash else beforeHash.substring(0, queryAt)
        val query = if (queryAt < 0) null else beforeHash.substring(queryAt + 1)

        val cleanQuery = query?.let { filterParams(provider, it) }
        // Only fragments that look like parameters; "#section" is left alone.
        val cleanFragment = if (fragment != null && '=' in fragment) {
            filterParams(provider, fragment)
        } else {
            fragment
        }

        return buildString {
            append(base)
            if (!cleanQuery.isNullOrEmpty()) append('?').append(cleanQuery)
            if (!cleanFragment.isNullOrEmpty()) append('#').append(cleanFragment)
        }
    }

    /** Drops the tracking pairs of an `a=1&b=2` string, keeping the others byte for byte. */
    private fun filterParams(provider: Provider, params: String): String =
        params.split('&')
            .filter { it.isNotEmpty() && !provider.isTracking(it.substringBefore('=')) }
            .joinToString("&")
}

/** The app-wide [LinkCleaner], loaded from the assets on first use. */
object ClearUrls {

    private const val TAG = "ClearUrls"

    @Volatile
    private var cleaner: LinkCleaner? = null

    @Volatile
    private var failed = false

    private fun get(context: Context): LinkCleaner? {
        cleaner?.let { return it }
        if (failed) return null
        return synchronized(this) {
            cleaner ?: try {
                val json = context.assets.open("clearurls/data.min.json")
                    .bufferedReader().use { it.readText() }
                LinkCleaner.parse(json) { Log.w(TAG, "Skipped rule: $it") }.also { cleaner = it }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load the rules", e)
                failed = true
                null
            }
        }
    }

    /** [url] without tracking parameters, or unchanged when "Clean links" is off. */
    fun clean(context: Context, url: String): String {
        if (!FeaturePrefs.get(context).isEnabled(Feature.CLEAN_LINKS)) return url
        val cleaned = get(context)?.clean(url) ?: return url
        if (cleaned != url) Log.d(TAG, "Cleaned $url -> $cleaned")
        return cleaned
    }
}
