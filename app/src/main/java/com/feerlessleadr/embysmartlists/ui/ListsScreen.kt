package com.feerlessleadr.embysmartlists.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.SmartListDoc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(vm: ListsViewModel, onNew: () -> Unit, onEdit: (String) -> Unit, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<SmartListDoc?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emby SmartLists") },
                actions = {
                    IconButton(onClick = vm::refreshAll, enabled = state.lists.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh all lists")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.lists.isEmpty() -> ErrorState(state.error.orEmpty(), onRetry = vm::reload, modifier = Modifier.align(Alignment.Center))
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::reload, modifier = Modifier.fillMaxSize()) {
                    if (state.lists.isEmpty()) {
                        Text(
                            "No smart lists yet. Tap \"New list\" to create one.",
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.lists, key = { it.id }) { doc ->
                                ListCard(
                                    doc = doc,
                                    catalog = state.catalog,
                                    busy = doc.id in state.busyIds,
                                    onEdit = { onEdit(doc.id) },
                                    onRefresh = { vm.refreshList(doc) },
                                    onToggleEnabled = { vm.setEnabled(doc, !doc.enabled) },
                                    onDelete = { confirmDelete = doc },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { doc ->
        DeleteDialog(
            doc = doc,
            onConfirm = { alsoDeleteEmbyList ->
                confirmDelete = null
                vm.delete(doc, alsoDeleteEmbyList)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun ListCard(
    doc: SmartListDoc,
    catalog: Catalog?,
    busy: Boolean,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(doc.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (doc.enabled) 0.55f else 0.3f)),
    ) {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (doc.isCollection) Icons.Default.VideoLibrary else Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = doc.type,
                    tint = if (doc.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (doc.enabled) Describe.summary(doc) else "Disabled · ${Describe.summary(doc)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Hide details" else "Show details")
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            enabled = !busy,
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh now") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = doc.enabled && !busy,
                            onClick = { menu = false; onRefresh() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (doc.enabled) "Disable" else "Enable") },
                            leadingIcon = { Icon(if (doc.enabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null) },
                            enabled = !busy,
                            onClick = { menu = false; onToggleEnabled() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            enabled = !busy,
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (expanded) Details(doc, catalog)
        }
    }
}

@Composable
private fun Details(doc: SmartListDoc, catalog: Catalog?) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailLine("Type", "${doc.type} · ${doc.mediaTypes.joinToString(", ").ifEmpty { "any media" }}")

        val groups = Describe.ruleGroups(doc, catalog)
        if (groups.isEmpty()) {
            DetailLine("Rules", "none (everything of that type)")
        } else {
            groups.forEachIndexed { index, text ->
                DetailLine(if (index == 0) "Rules" else "or", text)
            }
        }

        val sorts = doc.sortOptions
        if (sorts.isNotEmpty()) DetailLine("Sort", sorts.joinToString("; ") { Describe.sort(it, catalog) })

        val limits = listOfNotNull(
            doc.maxItems?.let { "at most $it items" },
            doc.maxPlayTimeMinutes?.let { "at most $it minutes" },
            doc.minItems?.let { "hidden below $it items" },
        )
        if (limits.isNotEmpty()) DetailLine("Limits", limits.joinToString(", "))
        DetailLine("Refresh", Describe.autoRefresh(doc.autoRefresh, catalog))
    }
}

@Composable
private fun DetailLine(label: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(width = 52.dp, height = 20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DeleteDialog(doc: SmartListDoc, onConfirm: (alsoDeleteEmbyList: Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoDelete by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${doc.name}\"?") },
        text = {
            Column {
                Text("This cannot be undone.")
                Row(Modifier.fillMaxWidth().clickable { alsoDelete = !alsoDelete }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoDelete, onCheckedChange = { alsoDelete = it })
                    Text(if (doc.isCollection) "Also delete the Emby collection" else "Also delete the Emby playlist")
                }
                if (!alsoDelete) {
                    Text(
                        "Only the smart list is removed; the Emby list stays and is no longer updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(alsoDelete) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
