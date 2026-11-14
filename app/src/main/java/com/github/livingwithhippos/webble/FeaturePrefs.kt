package com.github.livingwithhippos.webble

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SharedPreferences file shared by the whole app. */
const val PREFS_NAME = "webble"

/** Optional features the user can turn on and off from the setup screen. All are on by default. */
enum class Feature(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
) {
    FILE_UPLOADS("file_uploads", R.string.feature_file_uploads, R.string.feature_file_uploads_desc),
    DOWNLOADS("downloads", R.string.feature_downloads, R.string.feature_downloads_desc),
    FULLSCREEN_VIDEO(
        "fullscreen_video",
        R.string.feature_fullscreen_video,
        R.string.feature_fullscreen_video_desc,
    ),
    DARK_PAGES("dark_pages", R.string.feature_dark_pages, R.string.feature_dark_pages_desc),
    LINK_MENU("link_menu", R.string.feature_link_menu, R.string.feature_link_menu_desc),
    REUSE_BUBBLES("reuse_bubbles", R.string.feature_reuse_bubbles, R.string.feature_reuse_bubbles_desc),
    CLOSE_ALL("close_all", R.string.feature_close_all, R.string.feature_close_all_desc),
    RECENT_PAGES("recent_pages", R.string.feature_recent_pages, R.string.feature_recent_pages_desc),
    NATIVE_APP("native_app", R.string.feature_native_app, R.string.feature_native_app_desc),
    DESKTOP_SITE("desktop_site", R.string.feature_desktop_site, R.string.feature_desktop_site_desc),
    FIND_IN_PAGE("find_in_page", R.string.feature_find_in_page, R.string.feature_find_in_page_desc),
    READER_MODE("reader_mode", R.string.feature_reader_mode, R.string.feature_reader_mode_desc),
    AD_BLOCK("ad_block", R.string.feature_ad_block, R.string.feature_ad_block_desc),
    CLEAN_LINKS("clean_links", R.string.feature_clean_links, R.string.feature_clean_links_desc),
}

class FeaturePrefs private constructor(context: Context) {

    companion object {
        private const val PREFIX = "feature_"

        @Volatile
        private var instance: FeaturePrefs? = null

        fun get(context: Context): FeaturePrefs = instance ?: synchronized(this) {
            instance ?: FeaturePrefs(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(read())

    /** The enabled features. Updates as soon as a switch changes, so open bubbles can react. */
    val enabled: StateFlow<Set<Feature>> = _enabled.asStateFlow()

    // Kept in a field: SharedPreferences only holds a weak reference to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith(PREFIX)) _enabled.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): Set<Feature> =
        Feature.entries.filter { prefs.getBoolean(PREFIX + it.key, true) }.toSet()

    fun isEnabled(feature: Feature): Boolean = feature in _enabled.value

    fun setEnabled(feature: Feature, on: Boolean) {
        prefs.edit { putBoolean(PREFIX + feature.key, on) }
    }
}
