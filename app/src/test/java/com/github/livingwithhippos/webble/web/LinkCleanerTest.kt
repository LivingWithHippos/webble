package com.github.livingwithhippos.webble.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/** Runs the real bundled ClearURLs rules. */
class LinkCleanerTest {

    companion object {
        private val rulesJson = File("src/main/assets/clearurls/data.min.json").readText()
        private val skipped = mutableListOf<String>()
        private val cleaner = LinkCleaner.parse(rulesJson) { skipped += it }
    }

    private fun clean(url: String) = cleaner.clean(url)

    @Test
    fun removesUtmKeepsOtherParamsAndAnchor() {
        assertEquals(
            "https://example.com/a?id=5#top",
            clean("https://example.com/a?utm_source=x&id=5&utm_medium=mail#top"),
        )
    }

    @Test
    fun removesGlobalClickIds() {
        assertEquals("https://example.com/", clean("https://example.com/?fbclid=abc"))
        assertEquals("https://example.com/p?q=1", clean("https://example.com/p?gclid=x&q=1"))
    }

    @Test
    fun removesAmazonTrackingAndAffiliateTag() {
        assertEquals(
            "https://www.amazon.com/dp/B0TEST",
            clean("https://www.amazon.com/dp/B0TEST/ref=sr_1_1?tag=foo-20&ref_=abc&qid=123"),
        )
    }

    @Test
    fun unwrapsGoogleRedirect() {
        assertEquals(
            "https://example.com/",
            clean("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2F&sa=D"),
        )
    }

    @Test
    fun leavesCleanUrlsByteIdentical() {
        val urls = listOf(
            "https://example.com/",
            "https://example.com/search?q=a%20b%26c&page=2",
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)#History",
        )
        urls.forEach { assertEquals(it, clean(it)) }
    }

    @Test
    fun respectsExceptions() {
        val url = "https://accounts.google.com/o/oauth2/auth?client_id=1&source=x"
        assertEquals(url, clean(url))
    }

    @Test
    fun cleansParameterFragments() {
        assertEquals(
            "https://example.com/#page=2",
            clean("https://example.com/#utm_source=x&page=2"),
        )
    }

    @Test
    fun returnsGarbageUnchanged() {
        listOf("", "not a url", "mailto:a@b.c?utm_source=x").forEach {
            assertEquals(it, clean(it))
        }
    }

    @Test
    fun everyRuleCompilesInJava() {
        // The rules are written for JavaScript: check java.util.regex accepts them all.
        val providers = JSONObject(rulesJson).getJSONObject("providers")
        val bad = mutableListOf<String>()
        providers.keys().forEach { name ->
            val p = providers.getJSONObject(name)
            val patterns = listOf("rules", "referralMarketing", "rawRules", "exceptions", "redirections")
                .flatMap { key ->
                    val array = p.optJSONArray(key) ?: JSONArray()
                    (0 until array.length()).map { array.getString(it) }
                } + p.getString("urlPattern")
            patterns.forEach { regex ->
                runCatching { Pattern.compile(regex, Pattern.CASE_INSENSITIVE) }
                    .onFailure { bad += "$name: $regex" }
            }
        }
        assertTrue("Not compiling: $bad", bad.isEmpty())
        assertTrue("Skipped: $skipped", skipped.isEmpty())
    }
}
