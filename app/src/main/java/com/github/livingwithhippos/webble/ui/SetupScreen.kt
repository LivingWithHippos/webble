package com.github.livingwithhippos.webble.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import com.github.livingwithhippos.webble.HistoryStore
import com.github.livingwithhippos.webble.HistoryEntry
import com.github.livingwithhippos.webble.FeaturePrefs
import com.github.livingwithhippos.webble.Feature
import androidx.core.net.toUri
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.github.livingwithhippos.webble.BubbleNotifier
import com.github.livingwithhippos.webble.LinkUtils
import com.github.livingwithhippos.webble.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.github.livingwithhippos.webble.ui.theme.Coral
import com.github.livingwithhippos.webble.ui.theme.Ink
import com.github.livingwithhippos.webble.ui.theme.Mint
import com.github.livingwithhippos.webble.ui.theme.Mustard
import com.github.livingwithhippos.webble.ui.theme.Periwinkle

@Composable
fun SetupScreen(refreshKey: Int, onOpenLink: (Uri) -> Unit) {
    val context = LocalContext.current
    val notifier = remember { BubbleNotifier(context) }
    val roleManager = remember { context.getSystemService<RoleManager>() }
    // Bumped after permission/role results so statuses are re-read.
    var localRefresh by remember { mutableIntStateOf(0) }

    val notificationsOk = remember(refreshKey, localRefresh) { notifier.notificationsEnabled() }
    val bubblesOk = remember(refreshKey, localRefresh) { notifier.canBubble() }
    val isDefaultBrowser = remember(refreshKey, localRefresh) {
        roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
    }

    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        localRefresh++
        // Denied before: the system no longer shows the dialog, go to Settings instead.
        if (!granted) openNotificationSettings()
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { localRefresh++ }

    val featurePrefs = remember { FeaturePrefs.get(context) }
    val historyStore = remember { HistoryStore.get(context) }
    val enabled by featurePrefs.enabled.collectAsState()
    val history by historyStore.entries.collectAsState()
    var url by rememberSaveable { mutableStateOf("https://developer.android.com") }
    val openTyped = { LinkUtils.normalize(url)?.let(onOpenLink) }

    val setupDone = listOf(notificationsOk, bubblesOk, isDefaultBrowser).count { it }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Heading(
                        stringResource(R.string.app_name),
                        large = true,
                        modifier = Modifier.weight(1f),
                    )
                    StatPill(
                        text = stringResource(R.string.setup_ready_count, setupDone, 3),
                        container = if (setupDone == 3) Mint else Coral,
                        content = if (setupDone == 3) Ink else Color.White,
                        icon = if (setupDone == 3) R.drawable.ic_check else null,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.setup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                // Always dark, in both themes: the "hero" card.
                ColorBlockCard(color = Ink, contentColor = Color.White) {
                    SetupRow(
                        label = stringResource(R.string.setup_notifications),
                        done = notificationsOk,
                        action = stringResource(R.string.action_allow),
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings()
                        }
                    }
                    CardDivider()
                    SetupRow(
                        label = stringResource(R.string.setup_bubbles),
                        done = bubblesOk,
                        action = stringResource(R.string.action_open_settings),
                    ) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                    CardDivider()
                    SetupRow(
                        label = stringResource(R.string.setup_default_browser),
                        done = isDefaultBrowser,
                        action = stringResource(R.string.action_set_default),
                    ) {
                        if (roleManager?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true) {
                            roleLauncher.launch(
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER),
                            )
                        } else {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                        }
                    }
                }
            }

            item {
                ColorBlockCard(color = Periwinkle, contentColor = Ink) {
                    Heading(stringResource(R.string.setup_try))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = { Text(stringResource(R.string.setup_url_label)) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Ink,
                                unfocusedTextColor = Ink,
                                cursorColor = Coral,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { openTyped() }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(
                            icon = R.drawable.ic_arrow_forward,
                            contentDescription = stringResource(R.string.action_open_in_bubble),
                            container = Coral,
                            content = Color.White,
                            size = 52.dp,
                            onClick = { openTyped() },
                        )
                    }
                    if (Feature.CLOSE_ALL in enabled) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { notifier.removeAll() },
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, Ink),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                        ) {
                            Text(
                                stringResource(R.string.action_close_all).uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Heading(stringResource(R.string.setup_features), Modifier.weight(1f))
                    StatPill(
                        text = stringResource(
                            R.string.features_on_count,
                            enabled.size,
                            Feature.entries.size,
                        ),
                        container = MaterialTheme.colorScheme.inverseSurface,
                        content = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
            items(Feature.entries, key = { it.key }) { feature ->
                FeatureRow(
                    feature = feature,
                    checked = feature in enabled,
                    onCheckedChange = { featurePrefs.setEnabled(feature, it) },
                    extra = if (feature == Feature.RECENT_PAGES && history.isNotEmpty()) {
                        {
                            TextButton(onClick = historyStore::clear) {
                                Text(stringResource(R.string.action_clear_history))
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            if (Feature.RECENT_PAGES in enabled) {
                item {
                    ColorBlockCard(
                        color = Mustard,
                        contentColor = Ink,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Heading(stringResource(R.string.setup_recent), Modifier.weight(1f))
                            if (history.isNotEmpty()) {
                                PillButton(
                                    text = stringResource(R.string.action_clear),
                                    onClick = historyStore::clear,
                                    container = Ink,
                                    content = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (history.isEmpty()) {
                            Text(
                                stringResource(R.string.recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        history.forEach { entry ->
                            key(entry.url + entry.timestamp) {
                                HistoryRow(entry) { onOpenLink(entry.url.toUri()) }
                            }
                        }
                    }
                }
            }

            item { AboutFooter(onOpenLink) }
        }
    }
}

private const val REPO_URL = "https://github.com/LivingWithHippos/webble"
private const val LICENSE_URL = "$REPO_URL/blob/master/LICENSE"
private const val DONATE_URL = "https://ko-fi.com/E1E412NFX7"

/** Donation link, app version, and links to the source code and license (opened in a bubble). */
@Composable
private fun AboutFooter(onOpenLink: (Uri) -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        PillButton(
            text = stringResource(R.string.about_donate),
            onClick = { onOpenLink(DONATE_URL.toUri()) },
            trailingIcon = R.drawable.ic_open_in_new,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.about_version, stringResource(R.string.app_name), version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { onOpenLink(REPO_URL.toUri()) }) {
                Text(stringResource(R.string.about_source_code))
            }
            TextButton(onClick = { onOpenLink(LICENSE_URL.toUri()) }) {
                Text(stringResource(R.string.about_license))
            }
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FeatureRow(
    feature: Feature,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: (@Composable () -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(feature.title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(feature.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Shown when the feature is off, so old history can still be removed.
                if (!checked) extra?.invoke()
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = webbleSwitchColors(),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val host = entry.url.toUri().host.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        LetterAvatar(host, Modifier.border(1.5.dp, Ink, CircleShape))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$host · " +
                    if (System.currentTimeMillis() - entry.timestamp < DateUtils.MINUTE_IN_MILLIS) {
                        stringResource(R.string.just_now)
                    } else {
                        DateUtils.getRelativeTimeSpanString(entry.timestamp)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RoundIconButton(
            icon = R.drawable.ic_arrow_forward,
            contentDescription = null,
            container = Ink,
            content = Color.White,
            size = 32.dp,
            onClick = onClick,
        )
    }
}

@Composable
private fun SetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        if (done) {
            StatPill(
                text = stringResource(R.string.status_ok),
                container = Mint,
                content = Ink,
                icon = R.drawable.ic_check,
            )
        } else {
            PillButton(text = action, onClick = onClick, trailingIcon = R.drawable.ic_arrow_forward)
        }
    }
}
