package com.github.livingwithhippos.webble.web

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.github.livingwithhippos.webble.LinkUtils

/**
 * An installed app (not a browser) that can open the current page.
 * [packageName] is null when several apps can, then the system asks which one.
 */
data class NativeApp(val packageName: String?, val label: String?, val icon: Bitmap?)

object NativeApps {

    private fun viewIntent(uri: Uri) =
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    /**
     * Packages with an activity made for this specific site. Like Chrome, a handler counts only
     * if its intent filter names a host: browsers and generic link handlers accept any URL.
     * MATCH_ALL is needed, otherwise web intents only return the default handler.
     */
    private fun specializedHandlers(context: Context, uri: Uri): Set<String> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(viewIntent(uri), PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.queryIntentActivities(viewIntent(uri), flags)
        }
        return list
            .filter { (it.filter?.countDataAuthorities() ?: 0) > 0 }
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /** Slow (package manager queries): call off the main thread. */
    fun find(context: Context, url: String): NativeApp? {
        val uri = url.toUri()
        if (!LinkUtils.isWebUri(uri)) return null
        val apps = specializedHandlers(context, uri) - context.packageName
        return when (apps.size) {
            0 -> null
            1 -> {
                val pkg = apps.first()
                val pm = context.packageManager
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    NativeApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info).toBitmap(96, 96),
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }

            else -> NativeApp(packageName = null, label = null, icon = null)
        }
    }

    fun launchIntent(app: NativeApp, url: String): Intent =
        viewIntent(url.toUri()).apply {
            if (app.packageName != null) {
                setPackage(app.packageName)
            } else {
                // Only offer apps, never browsers (Android 11+).
                addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            }
        }
}
