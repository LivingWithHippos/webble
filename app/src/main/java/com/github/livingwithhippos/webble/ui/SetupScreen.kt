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
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Text(
                    stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
            }

            item {
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
            }
            item {
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
            }
            item {
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

            item { SectionTitle(stringResource(R.string.setup_try)) }
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.setup_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { openTyped() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openTyped() }) {
                        Text(stringResource(R.string.action_open_in_bubble))
                    }
                    if (Feature.CLOSE_ALL in enabled) {
                        OutlinedButton(onClick = { notifier.removeAll() }) {
                            Text(stringResource(R.string.action_close_all))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.setup_features)) }
            items(Feature.entries, key = { it.key }) { feature ->
                FeatureRow(
                    feature = feature,
                    checked = feature in enabled,
                    onCheckedChange = { featurePrefs.setEnabled(feature, it) },
                    extra = if (feature == Feature.RECENT_PAGES && history.isNotEmpty()) {
                        { TextButton(onClick = historyStore::clear) { Text(stringResource(R.string.action_clear_history)) } }
                    } else {
                        null
                    },
                )
            }

            if (Feature.RECENT_PAGES in enabled) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(stringResource(R.string.setup_recent), Modifier.weight(1f))
                        if (history.isNotEmpty()) {
                            TextButton(onClick = historyStore::clear) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                }
                if (history.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.recent_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(history, key = { it.url + it.timestamp }) { entry ->
                    HistoryRow(entry) { onOpenLink(entry.url.toUri()) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun FeatureRow(
    feature: Feature,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extra: (@Composable () -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(feature.title), style = MaterialTheme.typography.bodyLarge)
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
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.url.toUri().host.orEmpty()} · " +
                    if (System.currentTimeMillis() - entry.timestamp < DateUtils.MINUTE_IN_MILLIS) {
                        stringResource(R.string.just_now)
                    } else {
                        DateUtils.getRelativeTimeSpanString(entry.timestamp)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (done) {
            Text(
                stringResource(R.string.status_ok),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            Button(onClick = onClick) { Text(action) }
        }
    }
}
