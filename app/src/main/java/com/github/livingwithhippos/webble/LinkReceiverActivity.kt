package com.github.livingwithhippos.webble

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Invisible activity that receives clicked links (Webble is registered as a browser) and shared
 * text. It posts the bubble while it is still in the foreground, which lets the bubble expand
 * automatically, and then finishes.
 */
class LinkReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = LinkUtils.extractUrl(intent)
        if (url == null) {
            Toast.makeText(this, R.string.no_link_found, Toast.LENGTH_SHORT).show()
        } else {
            LinkUtils.openLink(this, url)
        }
        finish()
    }
}
