package com.github.livingwithhippos.webble

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import com.github.livingwithhippos.webble.web.ClearUrls

object LinkUtils {

    private const val KEY_NEXT_ID = "next_link_id"

    private val WEB_SCHEMES = setOf("http", "https")

    fun isWebUri(uri: Uri?): Boolean = uri?.scheme?.lowercase() in WEB_SCHEMES

    /**
     * A key for "is this the same page": no fragment, no trailing slash, lowercase scheme and
     * host. Query parameters are kept because they usually change the content.
     */
    fun sameUrlKey(url: String): String {
        val uri = url.toUri()
        if (!isWebUri(uri)) return url
        val path = uri.encodedPath.orEmpty().trimEnd('/')
        val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
        return "${uri.scheme?.lowercase()}://${uri.encodedAuthority?.lowercase()}$path$query"
    }

    /** Extracts the web link from a VIEW intent (clicked link) or a SEND intent (shared text). */
    fun extractUrl(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data?.takeIf { isWebUri(it) }
        Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { findUrl(it) }
        else -> null
    }

    /** Finds the first web link in free text, adding https:// when the scheme is missing. */
    fun findUrl(text: CharSequence): Uri? {
        val matcher = Patterns.WEB_URL.matcher(text)
        if (!matcher.find()) return null
        return normalize(matcher.group())
    }

    fun normalize(raw: String): Uri? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = trimmed.toUri()
        return when {
            isWebUri(uri) -> uri
            uri.scheme == null -> "https://$trimmed".toUri()
            else -> null
        }
    }

    /**
     * Returns a new id for a link. It is used as the notification id, the shortcut id suffix and
     * the PendingIntent request code, so every link gets its own bubble.
     */
    fun newLinkId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit { putInt(KEY_NEXT_ID, if (id >= Int.MAX_VALUE - 1) 1 else id + 1) }
        return id
    }

    /**
     * Opens [link] in a bubble, without its tracking parameters when "Clean links" is on. Call
     * this while [activity] is in the foreground so the bubble can expand automatically. If the
     * page is already in a bubble (and reusing is on), that bubble is brought forward instead of
     * making a duplicate. Falls back to a full screen page when bubbles are not allowed.
     *
     * @param expand false adds the bubble to the stack without opening it ("read later").
     */
    fun openLink(activity: Activity, link: Uri, expand: Boolean = true) {
        // Cleaned first, so a tracked and a clean copy of a page share one bubble.
        val url = ClearUrls.clean(activity, link.toString()).toUri()
        val notifier = BubbleNotifier(activity)
        notifier.ensureChannel()
        if (!notifier.canBubble()) {
            activity.startActivity(WebPageActivity.fullScreenIntent(activity, url))
            Toast.makeText(activity, R.string.bubbles_off_hint, Toast.LENGTH_LONG).show()
            return
        }
        val existing = if (FeaturePrefs.get(activity).isEnabled(Feature.REUSE_BUBBLES)) {
            notifier.findBubble(url.toString())
        } else {
            null
        }
        if (existing != null) {
            notifier.reshowBubble(existing, expand)
        } else {
            notifier.showBubble(id = newLinkId(activity), url = url.toString(), expand = expand)
        }
    }
}
