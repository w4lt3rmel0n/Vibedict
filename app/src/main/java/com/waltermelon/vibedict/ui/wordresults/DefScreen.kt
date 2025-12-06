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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.data.DictionaryManager
import androidx.compose.ui.res.stringResource
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DefScreen(
    navController: NavController,
    word: String,
    viewModel: DefViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val displayScale by viewModel.displayScale.collectAsState()

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
    // -------------------------------

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
                    focusRequester = searchFocusRequester
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
                    val scrollState = rememberScrollState()
                    val headerPositions = remember { mutableStateMapOf<Int, Float>() }
                    val headerHeights = remember { mutableStateMapOf<Int, Int>() }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // --- STICKY HEADER OVERLAY ---
                        val stickyIndex = state.results.indices.lastOrNull { index ->
                            val y = headerPositions[index] ?: Float.MAX_VALUE
                            y <= scrollState.value
                        } ?: -1

                        if (stickyIndex != -1) {
                            val stickyEntry = state.results[stickyIndex]
                            val isExpanded = expandedStates.getOrDefault(stickyEntry.dictionaryName, stickyEntry.isExpandedByDefault)
                            val selectedIndex = selectedIndices.getOrDefault(stickyEntry.id, 0)
                            
                            val nextHeaderY = headerPositions[stickyIndex + 1]
                            val currentHeaderHeight = headerHeights[stickyIndex] ?: 0
                            
                            val offset = if (nextHeaderY != null && currentHeaderHeight > 0) {
                                val intercept = nextHeaderY - scrollState.value
                                if (intercept < currentHeaderHeight) intercept - currentHeaderHeight else 0f
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

                                    Box(
                                        modifier = Modifier.onGloballyPositioned { coordinates ->
                                            headerPositions[index] = coordinates.positionInParent().y
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

                                    // Select content based on index, safe guard against OOB
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
                                        findNavEvent = findNavEvent,
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    focusRequester: FocusRequester
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

                            webViewClient = object : WebViewClient() {

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.startsWith("https://waltermelon.app/")) {
                                        android.util.Log.d("MdictJNI", "WebView Request Intercepted: $url")
                                    }

                                    // --- A. Serve Local Fonts ---
                                    if (url.startsWith("https://waltermelon.app/fonts/")) {
                                        val requestedFileNameEncoded = url.substringAfter("https://waltermelon.app/fonts/")
                                        android.util.Log.d("MdictJNI", "Intercepting font request: $requestedFileNameEncoded")
                                         try {
                                            val requestedFileName = java.net.URLDecoder.decode(requestedFileNameEncoded, "UTF-8")
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
                                    } else if (url.startsWith("https://waltermelon.app/")) {
                                        if (url != "https://waltermelon.app/") {
                                            resourceKey = url.substringAfter("https://waltermelon.app/")
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

                                    if (url != null && url != "https://waltermelon.app/") {
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
                            val isDisplayingLocalContent = webView.url == "https://waltermelon.app/"

                            if (!isDisplayingLocalContent) {
                                val transparencyCss = "html, body { background-color: transparent !important; }"

                                val darkModeCss = if (isDarkTheme && !forceOriginalStyle) {
                                    """
                                    html { filter: invert(1) hue-rotate(180deg); }
                                    img, video, iframe, .handwriting_img, .wordsource_img { filter: invert(1) hue-rotate(180deg); }
                                    """.trimIndent()
                                } else { "" }

                                // --- FONT INJECTION ---
                                val fontCss = if (customFontPaths.isNotEmpty()) {
                                    android.util.Log.d("MdictJNI", "Generating CSS for fonts: $customFontPaths")
                                    val fontList = customFontPaths.split(",").filter { it.isNotBlank() }
                                    val fontFaceDeclarations = fontList.joinToString("\n") { path ->
                                        val fontFileName = path.substringAfterLast('/')
                                        // Encode the filename for the URL, replacing "+" with "%20" as browsers expect
                                        val encodedFileName = java.net.URLEncoder.encode(fontFileName, "UTF-8").replace("+", "%20")
                                        val fontFamilyName = fontFileName.substringBeforeLast('.')
                                        """
                                        @font-face {
                                            font-family: '$fontFamilyName';
                                            src: url('https://waltermelon.app/fonts/$encodedFileName');
                                        }
                                        """
                                    }
                                    val firstFontFamily = fontList.firstOrNull()?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
                                    // Apply to body with high specificity
                                    "$fontFaceDeclarations\nbody { font-family: '$firstFontFamily', sans-serif !important; }"
                                } else { 
                                    android.util.Log.d("MdictJNI", "No custom fonts found.")
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

                                // Inject fontSizeCss BEFORE fontCss to ensure font properties on body take precedence if any conflict arose
                                val finalCss = "$sanitizedCustomCss\n$transparencyCss\n$darkModeCss\n$fontSizeCss\n$fontCss"
                                android.util.Log.d("MdictJNI", "Final CSS injected (last 200 chars): ${finalCss.takeLast(200)}")

                                val linkFixerJs = """
                                    <script>
                                    try {
                                        var links = document.getElementsByTagName('a');
                                        for (var i = 0; i < links.length; i++) {
                                            var href = links[i].getAttribute('href');
                                            if (href && (href.startsWith('content://') || href.startsWith('entry://')) && href.includes(' ')) {
                                                links[i].href = href.replace(/ /g, '%20');
                                            }
                                        }
                                    } catch (e) { console.error('Link fixer script failed', e); }
                                    </script>
                                """.trimIndent()

                                // FIX: Inject CSS at the end of body to override dictionary styles
                                val finalHtml = "<html><head></head><body>$content<style>$finalCss</style><script>$sanitizedCustomJs</script>$linkFixerJs</body></html>"

                                webView.loadDataWithBaseURL(
                                    "https://waltermelon.app/",
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