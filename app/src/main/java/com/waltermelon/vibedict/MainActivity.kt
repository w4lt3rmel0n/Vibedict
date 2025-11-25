/*
 * Copyright (C) 2025 Walter Melon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.waltermelon.vibedict

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.ui.main.BookmarkScreen
import com.waltermelon.vibedict.ui.main.BookmarkViewModelFactory
import com.waltermelon.vibedict.ui.main.MainScreen
import com.waltermelon.vibedict.ui.search.SearchScreen
import com.waltermelon.vibedict.ui.settings.CollectionDetailScreen
import com.waltermelon.vibedict.ui.settings.CollectionListScreen
import com.waltermelon.vibedict.ui.settings.DictionaryDetailScreen
import com.waltermelon.vibedict.ui.settings.DictionaryListScreen
import com.waltermelon.vibedict.ui.settings.SettingsScreen
import com.waltermelon.vibedict.ui.settings.SettingsViewModel
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.WaltermelonTheme
import com.waltermelon.vibedict.ui.wordresults.DefScreen
import com.waltermelon.vibedict.ui.wordresults.DefViewModel
import com.waltermelon.vibedict.ui.wordresults.DefViewModelFactory
import com.waltermelon.vibedict.ui.wordresults.AdBlocker
import com.waltermelon.vibedict.ui.settings.LLMProviderListScreen
import com.waltermelon.vibedict.ui.settings.LLMProviderConfigScreen
import com.waltermelon.vibedict.ui.settings.AIPromptConfigScreen
import kotlinx.coroutines.flow.first

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdBlocker.init(this)
        val app = application as WaltermelonApp
        // The repository is now passed to ViewModels
        val repository = app.userPreferencesRepository

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.SettingsViewModelFactory(repository)
            )
            val currentDarkMode by repository.darkMode.collectAsState(initial = "Follow System")
            val materialColour by repository.materialColour.collectAsState(initial = true)
            val instantSearch by repository.instantSearch.collectAsState(initial = false)


            LaunchedEffect(Unit) {
                // Load ALL data from repository
                val savedDirs = repository.dictionaryDirectories.first()
                val savedWebEngines = repository.webSearchEngines.first()
                val savedPrompts = repository.aiPrompts.first()      // <--- NEW
                val savedProviders = repository.llmProviders.first() // <--- NEW

                // Always reload to ensure DictionaryManager is in sync with saved preferences
                DictionaryManager.reloadDictionaries(
                    context = this@MainActivity,
                    folderUris = savedDirs,
                    webEngines = savedWebEngines,
                    aiPrompts = savedPrompts,
                    llmProviders = savedProviders
                )
            }

            val useDarkTheme = when (currentDarkMode) {
                getString(R.string.light) -> false
                getString(R.string.dark) -> true
                else -> isSystemInDarkTheme()
            }

            WaltermelonTheme(
                darkTheme = useDarkTheme,
                dynamicColor = materialColour
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var hasPerformedAutoNav by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(instantSearch, hasPerformedAutoNav) {
                        if (instantSearch && !hasPerformedAutoNav) {
                            navController.navigate(Screen.SEARCH)
                            hasPerformedAutoNav = true
                        }

                        // Handle PROCESS_TEXT intent (Global "Define" menu)
                        if (intent?.action == android.content.Intent.ACTION_PROCESS_TEXT) {
                            val text = intent?.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString()
                            if (!text.isNullOrBlank()) {
                                navController.navigate(Screen.createRouteForWord(text))
                                // Clear the intent action so we don't re-navigate on configuration changes
                                intent?.action = ""
                            }
                        }
                    }

                    val isLoading by DictionaryManager.isLoading.collectAsState()

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.MAIN,
                            // Default transitions (slide right-to-left)
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(500, easing = EaseInOutCubic)
                                ) + fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth },
                                    animationSpec = tween(500, easing = EaseInOutCubic)
                                ) + fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -fullWidth },
                                    animationSpec = tween(500, easing = EaseInOutCubic)
                                ) + fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(500, easing = EaseInOutCubic)
                                ) + fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                            }
                        ) {
                            // --- 1. MainScreen (Unchanged animation) ---
                            composable(
                                route = Screen.MAIN,
                                exitTransition = {
                                    when (targetState.destination.route) {
                                        // Slide RIGHT for Bookmark
                                        Screen.BOOKMARK -> slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                                        // FADE for Search
                                        Screen.SEARCH -> fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                                        // Default slide LEFT
                                        else -> slideOutHorizontally(
                                            targetOffsetX = { -it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                                    }
                                },
                                popEnterTransition = {
                                    when (initialState.destination.route) {
                                        // Slide IN from RIGHT
                                        Screen.BOOKMARK -> slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                                        // FADE IN from Search
                                        Screen.SEARCH -> fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                                        // Default slide IN from LEFT
                                        else -> slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                                    }
                                }
                            ) { MainScreen(navController = navController) }

                            // --- 2. SearchScreen (Unchanged animation) ---
                            composable(
                                route = Screen.SEARCH,
                                enterTransition = {
                                    // MODIFIED: Slide up ONLY if coming from MAIN
                                    if (initialState.destination.route == Screen.MAIN) {
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeIn(animationSpec = tween(500))
                                    } else {
                                        // Default horizontal slide IN from RIGHT
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeIn(animationSpec = tween(500, easing = EaseInOutCubic))
                                    }
                                },
                                exitTransition = {
                                    // Default slide LEFT (when going to DefScreen)
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeOut(animationSpec = tween(500))
                                },
                                popEnterTransition = {
                                    // Default slide IN from LEFT (when returning from DefScreen)
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeIn(animationSpec = tween(500))
                                },
                                popExitTransition = {
                                    // MODIFIED: Slide down ONLY if returning to MAIN
                                    if (targetState.destination.route == Screen.MAIN) {
                                        slideOutVertically(
                                            targetOffsetY = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeOut(animationSpec = tween(500))
                                    } else {
                                        // Default horizontal slide OUT to RIGHT
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                                    }
                                }
                            ) { SearchScreen(navController = navController, repository = repository) }

                            // --- 3. DefScreen (Unchanged animation) ---
                            composable(
                                route = Screen.WORD_RESULT_ROUTE,
                                arguments = listOf(navArgument("word") { type = NavType.StringType }),
                                enterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeIn(animationSpec = tween(500))
                                },
                                exitTransition = {
                                    when (targetState.destination.route) {
                                        // --- MODIFIED: Stay still when going to Bookmark ---
                                        Screen.BOOKMARK -> ExitTransition.None
                                        // Default slide LEFT
                                        else -> slideOutHorizontally(
                                            targetOffsetX = { -it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeOut(animationSpec = tween(500))
                                    }
                                },
                                popEnterTransition = {
                                    when (initialState.destination.route) {
                                        // --- MODIFIED: Stay still when returning from Bookmark ---
                                        Screen.BOOKMARK -> EnterTransition.None
                                        // Default slide IN from LEFT
                                        else -> slideInHorizontally(
                                            initialOffsetX = { -it },
                                            animationSpec = tween(500, easing = EaseInOutCubic)
                                        ) + fadeIn(animationSpec = tween(500))
                                    }
                                },
                                popExitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeOut(animationSpec = tween(500))
                                }
                            ) { backStackEntry ->
                                val word = backStackEntry.arguments?.getString("word") ?: ""
                                val viewModel: DefViewModel = viewModel(
                                    factory = DefViewModelFactory(word, repository)
                                )
                                DefScreen(navController = navController, word = word, viewModel = viewModel)
                            }

                            // --- Other Screens ---
                            composable(Screen.SETTINGS) {
                                val viewModel: SettingsViewModel = viewModel(
                                    factory = SettingsViewModel.SettingsViewModelFactory(repository)
                                )
                                SettingsScreen(navController = navController, viewModel = viewModel)
                            }

                            composable(Screen.DICTIONARY_LIST) {
                                DictionaryListScreen(navController = navController)
                            }

                            composable(Screen.COLLECTION_LIST) {
                                CollectionListScreen(navController = navController)
                            }

                            // --- NEW: Collection Detail Screen ---
                            composable(
                                route = Screen.COLLECTION_DETAIL_ROUTE,
                                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                                CollectionDetailScreen(navController, collectionId)
                            }

                            // --- NEW: LLM & AI Prompt Screens ---
                            composable(Screen.LLM_PROVIDER_LIST) {
                                LLMProviderListScreen(navController, settingsViewModel)
                            }

                            composable(
                                route = Screen.LLM_PROVIDER_CONFIG,
                                arguments = listOf(navArgument("providerId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
                                LLMProviderConfigScreen(navController, settingsViewModel, providerId)
                            }

                            composable(
                                route = Screen.AI_PROMPT_CONFIG,
                                arguments = listOf(navArgument("promptId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val promptId = backStackEntry.arguments?.getString("promptId") ?: ""
                                AIPromptConfigScreen(navController, settingsViewModel, promptId)
                            }

                            composable(
                                route = Screen.DICTIONARY_DETAIL,
                                arguments = listOf(navArgument("dictId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val dictId = backStackEntry.arguments?.getString("dictId") ?: ""
                                DictionaryDetailScreen(navController = navController, dictId = dictId)
                            }

                            // --- Bookmark (Unchanged animation) ---
                            composable(
                                route = Screen.BOOKMARK,
                                enterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeIn(animationSpec = tween(500))
                                },
                                exitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeOut(animationSpec = tween(500))
                                },
                                popEnterTransition = {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeIn(animationSpec = tween(500))
                                },
                                popExitTransition = {
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(500, easing = EaseInOutCubic)
                                    ) + fadeOut(animationSpec = tween(500))
                                }
                            ) {
                                val viewModel: com.waltermelon.vibedict.ui.main.BookmarkViewModel = viewModel(
                                    factory = BookmarkViewModelFactory(repository)
                                )
                                BookmarkScreen(navController = navController, viewModel = viewModel)
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                                    .clickable(enabled = false) {},
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.loading_dictionaries), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
