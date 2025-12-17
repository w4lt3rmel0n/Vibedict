package com.waltermelon.vibedict.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextOverflow
import com.waltermelon.vibedict.R
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.safeNavigate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(navController: NavController, dictId: String) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<String>()) }
    val dictionary = remember(dictId) { DictionaryManager.getDictionaryById(dictId) }
    
    // Auto-search when query changes
    LaunchedEffect(query) {
        if (dictionary?.mdxEngine != null) {
            val rawResults = if (query.isEmpty()) {
                // When query is empty, get all keys using the dedicated function
                dictionary.mdxEngine.getAllKeys()
            } else {
                // Normal prefix-based search
                DictionaryManager.getSuggestionsRaw(query, listOf(dictId)).map { it.first }
            }
            results = rawResults
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dictionary?.name ?: "<dictionary>", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(results) { word ->
                    ListItem(
                        headlineContent = { Text(word) },
                        modifier = Modifier.clickable {
                            navController.safeNavigate(Screen.createEntryDetailRoute(dictId, word))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
