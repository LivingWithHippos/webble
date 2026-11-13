package com.github.livingwithhippos.webble.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.github.livingwithhippos.webble.Feature
import com.github.livingwithhippos.webble.R
import com.github.livingwithhippos.webble.web.PageState

/** What the page UI can ask the activity to do. */
interface PageActions {
    fun reload()
    fun close()
    fun closeAll()
    fun share(url: String)
    fun copyLink(url: String)
    fun openInOtherBrowser(url: String)
    fun openInNewBubble(url: String)
    fun openNativeApp()
    fun toggleDesktop()
    fun toggleReader()
    fun exitReader()
    fun showFind()
    fun closeFind()
    fun find(query: String)
    fun findNext(forward: Boolean)
    fun exitFullscreen()
    fun dismissLinkMenu()
}

@Composable
fun WebPageScreen(
    state: PageState,
    webView: WebView,
    readerWebView: () -> WebView,
    actions: PageActions,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Surface(tonalElevation = 3.dp) {
                    if (state.findVisible) {
                        FindBar(state, actions)
                    } else {
                        TopBar(state, actions)
                    }
                }
                if (state.progress in 0..99) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    if (state.readerHtml != null) {
                        AndroidView(factory = { readerWebView() }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        state.customView?.let { view ->
            key(view) {
                AndroidView(
                    factory = { view },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            }
        }
    }

    state.linkMenuUrl?.let { url -> LinkMenu(url, actions) }

    // Back closes overlays first; the activity's own callback goes back in page history.
    BackHandler(enabled = state.readerHtml != null) { actions.exitReader() }
    BackHandler(enabled = state.findVisible) { actions.closeFind() }
    BackHandler(enabled = state.customView != null) { actions.exitFullscreen() }
}

@Composable
private fun TopBar(state: PageState, actions: PageActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val host = state.url.toUri().host.orEmpty()
            Text(
                text = state.title.ifBlank { host.ifBlank { state.url } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (host.isNotBlank()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val app = state.nativeApp
        if (app != null && state.has(Feature.NATIVE_APP)) {
            val label = app.label?.let { stringResource(R.string.action_open_in_app, it) }
                ?: stringResource(R.string.action_open_in_app_generic)
            IconButton(onClick = actions::openNativeApp) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = label)
                }
            }
        }
        ToolbarButton(R.drawable.ic_refresh, R.string.action_reload, actions::reload)
        OverflowMenu(state, actions)
        ToolbarButton(R.drawable.ic_close, R.string.action_close, actions::close)
    }
}

@Composable
private fun OverflowMenu(state: PageState, actions: PageActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(R.drawable.ic_more_vert, R.string.action_more) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            fun pick(action: () -> Unit) {
                expanded = false
                action()
            }
            if (state.has(Feature.FIND_IN_PAGE)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_find)) },
                    onClick = { pick(actions::showFind) },
                )
            }
            if (state.has(Feature.READER_MODE)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_reader)) },
                    trailingIcon = { Checkbox(checked = state.readerHtml != null, onCheckedChange = null) },
                    onClick = { pick(actions::toggleReader) },
                )
            }
            if (state.has(Feature.DESKTOP_SITE)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_desktop)) },
                    trailingIcon = { Checkbox(checked = state.desktopMode, onCheckedChange = null) },
                    onClick = { pick(actions::toggleDesktop) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_link)) },
                onClick = { pick { actions.copyLink(state.url) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                onClick = { pick { actions.share(state.url) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_in_browser)) },
                onClick = { pick { actions.openInOtherBrowser(state.url) } },
            )
            if (state.has(Feature.CLOSE_ALL)) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_close_all)) },
                    onClick = { pick(actions::closeAll) },
                )
            }
        }
    }
}

@Composable
private fun FindBar(state: PageState, actions: PageActions) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
    ) {
        TextField(
            value = state.findQuery,
            onValueChange = actions::find,
            placeholder = { Text(stringResource(R.string.find_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.findNext(true) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        if (state.findQuery.isNotEmpty()) {
            Text(
                text = stringResource(R.string.find_count, state.findCurrent, state.findTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ToolbarButton(R.drawable.ic_arrow_up, R.string.action_find_previous) {
            actions.findNext(false)
        }
        ToolbarButton(R.drawable.ic_arrow_down, R.string.action_find_next) {
            actions.findNext(true)
        }
        ToolbarButton(R.drawable.ic_close, R.string.action_find_close, actions::closeFind)
    }
}

@Composable
private fun LinkMenu(url: String, actions: PageActions) {
    fun pick(action: () -> Unit) {
        actions.dismissLinkMenu()
        action()
    }
    AlertDialog(
        onDismissRequest = actions::dismissLinkMenu,
        title = {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                MenuEntry(R.string.action_open_in_new_bubble) { pick { actions.openInNewBubble(url) } }
                MenuEntry(R.string.action_copy_link) { pick { actions.copyLink(url) } }
                MenuEntry(R.string.action_share_link) { pick { actions.share(url) } }
                MenuEntry(R.string.action_open_in_browser) { pick { actions.openInOtherBrowser(url) } }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MenuEntry(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(label),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ToolbarButton(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}
