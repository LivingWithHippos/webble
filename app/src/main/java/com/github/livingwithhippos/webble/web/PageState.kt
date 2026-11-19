package com.github.livingwithhippos.webble.web

import android.graphics.Bitmap
import android.view.View
import android.webkit.WebChromeClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.livingwithhippos.webble.Feature

/** Everything the page UI shows, as Compose state. Owned by WebPageActivity. */
class PageState {
    var title by mutableStateOf("")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var favicon: Bitmap? = null

    /** Enabled features, kept in sync with FeaturePrefs. */
    var features by mutableStateOf<Set<Feature>>(emptySet())

    var desktopMode by mutableStateOf(false)

    var findVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)

    /** HTML of the reader view, or null when reader mode is off. */
    var readerHtml by mutableStateOf<String?>(null)

    /** Fullscreen video view from WebChromeClient.onShowCustomView, or null. */
    var customView by mutableStateOf<View?>(null)
    var customViewCallback: WebChromeClient.CustomViewCallback? = null

    var nativeApp by mutableStateOf<NativeApp?>(null)

    /** URL of the long-pressed link, or null when the link menu is closed. */
    var linkMenuUrl by mutableStateOf<String?>(null)

    fun has(feature: Feature) = feature in features
}
