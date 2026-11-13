package com.github.livingwithhippos.webble.web

import android.text.TextUtils
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** Builds a clean article view with Mozilla Readability (assets/readability). */
object ReaderMode {

    @Volatile
    private var readability: String? = null

    private fun script(webView: WebView): String = readability
        ?: webView.context.assets.open("readability/Readability.js")
            .bufferedReader().use { it.readText() }
            .also { readability = it }

    /**
     * Runs Readability on a copy of the page's DOM. Calls [onResult] with the reader HTML, or
     * null when the page has no article.
     */
    fun extract(webView: WebView, onResult: (String?) -> Unit) {
        // Wrapped in a function so running it twice doesn't redeclare globals on the page.
        val js = """
            (function() {
              ${script(webView)}
              try {
                var article = new Readability(document.cloneNode(true)).parse();
                return article ? JSON.stringify(article) : null;
              } catch (e) {
                return null;
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw -> onResult(parse(raw)?.let(::buildPage)) }
    }

    /** evaluateJavascript returns the value JSON-encoded: a quoted string here, or "null". */
    private fun parse(raw: String?): JSONObject? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val json = JSONArray("[$raw]").getString(0)
            JSONObject(json).takeIf { it.optString("content").isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPage(article: JSONObject): String {
        val title = TextUtils.htmlEncode(article.optString("title"))
        val meta = listOf(article.optString("byline"), article.optString("siteName"))
            .filter { it.isNotBlank() && it != "null" }
            .joinToString(" · ")
            .let(TextUtils::htmlEncode)
        val content = article.optString("content")
        return """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: light dark; }
              body {
                font-family: Georgia, serif; font-size: 19px; line-height: 1.6;
                max-width: 40em; margin: 0 auto; padding: 16px 20px 48px;
                color: #1f1f1f; background: #fbfaf7; overflow-wrap: break-word;
              }
              h1 { font-family: sans-serif; font-size: 1.5em; line-height: 1.25; margin-bottom: 4px; }
              .meta { font-family: sans-serif; color: #6b6b6b; font-size: 0.8em; margin-top: 0; }
              a { color: #1a5fb4; }
              img, video, figure, iframe, table { max-width: 100%; height: auto; }
              pre { overflow-x: auto; white-space: pre-wrap; }
              blockquote { border-left: 3px solid #ccc; margin-left: 0; padding-left: 16px; color: #555; }
              @media (prefers-color-scheme: dark) {
                body { color: #e3e3e3; background: #161616; }
                a { color: #8ab4f8; }
                .meta, blockquote { color: #a0a0a0; }
              }
            </style>
            </head>
            <body>
            <h1>$title</h1>
            ${if (meta.isNotBlank()) "<p class=\"meta\">$meta</p>" else ""}
            $content
            </body>
            </html>
        """.trimIndent()
    }
}
