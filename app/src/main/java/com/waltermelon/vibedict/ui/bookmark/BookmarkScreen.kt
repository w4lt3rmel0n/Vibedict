package com.waltermelon.vibedict.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Bookmarks // NEW IMPORT
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.R
import com.waltermelon.vibedict.ui.theme.safeNavigate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    navController: NavController,
    // ViewModel is now injected
    viewModel: BookmarkViewModel
) {
    // Collect the list of words from the ViewModel
    val bookmarkedWords by viewModel.bookmarkedWords.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarks)) },
                // --- CHANGED: Use Bookmarks icon on left ---
                navigationIcon = {
                    Icon(
                        Icons.Outlined.Bookmarks,
                        "Bookmarks",
                        modifier = Modifier.padding(start = 12.dp) // Add padding
                    )
                },
                // --- CHANGED: Move Back arrow to right ---
                actions = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBackIos, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (bookmarkedWords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "You haven't bookmarked any words yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(bookmarkedWords, key = { it }) { word ->
                    BookmarkItem(
                        word = word,
                        onClick = {
                            navController.safeNavigate(Screen.createRouteForWord(word))
                        },
                        onRemove = {
                            viewModel.removeBookmark(word)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(
    word: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp), // Reduced vertical padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = word,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        // Added remove button
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Remove bookmark",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}