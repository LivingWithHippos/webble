package com.github.livingwithhippos.webble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf

/**
 * Posts one bubble per link. Bubbles are only shown for conversation notifications, so every
 * link is modeled as a conversation: a long-lived shortcut, a [Person] and a MessagingStyle.
 */
class BubbleNotifier(context: Context) {

    companion object {
        /** IMPORTANCE_HIGH channel: required for bubbles. */
        private const val CHANNEL_PAGES = "pages"

        /** Notification extra holding the page URL, used to find a bubble by URL. */
        private const val EXTRA_PAGE_URL = "com.github.livingwithhippos.webble.PAGE_URL"
        private const val EXTRA_PAGE_TITLE = "com.github.livingwithhippos.webble.PAGE_TITLE"

        fun shortcutId(id: Int) = "page_$id"

        /**
         * Favicons of the bubbles posted by this process, so a re-shown bubble keeps its icon.
         * Bitmaps can't go in notification extras cheaply, and losing them on process death is ok.
         */
        private val favicons = mutableMapOf<Int, Bitmap>()
    }

    private val appContext = context.applicationContext

    private val notificationManager: NotificationManager =
        appContext.getSystemService() ?: throw IllegalStateException("No NotificationManager")

    fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_PAGES) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PAGES,
                appContext.getString(R.string.channel_pages),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(R.string.channel_pages_description)
            },
        )
    }

    fun notificationsEnabled(): Boolean = notificationManager.areNotificationsEnabled()

    /**
     * True when a new link will actually be shown as a bubble. Every link is a brand new
     * conversation, so the app preference must be "All conversations can bubble".
     */
    fun canBubble(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notificationManager.areBubblesEnabled() &&
                notificationManager.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_ALL
        } else {
            @Suppress("DEPRECATION")
            notificationManager.areBubblesAllowed()
        }
    }

    private fun activeBubbles() = notificationManager.activeNotifications
        .filter { it.notification.channelId == CHANNEL_PAGES }

    fun isShowing(id: Int): Boolean = activeBubbles().any { it.id == id }

    /** The id of the bubble currently showing [url], if any. */
    fun findBubble(url: String): Int? {
        val key = LinkUtils.sameUrlKey(url)
        return activeBubbles().firstOrNull { sbn ->
            sbn.notification.extras.getString(EXTRA_PAGE_URL)
                ?.let { LinkUtils.sameUrlKey(it) } == key
        }?.id
    }

    /** Re-posts an existing bubble, keeping its URL, title and icon, optionally expanding it. */
    fun reshowBubble(id: Int, expand: Boolean) {
        val extras = activeBubbles().firstOrNull { it.id == id }?.notification?.extras ?: return
        val url = extras.getString(EXTRA_PAGE_URL) ?: return
        showBubble(
            id = id,
            url = url,
            title = extras.getString(EXTRA_PAGE_TITLE),
            favicon = favicons[id],
            expand = expand,
            update = true,
        )
    }

    /** Closes every bubble and removes all the page shortcuts. */
    fun removeAll() {
        activeBubbles().forEach { notificationManager.cancel(it.id) }
        // Also the shortcuts the system cached for past conversations, not only the active ones.
        val shortcutIds = ShortcutManagerCompat.getShortcuts(
            appContext,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED,
        ).map { it.id }.filter { it.startsWith("page_") }
        ShortcutManagerCompat.removeLongLivedShortcuts(appContext, shortcutIds)
        ShortcutManagerCompat.removeAllDynamicShortcuts(appContext)
        favicons.clear()
    }

    /**
     * Posts (or re-posts) the bubble for link [id].
     *
     * @param expand open the bubble right away. Only works while the app is in the foreground.
     * @param update true when refreshing an existing bubble (new title/icon): no alert.
     */
    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled().
    fun showBubble(
        id: Int,
        url: String,
        title: String? = null,
        favicon: Bitmap? = null,
        expand: Boolean,
        update: Boolean = false,
    ) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (favicon != null) favicons[id] = favicon

        val uri = url.toUri()
        val label = title?.takeIf { it.isNotBlank() } ?: uri.host ?: url
        val icon = favicon?.let { IconCompat.createWithAdaptiveBitmap(toAdaptiveBitmap(it)) }
            ?: IconCompat.createWithResource(appContext, R.mipmap.ic_launcher)
        val shortcutId = shortcutId(id)
        val page = Person.Builder()
            .setName(label)
            .setIcon(icon)
            .setKey(shortcutId)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(
            appContext,
            ShortcutInfoCompat.Builder(appContext, shortcutId)
                .setLocusId(LocusIdCompat(shortcutId))
                .setActivity(ComponentName(appContext, MainActivity::class.java))
                .setShortLabel(label)
                .setLongLabel(url)
                .setIcon(icon)
                .setLongLived(true)
                .setPerson(page)
                .setIntent(WebPageActivity.fullScreenIntent(appContext, uri, id))
                .build(),
        )

        // The activity shown in the expanded bubble. The data makes each link's intent unique.
        val bubbleIntent = PendingIntent.getActivity(
            appContext,
            id,
            Intent(appContext, WebPageActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData("webble://page/$id".toUri())
                .putExtra(WebPageActivity.EXTRA_URL, url)
                .putExtra(WebPageActivity.EXTRA_ID, id)
                .putExtra(WebPageActivity.EXTRA_IN_BUBBLE, true),
            // Bubble intents must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // Used for "open full screen" from the bubble menu and when bubbles are turned off.
        val contentIntent = PendingIntent.getActivity(
            appContext,
            id,
            WebPageActivity.fullScreenIntent(appContext, uri, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val deleteIntent = PendingIntent.getBroadcast(
            appContext,
            id,
            Intent(appContext, BubbleDismissReceiver::class.java)
                .putExtra(WebPageActivity.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val style = NotificationCompat.MessagingStyle(
            Person.Builder().setName(appContext.getString(R.string.you)).build(),
        ).addMessage(url, System.currentTimeMillis(), page)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_PAGES)
            .setBubbleMetadata(
                NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
                    .setDesiredHeight(
                        appContext.resources.getDimensionPixelSize(R.dimen.bubble_height),
                    )
                    .setAutoExpandBubble(expand)
                    // Only the bubble is shown, not a heads-up notification.
                    .setSuppressNotification(true)
                    .build(),
            )
            .setContentTitle(label)
            .setContentText(url)
            // Also what makes an icon-only update count as a visible change: without it the
            // system doesn't pass the update to the bubble, which keeps its old icon.
            .setLargeIcon(favicon)
            .setSmallIcon(R.drawable.ic_bubble)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .addPerson(page)
            .setStyle(style)
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setOnlyAlertOnce(update)
            .addExtras(bundleOf(EXTRA_PAGE_URL to url, EXTRA_PAGE_TITLE to title))
            .build()

        notificationManager.notify(id, notification)
    }

    /** Refreshes the bubble's title and icon once the page has loaded them. */
    fun updateBubble(id: Int, url: String, title: String?, favicon: Bitmap?) {
        if (!isShowing(id)) return
        showBubble(id, url, title, favicon, expand = false, update = true)
    }

    fun removeBubble(id: Int) {
        favicons.remove(id)
        notificationManager.cancel(id)
        removeShortcut(appContext, id)
    }

    /**
     * Adaptive icons are 108dp with the visible part in the center 72dp, so the favicon is drawn
     * small in the middle of a white square.
     */
    private fun toAdaptiveBitmap(favicon: Bitmap): Bitmap {
        val size = 216
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val iconSize = size / 3
        val start = (size - iconSize) / 2
        canvas.drawBitmap(
            favicon,
            null,
            Rect(start, start, start + iconSize, start + iconSize),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return bitmap
    }
}

fun removeShortcut(context: Context, id: Int) {
    ShortcutManagerCompat.removeLongLivedShortcuts(
        context.applicationContext,
        listOf(BubbleNotifier.shortcutId(id)),
    )
}
