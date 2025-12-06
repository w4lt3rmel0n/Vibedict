package com.waltermelon.vibedict.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.waltermelon.vibedict.ui.theme.Screen
import com.waltermelon.vibedict.ui.theme.RobotoFlex
import com.waltermelon.vibedict.ui.theme.safeNavigate
import androidx.compose.ui.res.stringResource
import com.waltermelon.vibedict.R
import com.waltermelon.vibedict.util.LogUtil
import kotlinx.coroutines.launch

@Composable
fun getLanguageDisplayName(value: String): String {
    return when (value) {
        "Follow System" -> stringResource(R.string.follow_system)
        "English" -> stringResource(R.string.lang_english)
        "简体中文" -> stringResource(R.string.lang_chinese_simp)
        "正體中文" -> stringResource(R.string.lang_chinese_trad)
        else -> value
    }
}

@Composable
fun getDarkModeDisplayName(value: String): String {
    return when (value) {
        "Follow System" -> stringResource(R.string.follow_system)
        "Light" -> stringResource(R.string.light_mode)
        "Dark" -> stringResource(R.string.dark_mode)
        else -> value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current

    // --- Observe DataStore States ---
    val currentDarkMode by viewModel.darkMode.collectAsState()
    val materialColour by viewModel.materialColour.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    val sliderPosition by viewModel.displayScale.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    val instantSearch by viewModel.instantSearch.collectAsState()
    val useWildcard by viewModel.useWildcard.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InterfaceSettingsCard(
                    currentLanguage = currentLanguage,
                    onLanguageClick = { showLanguageDialog = true },
                    currentDarkMode = currentDarkMode,
                    onDarkModeClick = { showDarkModeDialog = true },
                    materialColour = materialColour,
                    onMaterialColourChange = { viewModel.setMaterialColour(it) },
                    sliderPosition = sliderPosition,
                    onSliderPositionChange = { viewModel.setDisplayScale(it) },

                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { viewModel.setKeepScreenOn(it) }
                )
            }

            item {
                DictionariesSettingsCard(
                    onConfigureClick = { navController.safeNavigate(Screen.DICTIONARY_LIST) },
                    onCollectionClick = { navController.safeNavigate(Screen.COLLECTION_LIST) },
                    onLLMClick = { navController.safeNavigate(Screen.LLM_PROVIDER_LIST) }
                )
            }

            item {
                SearchSettingsCard(
                    instantSearch = instantSearch,
                    onInstantSearchChange = { viewModel.setInstantSearch(it) },
                    useWildcard = useWildcard,
                    onUseWildcardChange = { viewModel.setUseWildcard(it) }
                )
            }

            item {
                MiscActionsRow()
            }
        }
    }

    // --- DIALOGS ---

    if (showLanguageDialog) {
        val languageOptions = listOf(
            "Follow System" to stringResource(R.string.follow_system),
            "English" to stringResource(R.string.lang_english),
            "简体中文" to stringResource(R.string.lang_chinese_simp),
            "正體中文" to stringResource(R.string.lang_chinese_trad)
        )
        ListPreferenceDialog(
            title = stringResource(R.string.pref_language),
            options = languageOptions,
            selectedOption = currentLanguage,
            onOptionSelected = { newLanguage ->
                viewModel.setLanguage(newLanguage)
                showLanguageDialog = false
                if (newLanguage == "Follow System") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    }
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    if (showDarkModeDialog) {
        val darkModeOptions = listOf(
            "Follow System" to stringResource(R.string.follow_system),
            "Light" to stringResource(R.string.light_mode),
            "Dark" to stringResource(R.string.dark_mode)
        )
        ListPreferenceDialog(
            title = stringResource(R.string.pref_dark_mode),
            options = darkModeOptions,
            selectedOption = currentDarkMode,
            onOptionSelected = { newMode ->
                viewModel.setDarkMode(newMode)
                showDarkModeDialog = false
            },
            onDismiss = { showDarkModeDialog = false }
        )
    }
}

@Composable
private fun InterfaceSettingsCard(
    currentLanguage: String,
    onLanguageClick: () -> Unit,
    currentDarkMode: String,
    onDarkModeClick: () -> Unit,
    materialColour: Boolean,
    onMaterialColourChange: (Boolean) -> Unit,
    sliderPosition: Float,
    onSliderPositionChange: (Float) -> Unit,

    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit
) {
    SettingsCard(title = stringResource(R.string.pref_interface)) {
        SettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.pref_language),
            subtitle = getLanguageDisplayName(currentLanguage),
            onClick = onLanguageClick
        )
        SettingsRow(
            icon = Icons.Outlined.BrightnessMedium,
            title = stringResource(R.string.pref_dark_mode),
            subtitle = getDarkModeDisplayName(currentDarkMode),
            onClick = onDarkModeClick
        )
        SettingsRow(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.pref_material_colour),
            subtitle = stringResource(R.string.pref_material_subtitle),
            trailingContent = {
                Switch(
                    checked = materialColour,
                    onCheckedChange = onMaterialColourChange
                )
            }
        )


        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TextFields, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.pref_text_size), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
            Slider(
                value = sliderPosition,
                onValueChange = onSliderPositionChange,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsRow(
            icon = Icons.Outlined.Lightbulb,
            title = stringResource(R.string.pref_keep_screen_on),
            subtitle = stringResource(R.string.pref_keep_screen_on_subtitle),
            trailingContent = {
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun DictionariesSettingsCard(
    onConfigureClick: () -> Unit,
    onCollectionClick: () -> Unit,
    onLLMClick: () -> Unit
) {
    SettingsCard(title = stringResource(R.string.pref_dictionaries)) {
        SettingsRow(
            icon = Icons.Outlined.Book,
            title = stringResource(R.string.pref_manage_dicts),
            subtitle = stringResource(R.string.pref_manage_dicts_subtitle),
            onClick = onConfigureClick,
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
            }
        )
        SettingsRow(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(R.string.pref_manage_collections),
            subtitle = stringResource(R.string.pref_manage_collections_subtitle),
            onClick = onCollectionClick,
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
            }
        )
        SettingsRow(
            icon = Icons.Outlined.SmartToy,
            title = stringResource(R.string.pref_llm_service),
            subtitle = stringResource(R.string.pref_llm_service_subtitle),
            onClick = onLLMClick,
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, modifier = Modifier.size(16.dp))
            }
        )
    }
}

@Composable
private fun SearchSettingsCard(
    instantSearch: Boolean,
    onInstantSearchChange: (Boolean) -> Unit,
    useWildcard: Boolean,
    onUseWildcardChange: (Boolean) -> Unit
) {
    SettingsCard(title = stringResource(R.string.search)) {
        SettingsRow(
            icon = Icons.AutoMirrored.Outlined.Input,
            title = stringResource(R.string.pref_instant_search),
            subtitle = stringResource(R.string.pref_instant_search_subtitle),
            trailingContent = {
                Switch(checked = instantSearch, onCheckedChange = onInstantSearchChange)
            }
        )
        SettingsRow(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.wildcard_search),
            subtitle = stringResource(R.string.wildcard_subtitle),
            trailingContent = {
                Switch(checked = useWildcard, onCheckedChange = onUseWildcardChange)
            }
        )
    }
}

@Composable
private fun MiscActionsRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogDialog by remember { mutableStateOf(false) }

    if (showLogDialog) {
        LogDialog(onDismiss = { showLogDialog = false })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Info Button
        IconButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                context.startActivity(intent)
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.pref_app_info),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(32.dp))

        // GitHub Button
        IconButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/w4lt3rmel0n/Vibedict"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                painterResource(R.drawable.ic_github),
                contentDescription = "GitHub",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Bug/Log Button
        IconButton(
            onClick = { showLogDialog = true },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                Icons.Outlined.BugReport,
                contentDescription = "Report Bug",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedDays by remember { mutableIntStateOf(1) }
    var logs by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    
    // Day options
    val options = (1..7).toList()

    LaunchedEffect(selectedDays) {
        logs = LogUtil.getLogs(selectedDays)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Application Logs",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Time Frame Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = "$selectedDays Days",
                        onValueChange = { },
                        label = { Text("Time Frame") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { days ->
                            DropdownMenuItem(
                                text = { Text("$days Days") },
                                onClick = {
                                    selectedDays = days
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Log Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (logs == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        val scrollState = rememberScrollState()
                        Text(
                            text = logs ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        logs?.let { logContent ->
                            val file = LogUtil.exportLogs(context, logContent)
                            if (file != null) {
                                Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save logs", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Export .log")
                    }
                    
                    TextButton(onClick = {
                        logs?.let { logContent ->
                             LogUtil.shareLogText(context, logContent)
                        }
                    }) {
                        Text("Share")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

// --- Helper Functions ---

@Composable
fun SettingsCard(
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
fun SettingsRow(
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

@Composable
private fun ListPreferenceDialog(
    title: String,
    options: List<Pair<String, String>>, // Value to Display
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    options.forEach { (value, display) ->
                        RadioRow(
                            text = display,
                            selected = value == selectedOption,
                            onClick = { onOptionSelected(value) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 24.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}