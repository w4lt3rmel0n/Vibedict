package com.waltermelon.vibedict.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.waltermelon.vibedict.WaltermelonApp
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.ui.theme.RobotoFlex
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.safeNavigate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.ui.res.stringResource
import com.waltermelon.vibedict.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryListScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as WaltermelonApp
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.SettingsViewModelFactory(app.userPreferencesRepository)
    )

    val savedDirs by viewModel.dictionaryDirs.collectAsState()

    // --- NEW: Observe the manager's loading state ---
    val isManagerLoading by DictionaryManager.isLoading.collectAsState()
    // ---

    // Just to trigger recomposition on list changes
    var triggerRecomposition by remember { mutableIntStateOf(0) }

    val loadedDictionaries by remember(triggerRecomposition, savedDirs, isManagerLoading) {
        derivedStateOf { DictionaryManager.loadedDictionaries }
    }

    LaunchedEffect(savedDirs) {
        triggerRecomposition++
    }

    var showMenu by remember { mutableStateOf(false) }
    var isManageMode by remember { mutableStateOf(false) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addDictionaryFolder(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_dictionaries)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_local_dict)) },
                            onClick = {
                                showMenu = false
                                directoryPickerLauncher.launch(null)
                            },
                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }
                        )
                        // --- Add Web Search Engine Option ---
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_web_engine)) },
                            onClick = {
                                showMenu = false
                                viewModel.addWebSearchEngine { newId ->
                                    navController.safeNavigate(Screen.createDictionaryDetailRoute(newId))
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Public, null) }
                        )
                        // --- NEW: Add AI Prompt Option ---
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_ai_prompt)) },
                            onClick = {
                                showMenu = false
                                viewModel.addAIPrompt { newId ->
                                    navController.safeNavigate(Screen.createDictionaryDetailRoute(newId))
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.SmartToy, null) }
                        )
                        // -----------------------------------------
                        DropdownMenuItem(
                            text = { Text(if (isManageMode) stringResource(R.string.done_managing) else stringResource(R.string.manage_folders)) },
                            onClick = {
                                showMenu = false
                                isManageMode = !isManageMode
                            },
                            leadingIcon = {
                                Icon(if (isManageMode) Icons.Outlined.Check else Icons.Outlined.Delete, null)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        // --- NEW: Full-screen loading check ---
        if (isManagerLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding), // Respect scaffold padding
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.loading_dictionaries),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isManageMode) {
                    item {
                        DictSettingsCard(title = stringResource(R.string.linked_folders)) {
                            if (savedDirs.isEmpty()) {
                                Text(stringResource(R.string.no_folders_linked), Modifier.padding(16.dp))
                            } else {
                                savedDirs.forEach { dirUri ->
                                    val decodedPath = try {
                                        Uri.decode(dirUri).substringAfterLast('/')
                                    } catch (e: Exception) {
                                        stringResource(R.string.invalid_path)
                                    }
                                    DictSettingsRow(
                                        icon = Icons.Outlined.Folder,
                                        title = decodedPath,
                                        subtitle = dirUri,
                                        trailingContent = {
                                            IconButton(onClick = {
                                                viewModel.removeDictionaryFolder(context, dirUri)
                                            }) {
                                                Icon(Icons.Outlined.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (loadedDictionaries.isEmpty()) {
                        DictSettingsCard(title = stringResource(R.string.status)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.no_dicts_found), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.add_folder_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.loaded_dictionaries),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = RobotoFlex),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                }

                items(loadedDictionaries, key = { it.id }) { dict ->
                    DictionaryRow(
                        navController = navController,
                        viewModel = viewModel,
                        dict = dict,
                        isManageMode = isManageMode
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryRow(
    navController: NavController,
    viewModel: SettingsViewModel,
    dict: DictionaryManager.LoadedDictionary,
    isManageMode: Boolean
) {
    val savedName by viewModel.getDictionaryName(dict.id).collectAsState()
    val displayName = if (savedName.isNotBlank()) savedName else dict.name

    // Determine icon based on dictionary type
    val icon = when {
        dict.webUrl != null -> Icons.Outlined.Public
        dict.aiPrompt != null -> Icons.Outlined.SmartToy
        else -> Icons.Outlined.Book
    }

    DictSettingsRow(
        icon = icon,
        title = displayName,
        subtitle = null,
        onClick = if (!isManageMode) {
            {
                val encodedId = URLEncoder.encode(dict.id, StandardCharsets.UTF_8.toString())
                navController.safeNavigate(Screen.createDictionaryDetailRoute(encodedId))
            }
        } else null,
        trailingContent = if (!isManageMode) {
            { ArrowIcon() }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryDetailScreen(navController: NavController, dictId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as WaltermelonApp
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.SettingsViewModelFactory(app.userPreferencesRepository)
    )

    val dictionary = remember(dictId) { DictionaryManager.getDictionaryById(dictId) }
    val llmProviders by viewModel.llmProviders.collectAsState()

    val isWebEngine = dictionary?.webUrl != null
    val isAIPrompt = dictionary?.aiPrompt != null

    // -- Display Name Logic --
    val savedName by viewModel.getDictionaryName(dictId).collectAsState(initial = "")
    val originalName = dictionary?.name ?: stringResource(R.string.unknown)
    var nameInput by remember { mutableStateOf("") }

    // FIX: Split initialization into two separate effects.

    // 1. Set default to Original Name if input is empty
    LaunchedEffect(originalName) {
        if (nameInput.isEmpty() && originalName != "Unknown") {
            nameInput = originalName
        }
    }

    // 2. Overwrite with Saved Name once it loads (if it exists)
    LaunchedEffect(savedName) {
        if (savedName.isNotBlank()) {
            nameInput = savedName
        }
    }

    val forceOriginalStyle by viewModel.getDictionaryForceStyle(dictId).collectAsState()

    // -- Inputs --
    var urlInput by remember { mutableStateOf(dictionary?.webUrl ?: "") }
    var promptInput by remember { mutableStateOf(dictionary?.aiPrompt?.promptTemplate ?: "") }
    var isHtmlInput by remember { mutableStateOf(dictionary?.aiPrompt?.isHtml ?: true) }
    var providerIdInput by remember { mutableStateOf(dictionary?.aiPrompt?.providerId ?: "") }
    var isProviderMenuExpanded by remember { mutableStateOf(false) }

    // -- Change Detection Logic --
    val hasChanges by remember {
        derivedStateOf {
            val currentName = if (savedName.isNotBlank()) savedName else originalName
            val nameChanged = nameInput != currentName

            val urlChanged = isWebEngine && urlInput != (dictionary?.webUrl ?: "")

            val promptChanged = isAIPrompt && (
                    promptInput != (dictionary?.aiPrompt?.promptTemplate ?: "") ||
                            isHtmlInput != (dictionary?.aiPrompt?.isHtml ?: true) ||
                            providerIdInput != (dictionary?.aiPrompt?.providerId ?: "")
                    )

            nameChanged || urlChanged || promptChanged
        }
    }

    // -- Save Logic Wrapper --
    val onSave: () -> Unit = {
        viewModel.setDictionaryName(dictId, nameInput)

        if (isWebEngine) {
            viewModel.updateWebSearchEngineUrl(dictId, urlInput)
        }

        if (isAIPrompt) {
            dictionary?.aiPrompt?.let { current ->
                viewModel.updateAIPrompt(
                    current.copy(
                        name = nameInput, // Ensure the internal prompt object name is updated too
                        promptTemplate = promptInput,
                        isHtml = isHtmlInput,
                        providerId = providerIdInput
                    )
                )
            }
        }
        navController.popBackStack()
    }

    // -- Back Navigation Logic --
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val onBack: () -> Unit = {
        if (hasChanges) {
            showUnsavedChangesDialog = true
        } else {
            navController.popBackStack()
        }
    }

    // Intercept System Back Button
    BackHandler(enabled = hasChanges, onBack = onBack)

    // -- Other States (CSS/JS/Fonts) --
    val savedCss by viewModel.getDictionaryCss(dictId).collectAsState()
    val savedJs by viewModel.getDictionaryJs(dictId).collectAsState()
    val savedFontPaths by viewModel.getDictionaryFontPaths(dictId).collectAsState()
    val fileCss = dictionary?.defaultCssContent ?: ""
    val fileJs = dictionary?.defaultJsContent ?: ""

    var showCssDialog by remember { mutableStateOf(false) }
    var showJsDialog by remember { mutableStateOf(false) }
    var contentToView by remember { mutableStateOf("") }

    var showRenameFontDialog by remember { mutableStateOf(false) }
    var fontToRenamePath by remember { mutableStateOf("") }
    var fontRenameInput by remember { mutableStateOf("") }

    // -- Launchers --
    val cssPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setDictionaryCss(dictId, readUriContent(context, it)) }
    }

    val jsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setDictionaryJs(dictId, readUriContent(context, it)) }
    }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDictionaryFont(context, dictId, it) }
    }

    val displayTitle = if (nameInput.isNotBlank()) nameInput else originalName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- General Section ---
            DictSettingsCard(title = stringResource(R.string.general)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when {
                                isWebEngine -> Icons.Outlined.Public
                                isAIPrompt -> Icons.Outlined.SmartToy
                                else -> Icons.Default.Upload
                            }
                            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                    TextField(
                        value = nameInput, onValueChange = { nameInput = it },
                        label = { Text(stringResource(R.string.display_name)) }, singleLine = true, maxLines = 1,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isWebEngine) {
                DictSettingsCard(title = stringResource(R.string.search_config)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = urlInput, onValueChange = { urlInput = it },
                            label = { Text(stringResource(R.string.search_link)) }, placeholder = { Text("https://example.com/search?q=%s") },
                            supportingText = { Text(stringResource(R.string.search_link_placeholder, "%s")) }, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.deleteWebSearchEngine(dictId); navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.delete_search_engine)) }
                    }
                }
            } else if (isAIPrompt) {
                DictSettingsCard(title = stringResource(R.string.ai_config)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val selectedProviderName = llmProviders.find { it.id == providerIdInput }?.name ?: stringResource(R.string.select_provider)
                        ExposedDropdownMenuBox(expanded = isProviderMenuExpanded, onExpandedChange = { isProviderMenuExpanded = !isProviderMenuExpanded }) {
                            OutlinedTextField(
                                value = selectedProviderName, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.llm_provider)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProviderMenuExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = isProviderMenuExpanded, onDismissRequest = { isProviderMenuExpanded = false }) {
                                llmProviders.forEach { provider ->
                                    DropdownMenuItem(text = { Text(provider.name) }, onClick = { providerIdInput = provider.id; isProviderMenuExpanded = false })
                                }
                                if (llmProviders.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.no_providers_added), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }, onClick = { isProviderMenuExpanded = false }, enabled = false)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = promptInput, onValueChange = { promptInput = it }, label = { Text(stringResource(R.string.prompt_template)) },
                            placeholder = { Text(stringResource(R.string.prompt_placeholder, "%s")) }, supportingText = { Text(stringResource(R.string.search_link_placeholder, "%s")) },
                            modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 10
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { isHtmlInput = !isHtmlInput }) {
                            Text(stringResource(R.string.render_html), modifier = Modifier.weight(1f))
                            Switch(checked = isHtmlInput, onCheckedChange = { isHtmlInput = it })
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.deleteAIPrompt(dictId); navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.delete_ai_prompt)) }
                    }
                }
            } else {
                DictSettingsCard(title = stringResource(R.string.file_info)) {
                    val mddCount = dictionary?.mddEngines?.size ?: 0
                    val mddSubtitle = if (mddCount == 0) stringResource(R.string.not_found) else stringResource(R.string.files_loaded, mddCount)

                    DictSettingsRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.mdx_file),
                        subtitle = if (dictionary?.mdxEngine != null) stringResource(R.string.loaded) else stringResource(R.string.not_found),
                        trailingContent = {
                            if (dictionary?.mdxEngine != null)
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            else
                                Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )

                    DictSettingsRow(
                        icon = Icons.Outlined.Image,
                        title = stringResource(R.string.mdd_file),
                        subtitle = mddSubtitle,
                        trailingContent = {
                            if (mddCount > 0)
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            else
                                Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }

            // --- Configuration Section ---
            DictSettingsCard(title = stringResource(R.string.configuration)) {
                DictSettingsRow(
                    icon = Icons.Outlined.FormatPaint, title = stringResource(R.string.force_original_style),
                    subtitle = stringResource(R.string.force_original_subtitle),
                    trailingContent = { Switch(checked = forceOriginalStyle, onCheckedChange = { viewModel.setDictionaryForceStyle(dictId, it) }) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // CSS & JS Rows
                val cssStatus = if (savedCss.isNotBlank() || fileCss.isNotBlank()) stringResource(R.string.css_loaded) else stringResource(R.string.no_css)
                DictSettingsRow(
                    icon = Icons.Outlined.Css, title = stringResource(R.string.css_config), subtitle = cssStatus,
                    trailingContent = {
                        Row {
                            IconButton(onClick = { cssPicker.launch(arrayOf("text/css", "*/*")) }) { Icon(Icons.Default.Upload, stringResource(R.string.upload)) }
                            if (savedCss.isNotBlank()) {
                                IconButton(onClick = { contentToView = savedCss; showCssDialog = true }) { Icon(Icons.Outlined.Visibility, stringResource(R.string.view)) }
                                IconButton(onClick = { viewModel.deleteDictionaryCss(dictId) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                            } else if (fileCss.isNotBlank()) {
                                IconButton(onClick = { contentToView = fileCss; showCssDialog = true }) { Icon(Icons.Outlined.Visibility, stringResource(R.string.view_file_css)) }
                                IconButton(onClick = { viewModel.setDictionaryCss(dictId, fileCss) }) { Icon(Icons.Outlined.SaveAlt, stringResource(R.string.apply_file_css)) }
                            }
                        }
                    }
                )

                val jsStatus = if (savedJs.isNotBlank() || fileJs.isNotBlank()) stringResource(R.string.js_loaded) else stringResource(R.string.no_js)
                DictSettingsRow(
                    icon = Icons.Outlined.Javascript, title = stringResource(R.string.js_config), subtitle = jsStatus,
                    trailingContent = {
                        Row {
                            IconButton(onClick = { jsPicker.launch(arrayOf("application/javascript", "text/plain", "*/*")) }) { Icon(Icons.Default.Upload, stringResource(R.string.upload)) }
                            if (savedJs.isNotBlank()) {
                                IconButton(onClick = { contentToView = savedJs; showJsDialog = true }) { Icon(Icons.Outlined.Visibility, stringResource(R.string.view)) }
                                IconButton(onClick = { viewModel.setDictionaryJs(dictId, "") }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                            } else if (fileJs.isNotBlank()) {
                                IconButton(onClick = { contentToView = fileJs; showJsDialog = true }) { Icon(Icons.Outlined.Visibility, stringResource(R.string.view_file_js)) }
                                IconButton(onClick = { viewModel.setDictionaryJs(dictId, fileJs) }) { Icon(Icons.Outlined.SaveAlt, stringResource(R.string.apply_file_js)) }
                            }
                        }
                    }
                )

                // --- CUSTOM FONTS ---
                val fontList = savedFontPaths.split(",").filter { it.isNotBlank() }
                val fontCount = fontList.size
                val fontSubtitle = if (fontCount == 0) stringResource(R.string.no_fonts_loaded) else stringResource(R.string.fonts_loaded, fontCount)

                DictSettingsRow(
                    icon = Icons.Outlined.FontDownload,
                    title = stringResource(R.string.custom_fonts),
                    subtitle = fontSubtitle,
                    trailingContent = {
                        IconButton(onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/font-sfnt", "*/*")) }) {
                            Icon(Icons.Default.Add, stringResource(R.string.add_font))
                        }
                    }
                )

                if (fontList.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        fontList.forEach { path ->
                            val fileName = path.substringAfterLast("/")
                            val fontName = fileName.substringBeforeLast(".")

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 56.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fontName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "font-family: '$fontName';",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            fontToRenamePath = path
                                            fontRenameInput = fontName
                                            showRenameFontDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.Edit, stringResource(R.string.rename), modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeDictionaryFont(context, dictId, path) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.Close, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs (Unsaved, Rename, View Code) ---
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onSave()
                    showUnsavedChangesDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedChangesDialog = false }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        navController.popBackStack()
                        showUnsavedChangesDialog = false
                    }) { Text(stringResource(R.string.dont_save)) }
                }
            }
        )
    }

    if (showRenameFontDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFontDialog = false },
            title = { Text(stringResource(R.string.rename_font)) },
            text = {
                Column {
                    Text(stringResource(R.string.rename_font_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fontRenameInput,
                        onValueChange = { fontRenameInput = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fontRenameInput.isNotBlank()) {
                        viewModel.renameDictionaryFont(context, dictId, fontToRenamePath, fontRenameInput)
                    }
                    showRenameFontDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFontDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCssDialog || showJsDialog) {
        Dialog(onDismissRequest = { showCssDialog = false; showJsDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = if (showCssDialog) stringResource(R.string.css_content) else stringResource(R.string.js_content), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(contentToView, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showCssDialog = false; showJsDialog = false }, Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
private fun readUriContent(context: Context, uri: Uri): String {
    val stringBuilder = StringBuilder()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line).append("\n")
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }
    return stringBuilder.toString()
}

// --- Helper Composables ---

@Composable
fun ArrowIcon() {
    Icon(
        Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = null,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun DictSettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = RobotoFlex),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            content()
        }
    }
}

@Composable
fun DictSettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            Box(contentAlignment = Alignment.CenterEnd) {
                trailingContent()
            }
        }
    }
}