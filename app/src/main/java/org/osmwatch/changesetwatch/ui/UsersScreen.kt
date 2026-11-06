package org.osmwatch.changesetwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea

/**
 * Gli utenti che hai segnato: seguiti (notifiche ovunque nel mondo), sospetti e fidati. Tocco sul
 * nome = i suoi changeset recenti; la croce lo toglie dalla lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    watched: List<WatchArea>,
    suspects: Set<String>,
    trusted: Set<String>,
    onOpen: (String) -> Unit,
    onUnwatch: (WatchArea) -> Unit,
    onUnsuspect: (String) -> Unit,
    onUntrust: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
            )
        }
    ) { pad ->
        val scheme = MaterialTheme.colorScheme
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            section(
                R.string.users_watched,
                R.string.users_watched_empty,
                // un utente per riga anche se, per un vecchio bug, è stato seguito due volte
                watched
                    .distinctBy { it.watchUser }
                    .mapNotNull { a -> a.watchUser?.let { it to { onUnwatch(a) } } },
                Icons.Default.NotificationsActive,
                scheme.primary,
                onOpen,
            )
            section(
                R.string.users_suspect,
                R.string.users_suspect_empty,
                suspects.sortedBy { it.lowercase() }.map { it to { onUnsuspect(it) } },
                Icons.Default.Flag,
                scheme.error,
                onOpen,
            )
            section(
                R.string.users_trusted,
                R.string.users_trusted_empty,
                trusted.sortedBy { it.lowercase() }.map { it to { onUntrust(it) } },
                Icons.Default.Verified,
                scheme.primary,
                onOpen,
            )
        }
    }
}

private fun LazyListScope.section(
    title: Int,
    empty: Int,
    users: List<Pair<String, () -> Unit>>,
    icon: ImageVector,
    tint: Color,
    onOpen: (String) -> Unit,
) {
    item(key = "title-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
    }
    if (users.isEmpty())
        item(key = "empty-$title") {
            Muted(stringResource(empty), Modifier.padding(horizontal = 8.dp))
        }
    items(users, key = { "$title-${it.first}" }) { (name, onRemove) ->
        UserRow(name, icon, tint, { onOpen(name) }, onRemove)
    }
}

@Composable
private fun UserRow(
    name: String,
    icon: ImageVector,
    tint: Color,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier =
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.remove_from_list, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
