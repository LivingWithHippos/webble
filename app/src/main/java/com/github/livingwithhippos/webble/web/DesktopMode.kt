package com.github.livingwithhippos.webble.web

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView

object DesktopMode {

    /**
     * A desktop Chrome user agent with the same Chrome version as the WebView: the
     * "(Linux; Android …)" part and the "Mobile" marker are what sites look at.
     */
    fun userAgent(context: Context): String {
        val default = WebSettings.getDefaultUserAgent(context)
        val chrome = Regex("""Chrome/[\d.]+""").find(default)?.value ?: "Chrome/130.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "$chrome Safari/537.36"
    }

    private const val DESKTOP_WIDTH = 1024

    /**
     * Lays the page out at desktop width and zooms out to fit, like Chrome's desktop mode.
     * Changing the user agent alone isn't enough: many desktop pages declare a fixed viewport
     * that WebView then shows at 100% zoom, overflowing the bubble.
     */
    fun fitPage(webView: WebView) {
        val cssWidth = webView.width / webView.resources.displayMetrics.density
        if (cssWidth <= 0f) return
        val scale = cssWidth / DESKTOP_WIDTH
        webView.evaluateJavascript(
            """
            (function() {
              var m = document.querySelector('meta[name="viewport"]');
              if (!m) {
                m = document.createElement('meta');
                m.name = 'viewport';
                (document.head || document.documentElement).appendChild(m);
              }
              m.setAttribute('content', 'width=$DESKTOP_WIDTH, initial-scale=$scale');
            })();
            """.trimIndent(),
            null,
        )
        // WebView ignores a new initial-scale once the page is shown: zoom out to the minimum,
        // which WebView clamps to "whole page width fits".
        webView.postDelayed({ webView.zoomBy(0.02f) }, 150)
    }
}
