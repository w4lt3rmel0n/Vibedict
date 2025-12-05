package com.waltermelon.vibedict.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.waltermelon.vibedict.R
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.data.UserPreferencesRepository //
import com.waltermelon.vibedict.ui.theme.RobotoFlex
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.safeNavigate
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first // Needed for .first()
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // <-- ADDED ExperimentalLayoutApi
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val searchHistory by viewModel.searchHistory.collectAsState()
    val isRegexEnabled by viewModel.isRegexEnabled.collectAsState()
    val isFullText by viewModel.isFullText.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Auto-focus
    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
        keyboardController?.show()
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

    val searchProgress by DictionaryManager.searchProgress.collectAsState() // Observe progress

    // Shimmer Effect for Progress Bar
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onBackground,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.background,
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Custom Search Bar with Background Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp) // Standard TextField height
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant) // Base background
                ) {
                    // 2. The TextField (Transparent Background)
                    TextField(
                        value = searchQuery,
                        onValueChange = { 
                            // Block input ONLY if searching AND full-text
                            if (!(isSearching && isFullText)) {
                                viewModel.onSearchQueryChanged(it)
                            }
                        },
                        enabled = true, // Always "enabled" to keep focus/keyboard, but we block changes manually above
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                stringResource(R.string.input_text_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = RobotoFlex)
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.go_back))
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent, // Transparent to show progress behind
                            focusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSearch(searchQuery) }
                        )
                    )

                    // 3. Progress Bar (Bottom Strip)
                    if (isSearching && isFullText) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(searchProgress) // Animate width based on progress
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // --- NEW: Filter Row ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Dictionary Filter Dropdown
                    val collections by viewModel.collections.collectAsState()
                    val activeCollectionId by viewModel.activeCollectionId.collectAsState()
                    var isDictionaryDropdownExpanded by remember { mutableStateOf(false) }

                    val allDictionariesText = stringResource(R.string.all_dictionaries)
                    val activeCollectionName = remember(collections, activeCollectionId, allDictionariesText) {
                        collections.find { it.id == activeCollectionId }?.name ?: allDictionariesText
                    }

                    Box {
                        OutlinedButton(
                            onClick = { isDictionaryDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = activeCollectionName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp), // Slightly larger for standard dropdown arrow
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = isDictionaryDropdownExpanded,
                            onDismissRequest = { isDictionaryDropdownExpanded = false }
                        ) {
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text(collection.name) },
                                    onClick = {
                                        viewModel.setActiveCollection(collection.id)
                                        isDictionaryDropdownExpanded = false
                                    },
                                    leadingIcon = if (collection.id == activeCollectionId) {
                                        { Icon(Icons.Filled.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }

                    // 2. Fulltext Filter
                    FilterChip(
                        selected = isFullText,
                        onClick = { viewModel.setFullText(!isFullText) },
                        label = { Text(stringResource(R.string.fulltext)) },
                        leadingIcon = if (isFullText) {
                            { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )

                    // 3. Regex Filter
                    FilterChip(
                        selected = isRegexEnabled,
                        onClick = { viewModel.setRegexEnabled(!isRegexEnabled) },
                        label = { Text(stringResource(R.string.regex)) },
                        leadingIcon = if (isRegexEnabled) {
                            { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
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
                        stringResource(R.string.no_history),
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
                            stringResource(R.string.history),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = {
                            viewModel.clearHistory()
                        }) {
                            Text(stringResource(R.string.clear_all))
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
                    Text(stringResource(R.string.no_def_found_collection))
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
                Icon(Icons.Outlined.History, stringResource(R.string.history), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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