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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    val allDictionariesText = stringResource(R.string.all_dictionaries)
    val activeCollectionName = remember(collections, activeId, allDictionariesText) {
        collections.find { it.id == activeId }?.name ?: allDictionariesText
    }
    // --------------------------------------------------------

    // --- UPDATED: Get settings from repository ---
    val isFullText by repository.isFullText.collectAsState(initial = false)
    val isRegexEnabled by repository.isRegexEnabled.collectAsState(initial = false)
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
                    Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmark))
                }
            },
            actions = {
                IconButton(onClick = { navController.safeNavigate(Screen.SETTINGS) }) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.settings_icon))
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
                        Icons.Outlined.ArrowDropDown, stringResource(R.string.select_set),
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

            Spacer(modifier = Modifier.height(16.dp))

            // --- NEW: Filter Pills ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Fulltext Filter
                FilterChip(
                    selected = isFullText,
                    onClick = { coroutineScope.launch { repository.setFullText(!isFullText) } },
                    label = { Text(stringResource(R.string.search_full_text)) },
                    leadingIcon = if (isFullText) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Regex Filter
                FilterChip(
                    selected = isRegexEnabled,
                    onClick = { coroutineScope.launch { repository.setRegexEnabled(!isRegexEnabled) } },
                    label = { Text(stringResource(R.string.regex)) },
                    leadingIcon = if (isRegexEnabled) {
                        { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                )
            }
            // -------------------------
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
            Icons.Outlined.Search, stringResource(R.string.search_icon),
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