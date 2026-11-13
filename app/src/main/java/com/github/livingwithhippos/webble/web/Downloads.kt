package com.github.livingwithhippos.webble.web

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.github.livingwithhippos.webble.LinkUtils
import com.github.livingwithhippos.webble.R

object Downloads {

    /** Hands an http(s) download to DownloadManager, which shows its own progress notification. */
    fun enqueue(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val uri = url.toUri()
        val manager = context.getSystemService<DownloadManager>()
        // blob: and data: URLs only exist inside the page and can't be fetched again.
        if (!LinkUtils.isWebUri(uri) || manager == null) {
            Toast.makeText(context, R.string.download_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        // Pass the session cookies so downloads behind a login work.
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        try {
            manager.enqueue(request)
            Toast.makeText(
                context,
                context.getString(R.string.download_started, fileName),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.download_unsupported, Toast.LENGTH_SHORT).show()
        }
    }
}
