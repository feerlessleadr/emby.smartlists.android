package com.feerlessleadr.embysmartlists.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embysmartlists.data.COLLECTION_ONLY_MEDIA_TYPES
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.GroupDraft
import com.feerlessleadr.embysmartlists.data.ListDraft
import com.feerlessleadr.embysmartlists.data.MEDIA_TYPE_LABELS
import com.feerlessleadr.embysmartlists.data.SortDraft
import com.feerlessleadr.embysmartlists.data.Templates
import com.feerlessleadr.embysmartlists.data.fieldsFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val catalog = state.catalog
    val snackbar = remember { SnackbarHostState() }

    // The form is long: show a problem where it is seen wherever the person has scrolled to.
    LaunchedEffect(state.saveError) {
        state.saveError?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (draft == null || draft.isNew) "New list" else "Edit list") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (draft != null) {
                        TextButton(onClick = { vm.save(onClose) }, enabled = !state.saving) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::load) { Text("Try again") }
                }

                state.choosingTemplate -> TemplateChooser(onPick = vm::chooseTemplate)
                draft != null && catalog != null -> EditorForm(vm, state, draft, catalog)
            }
        }
    }
}

@Composable
private fun TemplateChooser(onPick: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Start from", style = MaterialTheme.typography.titleMedium)
        Templates.all.forEach { template ->
            Card(Modifier.fillMaxWidth().clickable { onPick(template.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(template.title, style = MaterialTheme.typography.titleMedium)
                    Text(template.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorForm(vm: EditorViewModel, state: EditorUiState, draft: ListDraft, catalog: Catalog) {
    val fields = remember(catalog, draft.mediaTypes) { fieldsFor(catalog, draft.mediaTypes) }
    var addRuleTo by remember { mutableStateOf<Long?>(null) }
    val lists = catalog

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        // ----- basics -----
        Section("Basics") {
            if (draft.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !draft.isCollection,
                        onClick = {
                            vm.update { d -> d.copy(type = "Playlist", mediaTypes = d.mediaTypes - COLLECTION_ONLY_MEDIA_TYPES) }
                        },
                        label = { Text("Playlist") },
                    )
                    FilterChip(selected = draft.isCollection, onClick = { vm.update { it.copy(type = "Collection") } }, label = { Text("Collection") })
                }
            } else {
                Text(draft.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.isNew) {
                DropdownField(
                    label = if (draft.isCollection) "Watch state of" else "For user",
                    selectedText = state.users.firstOrNull { it.id == draft.ownerId }?.name ?: "Choose a user",
                    options = state.users.map { it.id to it.name },
                    onSelect = { id -> vm.update { it.copy(ownerId = id) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val owner = state.users.firstOrNull { it.id.equals(draft.ownerId, ignoreCase = true) }?.name
                Text(
                    buildString {
                        append(if (draft.isCollection) "Watch state of: " else "For user: ")
                        append(owner ?: "unknown")
                        if (draft.shared) append(" (shared with other users; change who it is for on the web page)")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.enabled, onCheckedChange = { v -> vm.update { it.copy(enabled = v) } })
            }
        }

        // ----- media types -----
        Section("Media types") {
            val offered = MEDIA_TYPE_LABELS.filter { (value, _) ->
                (catalog.mediaTypes.isEmpty() || value in catalog.mediaTypes) && (draft.isCollection || value !in COLLECTION_ONLY_MEDIA_TYPES)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offered.forEach { (value, label) ->
                    FilterChip(
                        selected = value in draft.mediaTypes,
                        onClick = { vm.update { d -> d.copy(mediaTypes = if (value in d.mediaTypes) d.mediaTypes - value else d.mediaTypes + value) } },
                        label = { Text(label) },
                    )
                }
            }
            if (!draft.isCollection) {
                Text(
                    "Series, seasons, albums and the like can only go in a collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ----- rules -----
        Section("Rules") {
            Text(
                if (draft.groups.isEmpty()) {
                    "No rules: everything of the chosen media types is included."
                } else {
                    "An item is included when it matches every rule in at least one group."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.groups.forEachIndexed { index, group ->
                if (index > 0) Text("or", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        group.rules.forEach { rule ->
                            androidx.compose.runtime.key(rule.key) {
                                RuleCard(
                                    rule = rule,
                                    catalog = catalog,
                                    fields = fields,
                                    libraries = state.libraries,
                                    onChange = { changed ->
                                        vm.update { d -> d.mapGroup(group.key) { g -> g.copy(rules = g.rules.map { if (it.key == rule.key) changed else it }) } }
                                    },
                                    onDelete = {
                                        vm.update { d -> d.mapGroup(group.key) { g -> g.copy(rules = g.rules.filterNot { it.key == rule.key }) } }
                                    },
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { addRuleTo = group.key }) {
                                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                                Text("  Add rule")
                            }
                            TextButton(onClick = { vm.update { d -> d.copy(groups = d.groups.filterNot { it.key == group.key }) } }) {
                                Text("Remove group", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = {
                val g = GroupDraft(rules = emptyList())
                vm.update { d -> d.copy(groups = d.groups + g) }
                addRuleTo = g.key
            }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                Text(if (draft.groups.isEmpty()) "  Add a rule" else "  Add another group (or)")
            }
        }

        // ----- sorting -----
        Section("Sorting") {
            draft.sorts.forEachIndexed { index, sort ->
                androidx.compose.runtime.key(sort.key) {
                    SortRow(
                        sort = sort,
                        catalog = catalog,
                        canRemove = draft.sorts.size > 1,
                        onChange = { changed -> vm.update { d -> d.copy(sorts = d.sorts.map { if (it.key == sort.key) changed else it }) } },
                        onRemove = { vm.update { d -> d.copy(sorts = d.sorts.filterNot { it.key == sort.key }) } },
                        label = if (index == 0) "Sort by" else "Then by",
                    )
                }
            }
            if (draft.sorts.size < 3) {
                TextButton(onClick = { vm.update { d -> d.copy(sorts = d.sorts + SortDraft(sortBy = "Name")) } }) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                    Text("  Add a sort")
                }
            }
        }

        // ----- limits -----
        Section("Limits") {
            NumberField("Maximum items", draft.maxItems, "Empty means no limit") { v -> vm.update { it.copy(maxItems = v) } }
            NumberField("Maximum play time (minutes)", draft.maxPlayTimeMinutes, "Empty means no limit") { v -> vm.update { it.copy(maxPlayTimeMinutes = v) } }
            NumberField("Minimum items", draft.minItems, "The Emby list is hidden while fewer items match") { v -> vm.update { it.copy(minItems = v) } }
        }

        // ----- refresh -----
        Section("Refresh") {
            DropdownField(
                label = "Update the list",
                selectedText = lists.autoRefreshModes.firstOrNull { it.value == draft.autoRefresh }?.label ?: draft.autoRefresh,
                options = lists.autoRefreshModes.map { it.value to it.label },
                onSelect = { v -> vm.update { it.copy(autoRefresh = v) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.size(48.dp))
    }

    addRuleTo?.let { groupKey ->
        GroupedPickerDialog(
            title = "Choose a field",
            items = fieldPickerItems(fields),
            selected = null,
            onPick = { name ->
                addRuleTo = null
                catalog.field(name)?.let { f -> vm.update { d -> d.mapGroup(groupKey) { g -> g.copy(rules = g.rules + newRule(f)) } } }
            },
            onDismiss = {
                // A group created just now stays empty if nothing was picked: drop it.
                vm.update { d -> d.copy(groups = d.groups.filterNot { it.key == groupKey && it.rules.isEmpty() }) }
                addRuleTo = null
            },
        )
    }
}

private fun ListDraft.mapGroup(key: Long, change: (GroupDraft) -> GroupDraft): ListDraft =
    copy(groups = groups.map { if (it.key == key) change(it) else it })

@Composable
private fun NumberField(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit)) },
        label = { Text(label) },
        supportingText = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SortRow(
    sort: SortDraft,
    catalog: Catalog,
    canRemove: Boolean,
    onChange: (SortDraft) -> Unit,
    onRemove: () -> Unit,
    label: String,
) {
    val info = catalog.sorts.firstOrNull { it.value == sort.sortBy }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField(
                    label = label,
                    selectedText = (info?.label ?: sort.sortBy).substringBefore(" ("),
                    title = "Choose a sort",
                    items = catalog.sorts.map { PickerItem(it.value, it.label, it.group) },
                    selected = sort.sortBy,
                    onPick = { value ->
                        val picked = catalog.sorts.firstOrNull { it.value == value }
                        onChange(
                            sort.copy(
                                sortBy = value,
                                groupBy = if (picked?.roundRobin == true) sort.groupBy ?: catalog.roundRobinGroupByFields.firstOrNull()?.value else null,
                                within = if (picked?.roundRobin == true) sort.within else null,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove sort", tint = MaterialTheme.colorScheme.error) }
                }
            }
            if (info?.directionless != true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !sort.descending, onClick = { onChange(sort.copy(descending = false)) }, label = { Text("Ascending") })
                    FilterChip(selected = sort.descending, onClick = { onChange(sort.copy(descending = true)) }, label = { Text("Descending") })
                }
            }
            if (info?.roundRobin == true) {
                DropdownField(
                    label = "Group by",
                    selectedText = catalog.roundRobinGroupByFields.firstOrNull { it.value == sort.groupBy }?.label ?: sort.groupBy.orEmpty(),
                    options = catalog.roundRobinGroupByFields.map { it.value to it.label },
                    onSelect = { onChange(sort.copy(groupBy = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "Order inside each group",
                    selectedText = (catalog.withinGroupOrders.firstOrNull { it.value == (sort.within ?: "Natural") }?.label ?: "Natural order").substringBefore(" ("),
                    options = catalog.withinGroupOrders.map { it.value to it.label },
                    onSelect = { onChange(sort.copy(within = if (it == "Natural") null else it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
