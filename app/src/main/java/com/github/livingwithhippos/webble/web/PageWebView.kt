package com.github.livingwithhippos.webble.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.github.livingwithhippos.webble.Feature
import com.github.livingwithhippos.webble.LinkUtils

/** What the page WebView needs from its activity. */
interface PageCallbacks {
    /** Return false when uploads are off; then [callback] must not be used. */
    fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean

    fun onDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String)
    fun onOpenExternally(uri: Uri)
    fun onUrlChanged()
    fun onPageInfoChanged()
    fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onHideFullscreen()

    /** The page's renderer crashed or was killed: this WebView can't be used anymore. */
    fun onRendererGone()
}

fun setAlgorithmicDarkening(webView: WebView, allowed: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, allowed)
    }
}

/** Creates the main page WebView and wires its events into [state] and [callbacks]. */
@SuppressLint("SetJavaScriptEnabled")
fun createPageWebView(context: Context, state: PageState, callbacks: PageCallbacks): WebView =
    WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val uri = request.url
                // Web links stay in the bubble, everything else goes to the system.
                if (LinkUtils.isWebUri(uri)) return false
                callbacks.onOpenExternally(uri)
                return true
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (state.desktopMode) DesktopMode.fitPage(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Again at the end, in case the page replaced its viewport while loading.
                if (state.desktopMode) DesktopMode.fitPage(view)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                state.url = url
                callbacks.onUrlChanged()
            }

            // Without this, a renderer crash or OOM kill takes down the whole app (every bubble).
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                callbacks.onRendererGone()
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                state.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                state.title = title.orEmpty()
                callbacks.onPageInfoChanged()
            }

            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                state.favicon = icon
                callbacks.onPageInfoChanged()
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean = callbacks.onShowFileChooser(filePathCallback, fileChooserParams)

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                callbacks.onShowFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                callbacks.onHideFullscreen()
            }
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            callbacks.onDownload(url, userAgent, contentDisposition, mimeType)
        }

        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            state.findTotal = numberOfMatches
            state.findCurrent = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
        }

        // Long-press on a link opens Webble's link menu.
        val hrefHandler = Handler(Looper.getMainLooper()) { message ->
            message.data.getString("url")
                ?.takeIf { LinkUtils.isWebUri(it.toUri()) }
                ?.let { state.linkMenuUrl = it }
            true
        }
        setOnLongClickListener {
            if (!state.has(Feature.LINK_MENU)) return@setOnLongClickListener false
            val hit = hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    val url = hit.extra?.takeIf { LinkUtils.isWebUri(it.toUri()) }
                        ?: return@setOnLongClickListener false
                    state.linkMenuUrl = url
                    true
                }

                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // For a linked image, extra is the image URL: ask for the link's href.
                    requestFocusNodeHref(hrefHandler.obtainMessage())
                    true
                }

                else -> false
            }
        }
    }
