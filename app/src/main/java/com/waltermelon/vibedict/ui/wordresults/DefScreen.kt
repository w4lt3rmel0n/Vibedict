package com.waltermelon.vibedict.ui.wordresults

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.data.DictionaryManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.waltermelon.vibedict.R
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

// Helper to force "Next" clicks to be recognized as distinct events
data class FindNavEvent(val forward: Boolean, val timestamp: Long = System.currentTimeMillis())

    data class DictionaryEntry(
    val id: String, // --- NEW: ID for scoped lookup ---
    val dictionaryName: String,
    val iconRes: Int? = null,
    val entries: List<String>, // --- CHANGED: List of entries ---
    val customCss: String,
    val customJs: String,
    val isExpandedByDefault: Boolean,
    val forceOriginalStyle: Boolean,
    val customFontPaths: String = "",
    val isLoading: Boolean = false // --- NEW: Individual Loading State ---
)

// Helper function to convert Compose Color to hex string
private fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DefScreen(
    navController: NavController,
    word: String,
    viewModel: DefViewModel
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val displayScale by viewModel.displayScale.collectAsState()
    val useWebViewMode by viewModel.useWebViewMode.collectAsState()

    // Navigation Logic
    val navigateToWord by viewModel.navigateToWord.collectAsState()
    LaunchedEffect(navigateToWord) {
        if (navigateToWord != null) {
            val newWord = navigateToWord
            viewModel.onNavigationHandled()
            navController.navigate(Screen.createRouteForWord(newWord!!)) {
                popUpTo(Screen.createRouteForWord(word)) { inclusive = true }
            }
        }
    }

    // --- Find in Page State ---
    var isFindActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findNavEvent by remember { mutableStateOf<FindNavEvent?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    var activeMatchOrdinal by remember { mutableStateOf(0) }
    var numberOfMatches by remember { mutableStateOf(0) }
    // -------------------------------

    // Track nav events for WebView mode
    var webViewModeLastNavTimestamp by remember { mutableStateOf<Long?>(null) }

    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    // --- NEW: Track selected index for each dictionary ---
    val selectedIndices = remember { mutableStateMapOf<String, Int>() }
    
    val results = (uiState as? DefUiState.Success)?.results ?: emptyList()

    // Reset find state if results change
    LaunchedEffect(results) {
        if (results.isEmpty()) {
            isFindActive = false
            findQuery = ""
        }
    }

    Scaffold(
        topBar = {
            if (isFindActive) {
                FindInPageBar(
                    query = findQuery,
                    onQueryChange = { findQuery = it },
                    onClose = {
                        isFindActive = false
                        findQuery = ""
                    },
                    onNext = { findNavEvent = FindNavEvent(true) },
                    onPrev = { findNavEvent = FindNavEvent(false) },
                    focusRequester = searchFocusRequester,
                    matchStatus = if (numberOfMatches > 0) "${activeMatchOrdinal + 1}/$numberOfMatches" else ""
                )
            } else {
                TopAppBar(
                    title = { Text(text = word, style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = {
                            if (!navController.popBackStack(Screen.SEARCH, inclusive = false)) {
                                navController.navigate(Screen.SEARCH)
                            }
                        }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.new_search))
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.options))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) stringResource(R.string.remove_bookmark) else stringResource(R.string.add_bookmark)) },
                                    onClick = {
                                        viewModel.toggleBookmark()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(if (isBookmarked) Icons.Filled.BookmarkRemove else Icons.Outlined.BookmarkAdd, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmarks)) },
                                    onClick = {
                                        navController.navigate(Screen.BOOKMARK)
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.find_in_page)) },
                                    onClick = {
                                        isFindActive = true
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.FindInPage, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings)) },
                                    onClick = {
                                        navController.navigate(Screen.SETTINGS)
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is DefUiState.Loading -> {
                    // Removed global loading
                }
                is DefUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.SearchOff, null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.no_definition_found), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is DefUiState.Success -> {
                    val backgroundColor = MaterialTheme.colorScheme.background
                    val isDarkTheme = remember(backgroundColor) { backgroundColor.luminance() < 0.5f }
                    
                    // Collect Material You theme colors for WebView mode
                    val themeColors = ThemeColors(
                        background = colorToHex(MaterialTheme.colorScheme.background),
                        onBackground = colorToHex(MaterialTheme.colorScheme.onBackground),
                        primary = colorToHex(MaterialTheme.colorScheme.primary),
                        primaryContainer = colorToHex(MaterialTheme.colorScheme.primaryContainer),
                        onPrimaryContainer = colorToHex(MaterialTheme.colorScheme.onPrimaryContainer),
                        outline = colorToHex(MaterialTheme.colorScheme.outline),
                        onSurface = colorToHex(MaterialTheme.colorScheme.onSurface),
                        surface = colorToHex(MaterialTheme.colorScheme.surface)
                    )
                    
                    // Collect font paths for all entries for WebView mode
                    val fontPathsMap = remember { mutableStateMapOf<String, String>() }
                    state.results.forEach { entry ->
                        val paths by viewModel.getFontPaths(entry.id).collectAsState(initial = entry.customFontPaths)
                        LaunchedEffect(paths) {
                            fontPathsMap[entry.id] = paths
                        }
                    }
                    
                    if (useWebViewMode) {
                        // --- WEBVIEW MODE: Single full-page WebView with pinch-to-zoom ---
                        val coroutineScope = rememberCoroutineScope()
                        
                        AndroidView(
                            factory = { ctx ->
                                AdBlocker.init(ctx)
                                
                                CustomWebView(ctx).apply {
                                    onDefineRequested = { selectedText ->
                                        navController.navigate(Screen.createRouteForWord(selectedText))
                                    }
                                    @android.annotation.SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.setSupportZoom(true)
                                    settings.displayZoomControls = false
                                    settings.setSupportZoom(true)
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    setBackgroundColor(if (isDarkTheme) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
                                    
                                    setFindListener { activeMatch, numberOfMatches1, isDoneCounting ->
                                        activeMatchOrdinal = activeMatch
                                        numberOfMatches = numberOfMatches1
                                    }
                                    
                                    webViewClient = object : WebViewClient() {
                                        @android.annotation.SuppressLint("ResourceType")
                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                            val url = request?.url?.toString() ?: ""
                                            if (url.startsWith("https://app.vibedict/")) {
                                                android.util.Log.d("MdictJNI", "WebView Request Intercepted: $url")
                                            }
                                            
                                            if (url.startsWith("https://app.vibedict/entry_html?id=")) {
                                                val id = android.net.Uri.parse(url).getQueryParameter("id")
                                                if (id != null) {
                                                    val html = com.waltermelon.vibedict.ui.wordresults.WebViewModeRenderer.IframeCache.cache[id]
                                                    if (html != null) {
                                                        return WebResourceResponse("text/html", "UTF-8", java.io.ByteArrayInputStream(html.toByteArray()))
                                                    }
                                                }
                                            }
                                            
                                            // Serve local fonts
                                            if (url.startsWith("https://app.vibedict/fonts/")) {
                                                val requestedFileNameEncoded = url.substringAfter("https://app.vibedict/fonts/")
                                                android.util.Log.d("MdictJNI", "Intercepting font request: $requestedFileNameEncoded")
                                                try {
                                                    val requestedFileName = java.net.URLDecoder.decode(requestedFileNameEncoded, "UTF-8")
                                                    
                                                    if (requestedFileName == "roboto_flex.ttf") {
                                                        val inputStream = ctx.resources.openRawResource(R.font.roboto_flex)
                                                        return WebResourceResponse("font/ttf", "UTF-8", inputStream)
                                                    }
                                                    
                                                    val fontFile = File(ctx.filesDir, "fonts/$requestedFileName")
                                                    if (fontFile.exists()) {
                                                        val mime = when (requestedFileName.substringAfterLast('.').lowercase()) {
                                                            "ttf" -> "font/ttf"
                                                            "otf" -> "font/otf"
                                                            "woff" -> "font/woff"
                                                            "woff2" -> "font/woff2"
                                                            else -> "application/octet-stream"
                                                        }
                                                        return WebResourceResponse(mime, "UTF-8", FileInputStream(fontFile))
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            
                                            // Serve MDD resources - need to determine which dict this is for
                                            var resourceKey = ""
                                            if (url.startsWith("https://app.vibedict/")) {
                                                if (url != "https://app.vibedict/" && !url.startsWith("https://app.vibedict/entry_html")) {
                                                    resourceKey = url.substringAfter("https://app.vibedict/")
                                                }
                                            }
                                            
                                            if (resourceKey.isNotEmpty()) {
                                                try {
                                                    val decodedKey = java.net.URLDecoder.decode(resourceKey, "UTF-8")
                                                    val resourceData = DictionaryManager.getResourceByKey(decodedKey)
                                                    if (resourceData != null) {
                                                        val mimeType = when (decodedKey.substringAfterLast('.').lowercase()) {
                                                            "png" -> "image/png"
                                                            "jpg", "jpeg" -> "image/jpeg"
                                                            "gif" -> "image/gif"
                                                            "css" -> "text/css"
                                                            "js" -> "application/javascript"
                                                            else -> "application/octet-stream"
                                                        }
                                                        return WebResourceResponse(mimeType, null, ByteArrayInputStream(resourceData))
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            
                                            // AdBlocker
                                            if (AdBlocker.isAd(url)) {
                                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                            }
                                            
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                        
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: ""

                                            // Handle entry selection
                                            if (url.startsWith("vibedict://selectEntry/")) {
                                                val parts = url.removePrefix("vibedict://selectEntry/").split("/")
                                                if (parts.size == 2) {
                                                    val entryId = java.net.URLDecoder.decode(parts[0], "UTF-8")
                                                    val index = parts[1].toIntOrNull() ?: 0
                                                    selectedIndices[entryId] = index
                                                }
                                                return true
                                            }
                                            
                                            // Handle sound playback
                                            val isSound = url.startsWith("sound://") ||
                                                    (url.startsWith("content://") && (url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".spx")))
                                            
                                            if (isSound) {
                                                val resourceKey2 = when {
                                                    url.startsWith("sound://") -> url.substringAfter("sound://")
                                                    url.startsWith("content://mdict.cn/") -> url.substringAfter("content://mdict.cn/")
                                                    url.startsWith("content://") -> url.substringAfter("content://")
                                                    else -> url
                                                }
                                                
                                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    val audioData = DictionaryManager.getResourceByKey(java.net.URLDecoder.decode(resourceKey2, "UTF-8"))
                                                    if (audioData != null) {
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            playSound(ctx, audioData)
                                                        }
                                                    }
                                                }
                                                return true
                                            }
                                            
                                            // Handle entry links
                                            val entryWord = if (url.startsWith("entry://")) {
                                                url.substringAfter("entry://")
                                            } else if (url.startsWith("content://") && "/entry/" in url) {
                                                url.substringAfterLast("/")
                                            } else null
                                            
                                            if (entryWord != null) {
                                                try {
                                                    val decoded = java.net.URLDecoder.decode(entryWord, "UTF-8")
                                                    if (decoded.startsWith("#")) {
                                                        val hash = decoded.substring(1)
                                                        view?.evaluateJavascript("""
                                                            (function() {
                                                                var iframes = document.getElementsByClassName('entry-iframe');
                                                                for (var i=0; i<iframes.length; i++) {
                                                                    iframes[i].contentWindow.postMessage({type: 'scrollTo', hash: '$hash'}, '*');
                                                                }
                                                            })();
                                                        """.trimIndent(), null)
                                                        return true
                                                    }
                                                    navController.navigate(Screen.createRouteForWord(decoded))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                                return true
                                            }
                                            
                                            return super.shouldOverrideUrlLoading(view, request)
                                        }
                                    }
                                }
                            },
                            update = { webView ->
                                val html = WebViewModeRenderer.generateFullPageHtml(
                                    entries = state.results,
                                    expandedStates = expandedStates.toMap(),
                                    selectedIndices = selectedIndices.toMap(),
                                    fontPathsMap = fontPathsMap.toMap(),
                                    isDarkTheme = isDarkTheme,
                                    displayScale = displayScale,
                                    context = context,
                                    themeColors = themeColors
                                )
                                
                                // Prevent unnecessary reloads if HTML hasn't changed (e.g. during Find in Page)
                                val lastHtml = webView.getTag(R.id.webview_last_html_tag) as? String
                                if (html != lastHtml) {
                                    webView.loadDataWithBaseURL(
                                        "https://app.vibedict/",
                                        html,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                    webView.setTag(R.id.webview_last_html_tag, html)
                                }

                                // --- Find in Page Logic ---
                                val lastQuery = webView.getTag(R.id.webview_search_query_tag) as? String
                                if (findQuery != lastQuery) {
                                    if (findQuery.isNotEmpty()) {
                                        webView.findAllAsync(findQuery)
                                    } else {
                                        webView.clearMatches()
                                    }
                                    webView.setTag(R.id.webview_search_query_tag, findQuery)
                                }

                                val currentNavEvent = findNavEvent
                                if (currentNavEvent != null && currentNavEvent.timestamp != webViewModeLastNavTimestamp) {
                                    webView.findNext(currentNavEvent.forward)
                                    webViewModeLastNavTimestamp = currentNavEvent.timestamp
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // --- NATIVE MODE: Existing Compose + individual WebViews ---
                        val scrollState = rememberScrollState()
                        // --- STICKY HEADER OVERLAY ---
                        val headerHeights = remember { mutableStateMapOf<Int, Int>() }
                        val sectionPositions = remember { mutableStateMapOf<Int, Float>() }
                        val sectionHeights = remember { mutableStateMapOf<Int, Int>() }

                        val stickyIndex = state.results.indices.lastOrNull { index ->
                            val y = sectionPositions[index] ?: Float.MAX_VALUE
                            y <= scrollState.value
                        } ?: -1

                        if (stickyIndex != -1) {
                            val stickyEntry = state.results[stickyIndex]
                            val isExpanded = expandedStates.getOrDefault(stickyEntry.dictionaryName, stickyEntry.isExpandedByDefault)
                            val selectedIndex = selectedIndices.getOrDefault(stickyEntry.id, 0)
                            
                            val sectionTop = sectionPositions[stickyIndex] ?: 0f
                            val sectionHeight = sectionHeights[stickyIndex] ?: 0
                            val sectionBottom = sectionTop + sectionHeight
                            val currentHeaderHeight = headerHeights[stickyIndex] ?: 0
                            
                            // Calculate offset: push up if the bottom of the section hits the header
                            val offset = if (sectionHeight > 0 && currentHeaderHeight > 0) {
                                val distToBottom = sectionBottom - scrollState.value
                                if (distToBottom < currentHeaderHeight) distToBottom - currentHeaderHeight else 0f
                            } else {
                                0f
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(1f)
                                    .graphicsLayer { translationY = offset }
                            ) {
                                DictionaryHeaderItem(
                                    title = stickyEntry.dictionaryName,
                                    isExpanded = isExpanded,
                                    onToggle = { expandedStates[stickyEntry.dictionaryName] = !isExpanded },
                                    isLoading = stickyEntry.isLoading,
                                    entryCount = stickyEntry.entries.size,
                                    selectedIndex = selectedIndex,
                                    onSelectEntry = { newIndex -> selectedIndices[stickyEntry.id] = newIndex }
                                )
                            }
                        }
                        // -----------------------------

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            state.results.forEachIndexed { index, entry ->
                                key(entry.id) {
                                    val isExpanded = expandedStates.getOrDefault(entry.dictionaryName, entry.isExpandedByDefault)
                                    val selectedIndex = selectedIndices.getOrDefault(entry.id, 0)

                                    // Wrap the entire section (Header + Body) to track its bounds
                                    Column(
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            sectionPositions[index] = coordinates.positionInParent().y
                                            sectionHeights[index] = coordinates.size.height
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                                headerHeights[index] = coordinates.size.height
                                            }
                                        ) {
                                            DictionaryHeaderItem(
                                                title = entry.dictionaryName,
                                                isExpanded = isExpanded,
                                                onToggle = { expandedStates[entry.dictionaryName] = !isExpanded },
                                                isLoading = entry.isLoading,
                                                entryCount = entry.entries.size,
                                                selectedIndex = selectedIndex,
                                                onSelectEntry = { newIndex -> selectedIndices[entry.id] = newIndex }
                                            )
                                        }

                                        // --- NEW: Observe live font paths to handle updates immediately ---
                                        val dynamicFontPaths by viewModel.getFontPaths(entry.id).collectAsState(initial = entry.customFontPaths)

                                        // Select content based on index, safeguard against OOB
                                        val contentToShow = if (entry.entries.isNotEmpty()) {
                                            entry.entries.getOrElse(selectedIndex) { entry.entries[0] }
                                        } else {
                                            ""
                                        }

                                        DictionaryBodyItem(
                                            navController = navController,
                                            dictId = entry.id,
                                            content = contentToShow,
                                            customCss = entry.customCss,
                                            customJs = entry.customJs,
                                            isVisible = isExpanded,
                                            forceOriginalStyle = entry.forceOriginalStyle,
                                            customFontPaths = dynamicFontPaths, // --- CHANGED to live flow ---
                                            findQuery = findQuery, // --- NEW: Pass find query ---
                                            findNavEvent = findNavEvent,
                                            onFindResult = { active, total -> // --- NEW ---
                                                if (isExpanded) { // Only update if visible to avoid noise from hidden entries
                                                    activeMatchOrdinal = active
                                                    numberOfMatches = total
                                                }
                                            },
                                            isLoading = entry.isLoading,
                                            displayScale = displayScale // --- NEW ---
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    focusRequester: FocusRequester,
    matchStatus: String = ""
) {
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, stringResource(R.string.close))
            }

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.find_in_page_hint), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onNext() })
            )
            
            if (matchStatus.isNotEmpty()) {
                Text(
                    text = matchStatus,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            IconButton(onClick = onPrev) {
                Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.previous))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.next))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryHeaderItem(
    title: String,
    isExpanded: Boolean,
    isLoading: Boolean = false,
    entryCount: Int = 1,
    selectedIndex: Int = 0,
    onSelectEntry: (Int) -> Unit = {},
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .shadow(elevation = 2.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(bottom = if (entryCount > 1 && isExpanded) 8.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.expand),
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }

            // --- ENTRY SELECTION PILLS ---
            if (entryCount > 1 && isExpanded) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(entryCount) { index ->
                        val isSelected = index == selectedIndex
                        val color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .then(if (border != null) Modifier.border(border, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { onSelectEntry(index) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Entry ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor
                            )
                        }
                    }
                }
            }
            // -----------------------------

            if (isLoading && !isExpanded) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DictionaryBodyItem(
    navController: NavController,
    dictId: String, // --- NEW ---
    content: String,
    customCss: String,
    customJs: String,
    isVisible: Boolean,
    forceOriginalStyle: Boolean = false,
    customFontPaths: String = "",
    findQuery: String = "",
    findNavEvent: FindNavEvent? = null,
    onFindResult: (Int, Int) -> Unit = { _, _ -> }, // --- NEW ---
    isLoading: Boolean = false, // --- NEW ---
    displayScale: Float = 0.5f // --- NEW ---
) {
    val coroutineScope = rememberCoroutineScope()

    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkTheme = remember(backgroundColor) { backgroundColor.luminance() < 0.5f }

    // Track nav events to avoid re-processing the same click
    var lastProcessedNavEvent by remember { mutableStateOf<Long?>(null) }

    fun getMimeType(url: String): String {
        return when (url.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "spx" -> "audio/ogg"
            "mp3" -> "audio/mpeg"
            "css", "stylesheet" -> "text/css"
            "js" -> "application/javascript"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp), // Fixed height for loading state
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        AdBlocker.init(ctx)

                        CustomWebView(ctx).apply {
                            onDefineRequested = { selectedText ->
                                navController.navigate(Screen.createRouteForWord(selectedText))
                            }
                            @android.annotation.SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            setBackgroundColor(0x00000000)
                            isVerticalScrollBarEnabled = false
                            isNestedScrollingEnabled = false
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER

                            setFindListener { activeMatch, numberOfMatches1, isDoneCounting ->
                                onFindResult(activeMatch, numberOfMatches1)
                            }

                            webViewClient = object : WebViewClient() {

                                @android.annotation.SuppressLint("ResourceType")
                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.startsWith("https://app.vibedict/")) {
                                        android.util.Log.d("MdictJNI", "WebView Request Intercepted: $url")
                                    }

                                    // --- A. Serve Local Fonts ---
                                    if (url.startsWith("https://app.vibedict/fonts/")) {
                                        val requestedFileNameEncoded = url.substringAfter("https://app.vibedict/fonts/")
                                        android.util.Log.d("MdictJNI", "Intercepting font request: $requestedFileNameEncoded")
                                         try {
                                            val requestedFileName = java.net.URLDecoder.decode(requestedFileNameEncoded, "UTF-8")
                                            
                                            if (requestedFileName == "roboto_flex.ttf") {
                                                val inputStream = ctx.resources.openRawResource(R.font.roboto_flex)
                                                return WebResourceResponse("font/ttf", "UTF-8", inputStream)
                                            }

                                            val fontFile = File(ctx.filesDir, "fonts/$requestedFileName")
                                            
                                            if (fontFile.exists()) {
                                                // Use getMimeType to support .otf as well
                                                val mime = getMimeType(requestedFileName)
                                                return WebResourceResponse(mime, "UTF-8", FileInputStream(fontFile))
                                            } else {
                                                android.util.Log.e("MdictJNI", "Font file not found: ${fontFile.absolutePath}")
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    // B. Serve MDD Resources
                                    var resourceKey = ""
                                    if (url.startsWith("entry://")) {
                                        resourceKey = url.substringAfter("entry://")
                                    } else if (url.startsWith("https://app.vibedict/")) {
                                        if (url != "https://app.vibedict/") {
                                            resourceKey = url.substringAfter("https://app.vibedict/")
                                        }
                                    } else if (url.startsWith("content://")) {
                                        resourceKey = if (url.startsWith("content://mdict.cn/")) {
                                            url.substringAfter("content://mdict.cn/")
                                        } else {
                                            url.substringAfter("content://")
                                        }
                                    }

                                    if (resourceKey.isNotEmpty()) {
                                        try {
                                            val decodedKey = java.net.URLDecoder.decode(resourceKey, "UTF-8")
                                            // --- FIX: Use Scoped Lookup ---
                                            val resourceData = DictionaryManager.getResource(dictId, decodedKey)
                                            // ------------------------------
                                            if (resourceData != null) {
                                                val mimeType = getMimeType(decodedKey)
                                                return WebResourceResponse(mimeType, null, ByteArrayInputStream(resourceData))
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    // C. AdBlocker
                                    if (AdBlocker.isAd(url)) {
                                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    }

                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""

                                    val isSound = url.startsWith("sound://") ||
                                            (url.startsWith("content://") && (url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".spx")))

                                    if (isSound) {
                                        val resourceKey = when {
                                            url.startsWith("sound://") -> url.substringAfter("sound://")
                                            url.startsWith("content://mdict.cn/") -> url.substringAfter("content://mdict.cn/")
                                            url.startsWith("content://") -> url.substringAfter("content://")
                                            else -> url
                                        }

                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            // --- FIX: Use Scoped Lookup for Sound too ---
                                            val audioData = DictionaryManager.getResource(dictId, java.net.URLDecoder.decode(resourceKey, "UTF-8"))
                                            if (audioData != null) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    playSound(ctx, audioData)
                                                }
                                            }
                                        }
                                        return true
                                    }

                                    val entryWord = if (url.startsWith("entry://")) {
                                        url.substringAfter("entry://")
                                    } else if (url.startsWith("content://") && "/entry/" in url) {
                                        url.substringAfterLast("/")
                                    } else {
                                        null
                                    }

                                    if (entryWord != null) {
                                        try {
                                            val decoded = java.net.URLDecoder.decode(entryWord, "UTF-8")
                                            if (decoded.startsWith("#")) {
                                                val hash = decoded.substring(1)
                                                view?.evaluateJavascript("""
                                                    (function() {
                                                        var el = document.getElementById('$hash') || document.getElementsByName('$hash')[0];
                                                        if (!el) {
                                                            try { el = document.querySelector('[id="'+'$hash'.replace(/"/g, '\\"')+'"]'); } catch(e){}
                                                        }
                                                        if (el) el.scrollIntoView();
                                                    })();
                                                """.trimIndent(), null)
                                                return true
                                            }
                                            navController.navigate(Screen.createRouteForWord(decoded))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        return true
                                    }

                                    return super.shouldOverrideUrlLoading(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)

                                    if (url != null && url != "https://app.vibedict/") {
                                        val adCss = AdBlocker.COSMETIC_CSS.replace("\n", " ")
                                        view?.evaluateJavascript("""
                                        var style = document.createElement('style');
                                        style.innerHTML = "$adCss";
                                        document.head.appendChild(style);
                                        """, null)

                                        if (isDarkTheme && !forceOriginalStyle) {
                                            view?.evaluateJavascript("""
                                            var style = document.createElement('style');
                                            style.innerHTML = 'html { filter: invert(1) hue-rotate(180deg); } img, video { filter: invert(1) hue-rotate(180deg); }';
                                            document.head.appendChild(style);
                                            """, null)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    update = { webView ->
                        // --- UPDATE LOGIC ---

                        if (content.startsWith("@@WEB_URL@@")) {
                            val targetUrl = content.removePrefix("@@WEB_URL@@")
                            if (webView.url != targetUrl) {
                                webView.loadUrl(targetUrl)
                            }
                        }
                        else {
                            val isDisplayingLocalContent = webView.url == "https://app.vibedict/"

                            if (!isDisplayingLocalContent) {
                                val transparencyCss = "html, body { background-color: transparent !important; }"

                                val darkModeCss = if (isDarkTheme && !forceOriginalStyle) {
                                    """
                                    html { filter: invert(1) hue-rotate(180deg); }
                                    img, video, iframe, .handwriting_img, .wordsource_img { filter: invert(1) hue-rotate(180deg); }
                                    """.trimIndent()
                                } else { "" }

                                // --- FONT INJECTION ---
                                // 1. Base Default Font (Roboto Flex) - Injected FIRST as fallback if no other font specified
                                val defaultFontCss = """
                                    @font-face {
                                        font-family: 'Roboto Flex';
                                        font-style: normal;
                                        src: url('https://app.vibedict/fonts/roboto_flex.ttf');
                                    }
                                    @font-face {
                                        font-family: 'Roboto Flex';
                                        font-style: italic;
                                        src: url('https://app.vibedict/fonts/roboto_flex.ttf');
                                        font-variation-settings: 'slnt' -10;
                                    }
                                    body {
                                        font-family: 'Roboto Flex', sans-serif;
                                    }
                                """

                                // 2. User Custom Font Override - Injected LAST with !important to override everything
                                val userFontCss = if (customFontPaths.isNotEmpty()) {
                                    android.util.Log.d("MdictJNI", "Generating CSS for fonts: $customFontPaths")
                                    val fontList = customFontPaths.split(",").filter { it.isNotBlank() }
                                    val fontFaceDeclarations = fontList.joinToString("\n") { path ->
                                        val fontFileName = path.substringAfterLast('/')
                                        val fontFamilyName = fontFileName.substringBeforeLast('.')

                                        // Determine URL based on path type
                                        // "fonts/" prefix -> Global app font (in filesDir)
                                        // Otherwise -> Dictionary resource (relative path)
                                        val url = if (path.startsWith("fonts/")) {
                                            val encodedFileName = java.net.URLEncoder.encode(fontFileName, "UTF-8").replace("+", "%20")
                                            "https://app.vibedict/fonts/$encodedFileName"
                                        } else {
                                            // Dictionary resource: Use full relative path
                                            // Encode each segment to ensure valid URL
                                            val encodedPath = path.split("/").joinToString("/") {
                                                 java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                                            }
                                            "https://app.vibedict/$encodedPath"
                                        }

                                        """
                                        @font-face {
                                            font-family: '$fontFamilyName';
                                            src: url('$url');
                                        }
                                        """
                                    }
                                    val firstFontFamily = fontList.firstOrNull()?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
                                    // Apply to body with high specificity
                                    "$fontFaceDeclarations\nbody { font-family: '$firstFontFamily', sans-serif !important; }"
                                } else {
                                    android.util.Log.d("MdictJNI", "Using default app font as base: Roboto Flex")
                                    ""
                                }
                                // ----------------------

                                // Sanitize CSS and JS to remove any tags that might be present in the source files
                                val sanitizedCustomCss = customCss.replace("</?style[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
                                val sanitizedCustomJs = customJs.replace("</?script[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")

                                // --- DISPLAY ZOOM INJECTION ---
                                val zoomPercent = ((displayScale + 0.5f) * 100).toInt()
                                // FIX: Apply zoom to html ONLY to avoid conflicts with body styles/fonts
                                val fontSizeCss = "html { zoom: $zoomPercent%; }"
                                // ---------------------------

                                // ORDER: Default (Fallback) -> Custom (Dict) -> System (Dark/Zoom) -> User Override
                                val finalCss = "$defaultFontCss\n$sanitizedCustomCss\n$transparencyCss\n$darkModeCss\n$fontSizeCss\n$userFontCss"
                                android.util.Log.d("MdictJNI", "Final CSS injected (last 200 chars): ${finalCss.takeLast(200)}")

                                val linkFixerJs = """
                                    <script>
                                    try {
                                        document.addEventListener('click', function(e) {
                                            var target = e.target.closest('a');
                                            if (target && target.href) {
                                                var clickedUrl = target.getAttribute('href') || target.href;
                                                if (clickedUrl && (clickedUrl.startsWith('entry://') || clickedUrl.startsWith('sound://') || clickedUrl.startsWith('content://'))) {
                                                    e.preventDefault();
                                                    var finalUrl = clickedUrl;
                                                    
                                                    if (clickedUrl.startsWith('entry://')) {
                                                        var word = clickedUrl.substring(8);
                                                        try { word = decodeURIComponent(word); } catch(err) {}
                                                        finalUrl = 'entry://' + encodeURIComponent(word).replace(/['()~*!]/g, function(c) {
                                                            return '%' + c.charCodeAt(0).toString(16).toUpperCase();
                                                        });
                                                    } else if (clickedUrl.startsWith('sound://')) {
                                                        var word = clickedUrl.substring(8);
                                                        try { word = decodeURIComponent(word); } catch(err) {}
                                                        finalUrl = 'sound://' + encodeURIComponent(word).replace(/['()~*!]/g, function(c) {
                                                            return '%' + c.charCodeAt(0).toString(16).toUpperCase();
                                                        });
                                                    } else {
                                                        try {
                                                            finalUrl = encodeURI(decodeURI(clickedUrl));
                                                        } catch (err) {
                                                            finalUrl = encodeURI(clickedUrl);
                                                        }
                                                    }
                                                    window.location.href = finalUrl;
                                                }
                                            }
                                        });
                                    } catch (e) { console.error('Link fixer script failed', e); }
                                    </script>
                                """.trimIndent()

                                // FIX: Inject CSS at the end of body to override dictionary styles
                                val finalHtml = "<html><head></head><body>$content<style>$finalCss</style><script>$sanitizedCustomJs</script>$linkFixerJs</body></html>"

                                webView.loadDataWithBaseURL(
                                    "https://app.vibedict/",
                                    finalHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }

                        val lastQuery = webView.getTag(R.id.webview_search_query_tag) as? String
                        if (findQuery != lastQuery) {
                            if (findQuery.isNotEmpty()) {
                                webView.findAllAsync(findQuery)
                            } else {
                                webView.clearMatches()
                            }
                            webView.setTag(R.id.webview_search_query_tag, findQuery)
                        }

                        if (findNavEvent != null && findNavEvent.timestamp != lastProcessedNavEvent) {
                            webView.findNext(findNavEvent.forward)
                            lastProcessedNavEvent = findNavEvent.timestamp
                        }
                    },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )
            }
        }
    }
    if (isVisible) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}