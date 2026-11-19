package com.github.livingwithhippos.webble.web

import android.content.Context
import android.content.MutableContextWrapper
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.github.livingwithhippos.webble.Feature
import com.github.livingwithhippos.webble.FeaturePrefs
import com.github.livingwithhippos.webble.PageInfoUpdater

/**
 * A page loading behind a collapsed bubble. The bubble's activity only exists once the bubble is
 * expanded, so the WebView is created on the application context and handed over to
 * WebPageActivity when the bubble is opened.
 */
class PreloadedPage internal constructor(
    val id: Int,
    val webView: WebView,
    val state: PageState,
    val callbacks: SwitchableCallbacks,
    val infoUpdater: PageInfoUpdater,
) {
    /** Moves the page to [activity]: dialogs and pickers now use it, and so do the callbacks. */
    fun attach(activity: Context, target: PageCallbacks) {
        (webView.context as? MutableContextWrapper)?.baseContext = activity
        callbacks.target = target
    }

    internal fun destroy() {
        infoUpdater.cancel()
        callbacks.target = null
        webView.stopLoading()
        webView.destroy()
    }
}

/**
 * Forwards the WebView's events to [target], the activity showing the page. While there is none
 * (the page is loading in the background) only the bubble and history are kept up to date: a
 * background page can't open apps, start downloads, pick files or go fullscreen.
 */
class SwitchableCallbacks internal constructor(
    private val onHeadlessRendererGone: () -> Unit,
) : PageCallbacks {
    var target: PageCallbacks? = null
    internal lateinit var webView: WebView
    internal lateinit var infoUpdater: PageInfoUpdater

    override fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean = target?.onShowFileChooser(callback, params) ?: false

    override fun onDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        target?.onDownload(url, userAgent, contentDisposition, mimeType)
    }

    override fun onOpenExternally(uri: Uri) {
        target?.onOpenExternally(uri)
    }

    override fun onUrlChanged() {
        target?.onUrlChanged()
    }

    override fun onPageInfoChanged() {
        target?.onPageInfoChanged() ?: infoUpdater.onPageInfoChanged()
    }

    override fun onPageFinished(url: String) {
        target?.onPageFinished(url) ?: infoUpdater.onPageFinished(webView, url)
    }

    override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        target?.onShowFullscreen(view, callback) ?: callback.onCustomViewHidden()
    }

    override fun onHideFullscreen() {
        target?.onHideFullscreen()
    }

    override fun onRendererGone() {
        target?.onRendererGone() ?: onHeadlessRendererGone()
    }
}

/** Loads pages for collapsed bubbles in the background, so they are ready when opened. */
object PagePreloader {

    /** Each preloaded page is a live WebView: keep only the most recent few. */
    private const val MAX_PAGES = 3

    /** Oldest first. Only touched from the main thread. */
    private val pages = LinkedHashMap<Int, PreloadedPage>()

    fun preload(context: Context, id: Int, url: String) {
        discard(id)
        val appContext = context.applicationContext
        val features = FeaturePrefs.get(appContext).enabled.value
        val state = PageState().apply {
            this.features = features
            this.url = url
        }
        if (Feature.AD_BLOCK in features) AdBlocker.get(appContext).warmUp()

        val callbacks = SwitchableCallbacks(onHeadlessRendererGone = { discard(id) })
        val webView = createPageWebView(MutableContextWrapper(appContext), state, callbacks)
        val infoUpdater = PageInfoUpdater(appContext, state, id, inBubble = true)
        callbacks.webView = webView
        callbacks.infoUpdater = infoUpdater
        setAlgorithmicDarkening(webView, Feature.DARK_PAGES in features)

        pages[id] = PreloadedPage(id, webView, state, callbacks, infoUpdater)
        while (pages.size > MAX_PAGES) discard(pages.keys.first())
        webView.loadUrl(url)
    }

    /** Removes the preloaded page for bubble [id] so an activity can show it, if there is one. */
    fun take(id: Int): PreloadedPage? = pages.remove(id)

    fun discard(id: Int) {
        pages.remove(id)?.destroy()
    }

    fun clear() {
        val all = pages.values.toList()
        pages.clear()
        all.forEach { it.destroy() }
    }
}
