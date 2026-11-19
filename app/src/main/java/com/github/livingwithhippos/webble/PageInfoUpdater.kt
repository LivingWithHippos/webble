package com.github.livingwithhippos.webble

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.core.net.toUri
import com.github.livingwithhippos.webble.web.Favicons
import com.github.livingwithhippos.webble.web.PageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the recent pages list and the bubble's title and icon in sync with the page. Has its own
 * scope so it works both for an open page and for one preloaded behind a collapsed bubble.
 */
class PageInfoUpdater(
    context: Context,
    private val state: PageState,
    private val linkId: Int,
    private val inBubble: Boolean,
) {
    private val notifier = BubbleNotifier(context)
    private val history = HistoryStore.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var updateJob: Job? = null
    private var iconJob: Job? = null

    /** The site's icon from Favicons (host to icon): better than WebView's onReceivedIcon. */
    private var siteIcon: Pair<String, Bitmap>? = null

    /** Title and favicon arrive in bursts: wait a little, then update history and bubble once. */
    fun onPageInfoChanged() {
        updateJob?.cancel()
        updateJob = scope.launch {
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
    fun onPageFinished(webView: WebView, url: String) {
        if (!inBubble || linkId == -1) return
        val host = url.toUri().host ?: return
        if (siteIcon?.first == host || iconJob?.isActive == true) return
        iconJob = scope.launch {
            val icon = Favicons.find(webView, url) ?: return@launch
            if (state.url.toUri().host != host) return@launch
            siteIcon = host to icon
            onPageInfoChanged()
        }
    }

    fun cancel() = scope.cancel()
}
