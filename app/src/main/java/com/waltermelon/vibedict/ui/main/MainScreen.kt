package com.waltermelon.vibedict.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.waltermelon.vibedict.WaltermelonApp
import com.waltermelon.vibedict.ui.theme.RobotoFlex
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.WaltermelonTheme
import com.waltermelon.vibedict.ui.theme.safeNavigate
import com.waltermelon.vibedict.ui.theme.titleTag
import com.waltermelon.vibedict.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController
) {
    // --- NEW: Get repository and observe collection state ---
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as WaltermelonApp).userPreferencesRepository }

    val collections by repository.collections.collectAsState(initial = emptyList())
    val activeId by repository.activeCollectionId.collectAsState(initial = "default_all")

    // Find current active name
    val activeCollectionName = remember(collections, activeId) {
        collections.find { it.id == activeId }?.name ?: "All Dictionaries"
    }
    // --------------------------------------------------------

    var isFullText by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            title = { },
            navigationIcon = {
                IconButton(onClick = { navController.safeNavigate(Screen.BOOKMARK) }) {
                    Icon(Icons.Outlined.Bookmarks, "Bookmark")
                }
            },
            actions = {
                IconButton(onClick = { navController.safeNavigate(Screen.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- UPDATED: Collection Selector Dropdown ---
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)) // Clip the ripple for this row too
                        .clickable { showCollectionMenu = true }
                        .padding(8.dp), // Add padding inside the click area
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeCollectionName, // Use dynamic name
                        style = titleTag,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown, "Select set",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                DropdownMenu(
                    expanded = showCollectionMenu,
                    onDismissRequest = { showCollectionMenu = false }
                ) {
                    collections.forEach { collection ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    collection.name,
                                    fontWeight = if (collection.id == activeId) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showCollectionMenu = false
                                // Set the active collection
                                coroutineScope.launch { repository.setActiveCollection(collection.id) }
                            }
                        )
                    }
                }
            }
            // ---------------------------------------------

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    // FIX: Clip BEFORE clickable to contain the ripple within the rounded shape
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                            navController.safeNavigate(Screen.SEARCH)
                        }
                    }
                    .padding(horizontal = 16.dp)
            ) {
                SearchBarContent()
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 50.dp)
                .background(
                    color = if (isFullText) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.search_full_text),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = RobotoFlex),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = isFullText,
                onCheckedChange = { isFullText = it }
            )
        }
    }
}

@Composable
private fun SearchBarContent() {
    Row(
        modifier = Modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Search, "Search Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            stringResource(R.string.search),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = RobotoFlex),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WaltermelonTheme {
        MainScreen(navController = rememberNavController())
    }
}