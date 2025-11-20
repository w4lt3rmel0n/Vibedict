package com.waltermelon.vibedict.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.waltermelon.vibedict.WaltermelonApp
import com.waltermelon.vibedict.ui.settings.DictSettingsRow
import com.waltermelon.vibedict.ui.settings.SettingsViewModel
import com.waltermelon.vibedict.ui.theme.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionListScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as WaltermelonApp
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.SettingsViewModelFactory(app.userPreferencesRepository)
    )

    val collections by viewModel.collections.collectAsState()
    val activeCollectionId by viewModel.activeCollectionId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collections") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.createCollectionDetailRoute(Screen.COLLECTION_DETAIL_NEW_ID))
                    }) {
                        Icon(Icons.Default.Add, "Create Collection")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Matched spacing with Dictionary List
        ) {
            items(collections, key = { it.id }) { collection ->
                val isActive = collection.id == activeCollectionId

                // Removed SettingsCard wrapper to match standard list style
                DictSettingsRow(
                    icon = if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.CollectionsBookmark,
                    title = collection.name,
                    subtitle = if (collection.dictionaryIds.isEmpty()) "All dictionaries" else "${collection.dictionaryIds.size} dictionaries",
                    onClick = {
                        viewModel.setActiveCollection(collection.id)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                navController.navigate(Screen.createCollectionDetailRoute(collection.id))
                            }) {
                                Icon(Icons.Outlined.Edit, "Edit")
                            }
                            if (collection.id != "default_all") {
                                IconButton(onClick = { viewModel.deleteCollection(collection.id) }) {
                                    Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}