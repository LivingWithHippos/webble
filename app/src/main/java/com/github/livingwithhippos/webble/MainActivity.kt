package com.github.livingwithhippos.webble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.github.livingwithhippos.webble.ui.SetupScreen
import com.github.livingwithhippos.webble.ui.theme.WebbleTheme

/** Setup screen: permissions, bubble settings, default browser, and a test field. */
class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the setup statuses are re-read after visiting Settings. */
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BubbleNotifier(this).ensureChannel()
        setContent {
            WebbleTheme {
                SetupScreen(
                    refreshKey = resumeCount,
                    onOpenLink = { url -> LinkUtils.openLink(this, url) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }
}
