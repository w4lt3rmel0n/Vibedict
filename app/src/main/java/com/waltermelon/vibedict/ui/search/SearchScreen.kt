package com.waltermelon.vibedict.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.data.UserPreferencesRepository //
import com.waltermelon.vibedict.ui.theme.RobotoFlex
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.safeNavigate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first // Needed for .first()
import kotlinx.coroutines.launch

// Data model for search results
// Data model for search results
data class SearchResult(val word: String, val source: String)
// --- NEW: Data model for merged results ---
data class MergedSearchResult(val word: String, val sources: List<String>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // <-- ADDED ExperimentalLayoutApi
@Composable
fun SearchScreen(
    navController: NavController,
    repository: UserPreferencesRepository
) {
    var searchQuery by remember { mutableStateOf("") }
    // --- UPDATED: Use new data model ---
    var searchResults by remember { mutableStateOf<List<MergedSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // --- NEW: Collect real history and scope ---
    val searchHistory by repository.history.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    // ------------------------------------------

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Auto-focus
    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // --- UPDATED: Search Logic ---
    // --- UPDATED: Search Logic ---
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 1) {
            isSearching = true
            delay(300) // Debounce

            // 1. Get Active Collection Filter
            val activeId = repository.activeCollectionId.first()
            val collection = repository.collections.first().find { it.id == activeId }
            val filterIds = collection?.dictionaryIds

            // 2. Perform Suggestion Lookup
            val rawSuggestions = DictionaryManager.getSuggestionsRaw(searchQuery) // <-- CHANGED

            // 3. Apply Collection Filter
            val filteredSuggestions = if (filterIds == null || filterIds.isEmpty()) {
                rawSuggestions // "All Dictionaries"
            } else {
                rawSuggestions.filter { it.second in filterIds } // it.second is the dict.id
            }

            // 4. Map to SearchResult and group by word
            // (Word, DictID) -> Map<Word, List<DictID>>
            val groupedByWord = filteredSuggestions.groupBy(
                keySelector = { it.first }, // group by Word
                valueTransform = { it.second } // value is List<DictID>
            )

            // 5. Get display names and map to final MergedSearchResult
            searchResults = groupedByWord.map { (word, dictIds) ->
                // Get the unique display names for this word
                val displayNames = dictIds.map { id ->
                    val customName = repository.getDictionaryName(id).first()
                    if (customName.isNotBlank()) customName else {
                        DictionaryManager.getDictionaryById(id)?.name ?: "Unknown"
                    }
                }.distinct() // Show each dictionary name only once

                MergedSearchResult(word, displayNames)
            }

            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    val onSearch = { searchTerm: String ->
        if (searchTerm.isNotBlank()) {
            keyboardController?.hide()
            if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                // History saving is now done in DefViewModel *after* a successful load
                navController.safeNavigate(Screen.createRouteForWord(searchTerm)) {
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        "Input text...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = RobotoFlex)
                    )
                },
                leadingIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowDownward, "Go back")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(searchQuery) }
                )
            )
        }
    ) { paddingValues ->
        val listModifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()

        if (searchQuery.isEmpty()) {
            // --- UPDATED: Show real history ---
            if (searchHistory.isEmpty()) {
                Box(modifier = listModifier, contentAlignment = Alignment.Center) {
                    Text(
                        "No history yet. Start searching!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = listModifier) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "History",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = {
                            coroutineScope.launch { repository.clearHistory() }
                        }) {
                            Text("Clear All")
                        }
                    }
                    SearchHistoryList(
                        historyItems = searchHistory,
                        modifier = Modifier.weight(1f),
                        onSearch = onSearch
                    )
                }
            }
            // ---------------------------------
        } else {
            if (searchResults.isEmpty() && !isSearching) {
                Box(modifier = listModifier, contentAlignment = Alignment.Center) {
                    Text("No definition found in selected collection.")
                }
            } else {
                SuggestionList(
                    suggestions = searchResults,
                    modifier = listModifier,
                    onSearch = onSearch
                )
            }
        }
    }
}

@Composable
fun SearchHistoryList(
    historyItems: List<String>,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        items(historyItems) { historyItem ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearch(historyItem) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.History, "History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Text(historyItem, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionList(
    suggestions: List<MergedSearchResult>, // <-- CHANGED
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion -> // <-- suggestion is now MergedSearchResult
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearch(suggestion.word) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(suggestion.word, style = MaterialTheme.typography.titleLarge)

                    // --- NEW: FlowRow for dictionary "pills" ---
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestion.sources.forEach { sourceName ->
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.labelSmall, // Changed to labelSmall
                                color = MaterialTheme.colorScheme.onPrimaryContainer, // Changed color
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer, // Changed color
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // ------------------------------------------
                }
            }
        }
    }
}