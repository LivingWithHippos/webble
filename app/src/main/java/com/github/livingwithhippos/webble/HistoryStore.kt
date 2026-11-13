package com.github.livingwithhippos.webble

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val url: String, val title: String, val timestamp: Long)

/** Recently opened pages, newest first, stored as JSON in SharedPreferences. */
class HistoryStore private constructor(context: Context) {

    companion object {
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 50

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore = instance ?: synchronized(this) {
            instance ?: HistoryStore(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    /** Adds a visit. Visiting a page again moves it to the top instead of duplicating it. */
    fun add(url: String, title: String) {
        if (!LinkUtils.isWebUri(url.toUri())) return
        val key = LinkUtils.sameUrlKey(url)
        val entry = HistoryEntry(
            url = url,
            title = title.ifBlank { url.toUri().host ?: url },
            timestamp = System.currentTimeMillis(),
        )
        val updated = listOf(entry) +
            _entries.value.filterNot { LinkUtils.sameUrlKey(it.url) == key }
        save(updated.take(MAX_ENTRIES))
    }

    fun clear() = save(emptyList())

    private fun save(list: List<HistoryEntry>) {
        _entries.value = list
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject()
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("time", it.timestamp),
            )
        }
        prefs.edit { putString(KEY_HISTORY, json.toString()) }
    }

    private fun load(): List<HistoryEntry> = try {
        val json = JSONArray(prefs.getString(KEY_HISTORY, null) ?: "[]")
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            HistoryEntry(o.getString("url"), o.optString("title"), o.optLong("time"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}
