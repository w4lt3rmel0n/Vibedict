package com.waltermelon.vibedict.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.waltermelon.vibedict.WaltermelonApp
import com.waltermelon.vibedict.data.DictCollection
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.ui.theme.Screen
import java.util.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A dedicated screen for creating or editing a collection.
 * Replaces the old AlertDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    navController: NavController,
    collectionId: String
) {
    val context = LocalContext.current
    val app = context.applicationContext as WaltermelonApp
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.SettingsViewModelFactory(app.userPreferencesRepository)
    )

    // Load the collection when the screen launches
    LaunchedEffect(collectionId) {
        viewModel.loadCollectionForEditing(collectionId)
    }

    // Observe the collection being edited
    val collection by viewModel.editingCollection.collectAsState()
    val isDefault = collection?.id == "default_all"
    val isNew = collectionId == Screen.COLLECTION_DETAIL_NEW_ID

    // --- Local state for editing ---
    var name by remember(collection) {
        mutableStateOf(collection?.name ?: "")
    }
    val selectedIds = remember(collection) {
        mutableStateListOf<String>().apply {
            collection?.dictionaryIds?.let { addAll(it) }
        }
    }
    val autoExpandIds = remember(collection) {
        mutableStateListOf<String>().apply {
            collection?.autoExpandIds?.let { addAll(it) }
        }
    }
    // -----------------------------

    val allDictionaries = remember { DictionaryManager.loadedDictionaries }

    val (selectedDicts, unselectedDicts) = remember(selectedIds.toList(), allDictionaries) {
        if (isDefault) {
            allDictionaries.sortedBy { it.name } to emptyList()
        } else {
            val selected = selectedIds.mapNotNull { id -> allDictionaries.find { it.id == id } }
            val unselected = allDictionaries.filter { it.id !in selectedIds }.sortedBy { it.name }
            selected to unselected
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        selectedIds.add(to.index, selectedIds.removeAt(from.index))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Collection" else "Edit Collection") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val id = collection?.id ?: UUID.randomUUID().toString()
                            viewModel.saveCollection(
                                DictCollection(
                                    id,
                                    name.ifBlank { "Unnamed" },
                                    if (isDefault) emptyList() else selectedIds.toList(),
                                    autoExpandIds.toList()
                                )
                            )
                            navController.popBackStack()
                        }
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Collection Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                readOnly = isDefault
            )

            HorizontalDivider()

            val listTitle = if (isDefault) "Configure Auto-Expand" else "Select Dictionaries & Set Order"
            Text(listTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

            // Header for the list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Dictionary",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Expand",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    "Load",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(72.dp)
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f)
            ) {
                if (isDefault) {
                    items(allDictionaries, key = { it.id }) { dict ->
                        val isAutoExpand = autoExpandIds.contains(dict.id)
                        DictionaryCheckRow(
                            dict = dict,
                            viewModel = viewModel,
                            isSelected = true,
                            isAutoExpand = isAutoExpand,
                            onSelectToggle = {},
                            onAutoExpandToggle = {
                                if (isAutoExpand) autoExpandIds.remove(dict.id) else autoExpandIds.add(dict.id)
                            }
                        )
                    }
                } else {
                    items(selectedDicts, key = { it.id }) { dict ->
                        ReorderableItem(reorderableState, key = dict.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp)
                            val isAutoExpand = autoExpandIds.contains(dict.id)
                            DictionaryCheckRow(
                                modifier = Modifier.longPressDraggableHandle(),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                dict = dict,
                                viewModel = viewModel,
                                isSelected = true,
                                isAutoExpand = isAutoExpand,
                                onSelectToggle = {
                                    selectedIds.remove(dict.id)
                                    autoExpandIds.remove(dict.id)
                                },
                                onAutoExpandToggle = {
                                    if (isAutoExpand) autoExpandIds.remove(dict.id) else autoExpandIds.add(dict.id)
                                }
                            )
                        }
                    }
                    if (unselectedDicts.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        items(unselectedDicts, key = { it.id }) { dict ->
                            DictionaryCheckRow(
                                dict = dict,
                                viewModel = viewModel,
                                isSelected = false,
                                isAutoExpand = false,
                                onSelectToggle = { selectedIds.add(dict.id) },
                                onAutoExpandToggle = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryCheckRow(
    modifier: Modifier = Modifier,
    elevation: CardElevation = CardDefaults.cardElevation(),
    dict: DictionaryManager.LoadedDictionary,
    viewModel: SettingsViewModel,
    isSelected: Boolean,
    isAutoExpand: Boolean,
    onSelectToggle: () -> Unit,
    onAutoExpandToggle: () -> Unit,
) {
    val savedName by viewModel.getDictionaryName(dict.id).collectAsState()
    val displayName = if (savedName.isNotBlank()) savedName else dict.name

    Card(
        modifier = modifier.padding(vertical = 4.dp),
        elevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier.width(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = isAutoExpand,
                    onCheckedChange = { onAutoExpandToggle() },
                    enabled = isSelected
                )
            }
            Box(
                modifier = Modifier.width(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
            }
        }
    }
}