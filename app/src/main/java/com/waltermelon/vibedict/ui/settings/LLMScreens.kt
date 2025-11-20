package com.waltermelon.vibedict.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.waltermelon.vibedict.ui.theme.safeNavigate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMProviderListScreen(
    navController: NavController,
    viewModel: SettingsViewModel
) {
    val providers by viewModel.llmProviders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Services") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.addLLMProvider { newId ->
                            navController.safeNavigate("llm_provider_config/$newId")
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Matched spacing
        ) {
            items(providers) { provider ->
                DictSettingsRow(
                    title = provider.name,
                    subtitle = "${provider.type} - ${provider.model}",
                    onClick = {
                        navController.safeNavigate("llm_provider_config/${provider.id}")
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LLMProviderConfigScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    providerId: String
) {
    val providers by viewModel.llmProviders.collectAsState()
    val provider = providers.find { it.id == providerId }

    if (provider == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Provider not found")
        }
        return
    }

    var name by remember { mutableStateOf(provider.name) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var type by remember { mutableStateOf(provider.type) }

    val providerTypes = listOf("Google", "Mistral", "Groq", "Celebras")
    var isTypeExpanded by remember { mutableStateOf(false) }

    fun update() {
        viewModel.updateLLMProvider(
            provider.copy(
                name = name,
                apiKey = apiKey,
                model = model,
                type = type
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Service") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteLLMProvider(provider.id)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; update() },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = isTypeExpanded,
                onExpandedChange = { isTypeExpanded = !isTypeExpanded }
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isTypeExpanded,
                    onDismissRequest = { isTypeExpanded = false }
                ) {
                    providerTypes.forEach { selection ->
                        DropdownMenuItem(
                            text = { Text(selection) },
                            onClick = {
                                type = selection
                                update()
                                isTypeExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; update() },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it; update() },
                label = { Text("Model Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptConfigScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    promptId: String
) {
    val prompts by viewModel.aiPrompts.collectAsState()
    val providers by viewModel.llmProviders.collectAsState()
    val prompt = prompts.find { it.id == promptId }

    if (prompt == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Prompt not found")
        }
        return
    }

    var name by remember { mutableStateOf(prompt.name) }
    var promptTemplate by remember { mutableStateOf(prompt.promptTemplate) }
    var providerId by remember { mutableStateOf(prompt.providerId) }
    var isHtml by remember { mutableStateOf(prompt.isHtml) }

    fun update() {
        viewModel.updateAIPrompt(
            prompt.copy(
                name = name,
                promptTemplate = promptTemplate,
                providerId = providerId,
                isHtml = isHtml
            )
        )
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedProviderName =
        providers.find { it.id == providerId }?.name ?: "Select Provider"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit AI Prompt") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteAIPrompt(prompt.id)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; update() },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedProviderName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LLM Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                providerId = provider.id
                                update()
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = promptTemplate,
                onValueChange = { promptTemplate = it; update() },
                label = { Text("Prompt Template (use %s for query)") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                maxLines = 10
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Response Format:", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isHtml,
                        onClick = { isHtml = true; update() }
                    )
                    Text("HTML")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isHtml,
                        onClick = { isHtml = false; update() }
                    )
                    Text("Plain Text")
                }
            }
        }
    }
}