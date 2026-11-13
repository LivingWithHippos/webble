package com.github.livingwithhippos.webble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Removes the link's shortcut when its bubble/notification is dismissed. */
class BubbleDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(WebPageActivity.EXTRA_ID, -1)
        if (id != -1) removeShortcut(context, id)
    }
}
