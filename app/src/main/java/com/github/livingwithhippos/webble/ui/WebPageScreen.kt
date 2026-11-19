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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.zIndex
import com.github.livingwithhippos.webble.ui.theme.Coral
import com.github.livingwithhippos.webble.ui.theme.Ink
import com.github.livingwithhippos.webble.ui.theme.Mint
import com.github.livingwithhippos.webble.ui.theme.Periwinkle

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
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.zIndex(1f),
                ) {
                    Column {
                        if (state.findVisible) {
                            FindBar(state, actions)
                        } else {
                            TopBar(state, actions)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .padding(horizontal = 24.dp),
                        ) {
                            if (state.progress in 0..99) {
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    color = Coral,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val host = state.url.toUri().host.orEmpty()
            Text(
                text = state.title.ifBlank { host.ifBlank { state.url } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (host.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                StatPill(
                    text = host.removePrefix("www."),
                    container = Periwinkle.copy(alpha = 0.22f),
                    content = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        val app = state.nativeApp
        if (app != null && state.has(Feature.NATIVE_APP)) {
            val label = app.label?.let { stringResource(R.string.action_open_in_app, it) }
                ?: stringResource(R.string.action_open_in_app_generic)
            if (app.icon != null) {
                Surface(
                    onClick = actions::openNativeApp,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(BUTTON_SIZE),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = label,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } else {
                ToolbarButton(R.drawable.ic_open_in_new, label, actions::openNativeApp)
            }
        }
        ToolbarButton(R.drawable.ic_refresh, stringResource(R.string.action_reload), actions::reload)
        OverflowMenu(state, actions)
        RoundIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.action_close),
            container = Coral,
            content = Color.White,
            size = BUTTON_SIZE,
            onClick = actions::close,
        )
    }
}

@Composable
private fun OverflowMenu(state: PageState, actions: PageActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(R.drawable.ic_more_vert, stringResource(R.string.action_more)) {
            expanded = true
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
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
                    trailingIcon = { OnOffPill(state.readerHtml != null) },
                    onClick = { pick(actions::toggleReader) },
                )
            }
            if (state.has(Feature.DESKTOP_SITE)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_desktop)) },
                    trailingIcon = { OnOffPill(state.desktopMode) },
                    onClick = { pick(actions::toggleDesktop) },
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
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
                HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
    ) {
        TextField(
            value = state.findQuery,
            onValueChange = actions::find,
            placeholder = { Text(stringResource(R.string.find_hint)) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.findNext(true) }),
            trailingIcon = if (state.findQuery.isNotEmpty()) {
                {
                    StatPill(
                        text = stringResource(R.string.find_count, state.findCurrent, state.findTotal),
                        container = MaterialTheme.colorScheme.inverseSurface,
                        content = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            } else {
                null
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = Coral,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        ToolbarButton(R.drawable.ic_arrow_up, stringResource(R.string.action_find_previous)) {
            actions.findNext(false)
        }
        ToolbarButton(R.drawable.ic_arrow_down, stringResource(R.string.action_find_next)) {
            actions.findNext(true)
        }
        RoundIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.action_find_close),
            container = Coral,
            content = Color.White,
            size = BUTTON_SIZE,
            onClick = actions::closeFind,
        )
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
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(Periwinkle.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuEntry(R.string.action_open_in_new_bubble, highlighted = true) {
                    pick { actions.openInNewBubble(url) }
                }
                MenuEntry(R.string.action_copy_link) { pick { actions.copyLink(url) } }
                MenuEntry(R.string.action_share_link) { pick { actions.share(url) } }
                MenuEntry(R.string.action_open_in_browser) { pick { actions.openInOtherBrowser(url) } }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun MenuEntry(label: Int, highlighted: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) Coral else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OnOffPill(on: Boolean) {
    StatPill(
        text = stringResource(if (on) R.string.status_on else R.string.status_off),
        container = if (on) Mint else MaterialTheme.colorScheme.surfaceVariant,
        content = if (on) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val BUTTON_SIZE = 36.dp

@Composable
private fun ToolbarButton(icon: Int, label: String, onClick: () -> Unit) {
    RoundIconButton(
        icon = icon,
        contentDescription = label,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurface,
        size = BUTTON_SIZE,
        onClick = onClick,
    )
}
