package com.github.livingwithhippos.webble

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.github.livingwithhippos.webble.ui.PageActions
import com.github.livingwithhippos.webble.ui.WebPageScreen
import com.github.livingwithhippos.webble.ui.theme.WebbleTheme
import com.github.livingwithhippos.webble.web.DesktopMode
import com.github.livingwithhippos.webble.web.Downloads
import com.github.livingwithhippos.webble.web.Favicons
import com.github.livingwithhippos.webble.web.NativeApps
import com.github.livingwithhippos.webble.web.PageCallbacks
import com.github.livingwithhippos.webble.web.PageState
import com.github.livingwithhippos.webble.web.ReaderMode
import com.github.livingwithhippos.webble.web.AdBlocker
import com.github.livingwithhippos.webble.web.ClearUrls
import com.github.livingwithhippos.webble.web.createPageWebView
import com.github.livingwithhippos.webble.web.setAlgorithmicDarkening
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows a web page. Runs inside the expanded bubble, or full screen as a fallback. */
class WebPageActivity : ComponentActivity(), PageCallbacks, PageActions {

    companion object {
        const val EXTRA_URL = "com.github.livingwithhippos.webble.EXTRA_URL"
        const val EXTRA_ID = "com.github.livingwithhippos.webble.EXTRA_ID"
        const val EXTRA_IN_BUBBLE = "com.github.livingwithhippos.webble.EXTRA_IN_BUBBLE"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_URL = "page_url"

        fun fullScreenIntent(context: Context, url: Uri, id: Int = -1): Intent =
            Intent(context, WebPageActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(url)
                .putExtra(EXTRA_ID, id)
    }

    private val state = PageState()

    private lateinit var webView: WebView
    private var readerView: WebView? = null
    private lateinit var notifier: BubbleNotifier
    private lateinit var featurePrefs: FeaturePrefs
    private lateinit var history: HistoryStore

    private var rendererGone = false
    private var linkId = -1
    private var inBubble = false

    private var updateJob: Job? = null
    private var nativeAppJob: Job? = null
    private var iconJob: Job? = null

    /** The site's icon from Favicons (host to icon): better than WebView's onReceivedIcon. */
    private var siteIcon: Pair<String, Bitmap>? = null

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
            fileCallback = null
        }

    /** Page history. Fullscreen, find and reader mode have their own BackHandlers in the UI. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) webView.goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notifier = BubbleNotifier(this)
        featurePrefs = FeaturePrefs.get(this)
        history = HistoryStore.get(this)
        state.features = featurePrefs.enabled.value

        if (state.has(Feature.AD_BLOCK)) AdBlocker.get(this).warmUp()
        webView = createPageWebView(this, state, this)
        onBackPressedDispatcher.addCallback(this, backCallback)

        readIntent(intent)
        val savedUrl = savedInstanceState?.getString(KEY_URL)
        if (savedUrl != null) state.url = savedUrl
        state.desktopMode = savedInstanceState?.getBoolean(KEY_DESKTOP_MODE) == true &&
            state.has(Feature.DESKTOP_SITE)
        applyDesktopMode(reload = false)
        applyDarkening()
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            if (state.url.isNotEmpty()) webView.loadUrl(state.url)
        }

        lifecycleScope.launch {
            featurePrefs.enabled.collect { onFeaturesChanged(it) }
        }

        setContent {
            WebbleTheme {
                WebPageScreen(
                    state = state,
                    webView = webView,
                    readerWebView = ::readerWebView,
                    actions = this,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        if (state.url.isNotEmpty()) webView.loadUrl(state.url)
    }

    private fun readIntent(intent: Intent) {
        linkId = intent.getIntExtra(EXTRA_ID, -1)
        inBubble = intent.getBooleanExtra(EXTRA_IN_BUBBLE, false)
        val url = intent.getStringExtra(EXTRA_URL)
            ?: intent.data?.takeIf { LinkUtils.isWebUri(it) }?.toString()
        if (url != null) state.url = url
    }

    // region Feature switches

    /** Applies switches changed in the setup screen while this page is open. */
    private fun onFeaturesChanged(enabled: Set<Feature>) {
        val previous = state.features
        state.features = enabled
        if ((Feature.DARK_PAGES in enabled) != (Feature.DARK_PAGES in previous)) applyDarkening()
        if (Feature.DESKTOP_SITE !in enabled && state.desktopMode) {
            state.desktopMode = false
            applyDesktopMode(reload = true)
        }
        if (Feature.FIND_IN_PAGE !in enabled && state.findVisible) closeFind()
        if (Feature.READER_MODE !in enabled) state.readerHtml = null
        if (Feature.AD_BLOCK in enabled) AdBlocker.get(this).warmUp()
        if (Feature.FULLSCREEN_VIDEO !in enabled && state.customView != null) exitFullscreen()
        if (Feature.LINK_MENU !in enabled) state.linkMenuUrl = null
        if (Feature.NATIVE_APP !in enabled) {
            nativeAppJob?.cancel()
            state.nativeApp = null
        } else if (Feature.NATIVE_APP !in previous) {
            lookUpNativeApp()
        }
    }

    private fun applyDarkening() {
        val allowed = state.has(Feature.DARK_PAGES)
        setAlgorithmicDarkening(webView, allowed)
        readerView?.let { setAlgorithmicDarkening(it, allowed) }
    }

    private fun applyDesktopMode(reload: Boolean) {
        webView.settings.userAgentString =
            if (state.desktopMode) DesktopMode.userAgent(this) else null
        if (reload) webView.reload()
    }

    // endregion

    // region PageCallbacks (WebView events)

    override fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        if (!state.has(Feature.FILE_UPLOADS)) return false
        // A previous chooser that never returned must still be completed.
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        try {
            fileChooser.launch(params.createIntent())
        } catch (_: ActivityNotFoundException) {
            fileCallback = null
            callback.onReceiveValue(null)
        }
        return true
    }

    override fun onDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        if (state.has(Feature.DOWNLOADS)) {
            Downloads.enqueue(this, url, userAgent, contentDisposition, mimeType)
        } else {
            Toast.makeText(this, R.string.downloads_off, Toast.LENGTH_SHORT).show()
        }
        // The link itself was the download (no page ever loaded): don't leave an empty bubble.
        if (webView.copyBackForwardList().size == 0) close()
    }

    override fun onOpenExternally(uri: Uri) {
        try {
            if (uri.scheme == "intent") {
                val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    // Only allow what a browser would: no explicit components.
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    intent.getStringExtra("browser_fallback_url")
                        ?.takeIf { LinkUtils.isWebUri(it.toUri()) }
                        ?.let { webView.loadUrl(it) }
                        ?: toast(R.string.no_app_for_link)
                }
            } else {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
                )
            }
        } catch (_: Exception) {
            toast(R.string.no_app_for_link)
        }
    }

    override fun onUrlChanged() {
        backCallback.isEnabled = webView.canGoBack()
        lookUpNativeApp()
    }

    /** Title and favicon arrive in bursts: wait a little, then update history and bubble once. */
    override fun onPageInfoChanged() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            delay(700)
            if (state.has(Feature.RECENT_PAGES)) history.add(state.url, state.title)
            if (inBubble && linkId != -1) {
                val host = state.url.toUri().host
                val icon = siteIcon?.takeIf { it.first == host }?.second ?: state.favicon
                notifier.updateBubble(linkId, state.url, state.title, icon)
            }
        }
    }

    /** Looks for a better bubble icon than WebView's, once per site. */
    override fun onPageFinished(url: String) {
        if (!inBubble || linkId == -1) return
        val host = url.toUri().host ?: return
        if (siteIcon?.first == host || iconJob?.isActive == true) return
        iconJob = lifecycleScope.launch {
            val icon = Favicons.find(webView, url) ?: return@launch
            if (state.url.toUri().host != host) return@launch
            siteIcon = host to icon
            onPageInfoChanged()
        }
    }

    override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (!state.has(Feature.FULLSCREEN_VIDEO) || state.customView != null) {
            callback.onCustomViewHidden()
            return
        }
        state.customView = view
        state.customViewCallback = callback
        insetsController().apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** The page left fullscreen by itself. */
    override fun onHideFullscreen() {
        state.customViewCallback = null
        exitFullscreen()
    }

    /** Rebuild the activity, which creates a fresh WebView and reloads the page. */
    override fun onRendererGone() {
        rendererGone = true
        recreate()
    }

    private fun insetsController() = WindowCompat.getInsetsController(window, window.decorView)

    // endregion

    private fun lookUpNativeApp() {
        nativeAppJob?.cancel()
        if (!state.has(Feature.NATIVE_APP)) return
        val url = state.url
        nativeAppJob = lifecycleScope.launch {
            delay(300)
            state.nativeApp = withContext(Dispatchers.Default) {
                NativeApps.find(applicationContext, url)
            }
        }
    }

    private fun readerWebView(): WebView = readerView ?: WebView(this).apply {
        // Article HTML only, no scripts.
        settings.javaScriptEnabled = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        setAlgorithmicDarkening(this, state.has(Feature.DARK_PAGES))
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                // A link in the article: leave reader mode and open it in the page.
                state.readerHtml = null
                if (LinkUtils.isWebUri(request.url)) {
                    webView.loadUrl(request.url.toString())
                } else {
                    onOpenExternally(request.url)
                }
                return true
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                // Same renderer as the page in practice; the page handler recreates the activity.
                state.readerHtml = null
                return true
            }
        }
    }.also { readerView = it }

    private fun toast(text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Webble's own components, excluded from choosers so links don't loop back to Bubbol. */
    private fun ownComponents() = arrayOf(
        ComponentName(this, LinkReceiverActivity::class.java),
    )

    // region PageActions (UI)

    override fun reload() = webView.reload()

    override fun close() {
        if (linkId != -1) notifier.removeBubble(linkId)
        finish()
    }

    override fun closeAll() {
        notifier.removeAll()
        finish()
    }

    override fun share(url: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, ClearUrls.clean(this, url))
        if (url == state.url) send.putExtra(Intent.EXTRA_SUBJECT, state.title)
        startActivity(
            Intent.createChooser(send, getString(R.string.share_with))
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents()),
        )
    }

    override fun copyLink(url: String) {
        getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("URL", ClearUrls.clean(this, url)))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(R.string.link_copied)
    }

    override fun openInOtherBrowser(url: String) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        startActivity(
            Intent.createChooser(view, getString(R.string.open_with))
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents()),
        )
    }

    override fun openInNewBubble(url: String) {
        LinkUtils.openLink(this, url.toUri(), expand = false)
    }

    override fun openNativeApp() {
        val app = state.nativeApp ?: return
        try {
            startActivity(NativeApps.launchIntent(app, state.url))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.no_app_for_link)
        }
    }

    override fun toggleDesktop() {
        state.desktopMode = !state.desktopMode
        applyDesktopMode(reload = true)
    }

    override fun toggleReader() {
        if (state.readerHtml != null) {
            state.readerHtml = null
            return
        }
        ReaderMode.extract(webView) { html ->
            if (html == null) {
                toast(R.string.reader_unavailable)
            } else {
                readerWebView().loadDataWithBaseURL(state.url, html, "text/html", "utf-8", null)
                state.readerHtml = html
            }
        }
    }

    override fun exitReader() {
        state.readerHtml = null
    }

    override fun showFind() {
        state.findVisible = true
    }

    override fun closeFind() {
        webView.clearMatches()
        state.findVisible = false
        state.findQuery = ""
        state.findCurrent = 0
        state.findTotal = 0
    }

    override fun find(query: String) {
        state.findQuery = query
        if (query.isEmpty()) {
            webView.clearMatches()
            state.findCurrent = 0
            state.findTotal = 0
        } else {
            webView.findAllAsync(query)
        }
    }

    override fun findNext(forward: Boolean) = webView.findNext(forward)

    override fun exitFullscreen() {
        val callback = state.customViewCallback
        state.customView = null
        state.customViewCallback = null
        insetsController().show(WindowInsetsCompat.Type.systemBars())
        callback?.onCustomViewHidden()
    }

    override fun dismissLinkMenu() {
        state.linkMenuUrl = null
    }

    // endregion

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_DESKTOP_MODE, state.desktopMode)
        outState.putString(KEY_URL, state.url)
        // A dead WebView can't save its history: the new one just reloads the current URL.
        if (!rendererGone) webView.saveState(outState)
    }

    override fun onDestroy() {
        updateJob?.cancel()
        nativeAppJob?.cancel()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        state.customViewCallback?.onCustomViewHidden()
        readerView?.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
