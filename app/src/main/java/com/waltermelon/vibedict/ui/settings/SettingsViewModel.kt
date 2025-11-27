package com.waltermelon.vibedict.ui.settings

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.waltermelon.vibedict.data.DictCollection
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.data.UserPreferencesRepository
import com.waltermelon.vibedict.data.FontUtils
import com.waltermelon.vibedict.ui.theme.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File
import java.io.FileOutputStream

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    // --- Caches to prevent recomposition loops ---
    private val dictionaryNameFlows = mutableMapOf<String, StateFlow<String>>()
    private val dictionaryCssFlows = mutableMapOf<String, StateFlow<String>>()
    private val dictionaryJsFlows = mutableMapOf<String, StateFlow<String>>()
    private val dictionaryForceStyleFlows = mutableMapOf<String, StateFlow<Boolean>>()
    private val dictionaryFontPathsFlows = mutableMapOf<String, StateFlow<String>>()

    // --- Existing Flows ---
    val darkMode = repository.darkMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "Follow System"
    )
    val materialColour = repository.materialColour.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )
    val language = repository.language.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "Follow System"
    )
    val textScale =
        repository.textScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)
    val debugMode =
        repository.debugMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val keepScreenOn =
        repository.keepScreenOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val defaultFolder = repository.defaultFolder.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "/new_dict/dictionaries"
    )
    val instantSearch = repository.instantSearch.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )
    val dictionaryDirs = repository.dictionaryDirectories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet()
    )

    // --- Collection Flows ---
    val collections = repository.collections.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val activeCollectionId = repository.activeCollectionId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "default_all"
    )

    // --- Web Search Engine Flow ---
    val webSearchEngines = repository.webSearchEngines.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- LLM & AI Prompt Flows ---
    val llmProviders = repository.llmProviders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val aiPrompts = repository.aiPrompts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = DictionaryManager.isLoading
    val isLoading: StateFlow<Boolean> = _isLoading

    // --- State for Collection Editor Screen ---
    private val _editingCollection = MutableStateFlow<DictCollection?>(null)
    val editingCollection = _editingCollection.asStateFlow()

    init {
        // Initial load is handled by UI or MainActivity calling reloadAllDictionaries
    }

    /**
     * Loads a collection from the repository into the editing state.
     * Call this when navigating to the detail screen.
     */
    fun loadCollectionForEditing(collectionId: String) {
        viewModelScope.launch {
            if (collectionId == Screen.COLLECTION_DETAIL_NEW_ID) {
                _editingCollection.value = null // It's a new collection
            } else {
                // Find the collection from the full list
                val col = repository.collections.first().find { it.id == collectionId }
                _editingCollection.value = col
            }
        }
    }

    /**
     * Clears the editing state. Call this when navigating away or creating new.
     */
    fun clearEdit() {
        _editingCollection.value = null
    }
    // --------------------------------------------------

    fun saveCollection(collection: DictCollection) = viewModelScope.launch {
        repository.createOrUpdateCollection(collection)
    }

    fun deleteCollection(id: String) = viewModelScope.launch {
        repository.deleteCollection(id)
    }

    fun setActiveCollection(id: String) = viewModelScope.launch {
        repository.setActiveCollection(id)
    }

    fun clearSearchHistory() = viewModelScope.launch {
        repository.clearHistory()
    }

    // --- Existing Setters ---
    fun setDarkMode(mode: String) = viewModelScope.launch { repository.setDarkMode(mode) }
    fun setMaterialColour(enabled: Boolean) =
        viewModelScope.launch { repository.setMaterialColour(enabled) }

    fun setLanguage(lang: String) = viewModelScope.launch {
        repository.setLanguage(lang)
        
        // Apply the locale change immediately
        val localeList = when (lang) {
            "English" -> androidx.core.os.LocaleListCompat.create(java.util.Locale.ENGLISH)
            "简体中文" -> androidx.core.os.LocaleListCompat.create(java.util.Locale.SIMPLIFIED_CHINESE)
            "正體中文" -> androidx.core.os.LocaleListCompat.create(java.util.Locale.TAIWAN)
            else -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
    }
    fun setTextScale(scale: Float) = viewModelScope.launch { repository.setTextScale(scale) }
    fun setDebugMode(enabled: Boolean) = viewModelScope.launch { repository.setDebugMode(enabled) }
    fun setKeepScreenOn(enabled: Boolean) =
        viewModelScope.launch { repository.setKeepScreenOn(enabled) }

    fun setDefaultFolder(path: String) = viewModelScope.launch { repository.setDefaultFolder(path) }
    fun setInstantSearch(enabled: Boolean) =
        viewModelScope.launch { repository.setInstantSearch(enabled) }

    fun addDictionaryFolder(context: Context, uri: Uri) = viewModelScope.launch {
        try {
            val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        repository.addDictionaryDirectory(uri.toString())

        // FIX: Fetch ALL current data to preserve AI/Web settings during reload
        val dirs = repository.dictionaryDirectories.first().toMutableSet()
        dirs.add(uri.toString())

        val engines = repository.webSearchEngines.first()
        val prompts = repository.aiPrompts.first()
        val providers = repository.llmProviders.first()

        DictionaryManager.reloadDictionaries(context, dirs, engines, prompts, providers)
    }

    fun removeDictionaryFolder(context: Context, uriString: String) = viewModelScope.launch {
        repository.removeDictionaryDirectory(uriString)

        // FIX: Fetch ALL current data to preserve AI/Web settings during reload
        val dirs = repository.dictionaryDirectories.first().toMutableSet()
        dirs.remove(uriString)

        val engines = repository.webSearchEngines.first()
        val prompts = repository.aiPrompts.first()
        val providers = repository.llmProviders.first()

        DictionaryManager.reloadDictionaries(context, dirs, engines, prompts, providers)
    }

    fun reloadAllDictionaries(context: Context) = viewModelScope.launch {
        val dirs = repository.dictionaryDirectories.first()
        val engines = repository.webSearchEngines.first()
        val prompts = repository.aiPrompts.first()
        val providers = repository.llmProviders.first()
        // Pass providers to DictionaryManager so it can use the API keys
        DictionaryManager.reloadDictionaries(context, dirs, engines, prompts, providers)
    }

    // --- Dictionary Config ---

    fun getDictionaryCss(dictId: String): StateFlow<String> {
        return dictionaryCssFlows.getOrPut(dictId) {
            repository.getDictionaryCss(dictId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        }
    }

    fun getDictionaryJs(dictId: String): StateFlow<String> {
        return dictionaryJsFlows.getOrPut(dictId) {
            repository.getDictionaryJs(dictId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        }
    }

    fun getDictionaryName(dictId: String): StateFlow<String> {
        return dictionaryNameFlows.getOrPut(dictId) {
            repository.getDictionaryName(dictId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        }
    }

    fun getDictionaryForceStyle(dictId: String): StateFlow<Boolean> {
        return dictionaryForceStyleFlows.getOrPut(dictId) {
            repository.getDictionaryForceStyle(dictId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        }
    }

    fun getDictionaryFontPaths(dictId: String): StateFlow<String> {
        return dictionaryFontPathsFlows.getOrPut(dictId) {
            repository.getDictionaryFontPaths(dictId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        }
    }


    fun setDictionaryName(dictId: String, name: String) = viewModelScope.launch {
        repository.setDictionaryName(dictId, name)
    }

    fun setDictionaryCss(dictId: String, css: String) = viewModelScope.launch {
        repository.setDictionaryCss(dictId, css)
    }

    fun setDictionaryJs(dictId: String, js: String) = viewModelScope.launch {
        repository.setDictionaryJs(dictId, js)
    }

    fun deleteDictionaryCss(dictId: String) = viewModelScope.launch {
        repository.setDictionaryCss(dictId, "")
    }

    fun setDictionaryForceStyle(dictId: String, forceOriginal: Boolean) = viewModelScope.launch {
        repository.setDictionaryForceStyle(dictId, forceOriginal)
    }

    // --- Font Management ---

    fun addDictionaryFont(context: Context, dictId: String, uri: Uri) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fontsDir = File(context.filesDir, "fonts")
                    if (!fontsDir.exists()) fontsDir.mkdirs()

                    // 1. Get extension
                    val mimeType = context.contentResolver.getType(uri)
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "ttf"

                    // 2. Create a temporary file first
                    val tempFile = File.createTempFile("temp_font", ".$extension", context.cacheDir)
                    val outputStream = FileOutputStream(tempFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    // 3. Try to parse the real name
                    val parsedName = FontUtils.getFontName(tempFile)

                    // 4. Determine final filename (Sanitize name)
                    val baseName = if (!parsedName.isNullOrBlank()) {
                        parsedName.replace("[^a-zA-Z0-9_\\-\\s]".toRegex(), "") // Allow spaces
                    } else {
                        "font_${UUID.randomUUID().toString().take(8)}"
                    }

                    // Ensure uniqueness
                    var finalName = "$baseName.$extension"
                    var finalFile = File(fontsDir, finalName)
                    var counter = 1
                    while (finalFile.exists()) {
                        finalName = "${baseName}_$counter.$extension"
                        finalFile = File(fontsDir, finalName)
                        counter++
                    }

                    // 5. Move temp file to final destination
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()

                    // 6. Add to repository
                    repository.addDictionaryFontPaths(dictId, "fonts/$finalName")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    // NEW FUNCTION: Rename Font
    fun renameDictionaryFont(context: Context, dictId: String, oldPath: String, newName: String) =
        viewModelScope.launch(Dispatchers.IO) {
            val fontsDir = File(context.filesDir, "fonts")
            val oldFile = File(context.filesDir, oldPath)

            if (oldFile.exists()) {
                val extension = oldFile.extension
                // Sanitize new name (Allow spaces)
                val sanitized = newName.replace("[^a-zA-Z0-9_\\-\\s]".toRegex(), "")
                val newFileName = "$sanitized.$extension"
                val newFile = File(fontsDir, newFileName)

                // If names are identical (ignoring case/path), do nothing
                if (oldFile.absolutePath == newFile.absolutePath) return@launch

                if (!newFile.exists()) {
                    var success = oldFile.renameTo(newFile)
                    
                    // Fallback: Copy and Delete if rename fails (e.g. cross-filesystem or locked)
                    if (!success) {
                        try {
                            oldFile.copyTo(newFile, overwrite = true)
                            if (newFile.exists() && newFile.length() > 0) {
                                oldFile.delete()
                                success = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (success) {
                        // 1. Remove old path from Repo
                        repository.removeDictionaryFontPaths(dictId, oldPath)
                        // 2. Add new path to Repo
                        repository.addDictionaryFontPaths(dictId, "fonts/$newFileName")
                    }
                }
            }
        }

    fun removeDictionaryFont(context: Context, dictId: String, pathToRemove: String) =
        viewModelScope.launch(Dispatchers.IO) {
            if (pathToRemove.isNotEmpty()) {
                val file = File(context.filesDir, pathToRemove)
                if (file.exists()) file.delete()
                repository.removeDictionaryFontPaths(dictId, pathToRemove)
            }
        }

    // --- Web Search Engine Methods ---
    fun addWebSearchEngine(onCreated: (String) -> Unit) = viewModelScope.launch {
        val newId = "web_" + UUID.randomUUID().toString()
        val newEngine = com.waltermelon.vibedict.data.WebSearchEngine(
            id = newId,
            name = "New Search Engine",
            url = ""
        )
        repository.addWebSearchEngine(newEngine)
        val engines = repository.webSearchEngines.first()
        DictionaryManager.updateWebDictionaries(engines)
        onCreated(newId)
    }

    fun deleteWebSearchEngine(id: String) = viewModelScope.launch {
        repository.deleteWebSearchEngine(id)
        val engines = repository.webSearchEngines.first()
        DictionaryManager.updateWebDictionaries(engines)
    }

    fun updateWebSearchEngineUrl(id: String, newUrl: String) = viewModelScope.launch {
        val currentList = repository.webSearchEngines.first()
        val engine = currentList.find { it.id == id }
        if (engine != null) {
            repository.updateWebSearchEngine(engine.copy(url = newUrl))
            val engines = repository.webSearchEngines.first()
            DictionaryManager.updateWebDictionaries(engines)
        }
    }

    // --- LLM Provider Methods ---

    fun addLLMProvider(onCreated: (String) -> Unit) = viewModelScope.launch {
        val newId = "llm_" + UUID.randomUUID().toString()
        val newProvider = com.waltermelon.vibedict.data.LLMProvider(
            id = newId,
            name = "New Provider",
            type = "Google", // Default
            apiKey = "",
            model = "gemini-pro"
        )
        repository.addLLMProvider(newProvider)
        onCreated(newId)
    }

    fun updateLLMProvider(provider: com.waltermelon.vibedict.data.LLMProvider) = viewModelScope.launch {
        repository.updateLLMProvider(provider)
    }

    fun deleteLLMProvider(id: String) = viewModelScope.launch {
        repository.deleteLLMProvider(id)
    }

    // --- AI Prompt Methods ---

    fun addAIPrompt(onCreated: (String) -> Unit) = viewModelScope.launch {
        val newId = "ai_" + UUID.randomUUID().toString()
        // Find a default provider if possible
        val providers = repository.llmProviders.first()
        val defaultProviderId = providers.firstOrNull()?.id ?: ""

        val newPrompt = com.waltermelon.vibedict.data.AIPrompt(
            id = newId,
            name = "New AI Prompt",
            providerId = defaultProviderId,
            promptTemplate = "Explain %s",
            isHtml = true
        )
        repository.addAIPrompt(newPrompt)
        val prompts = repository.aiPrompts.first()
        DictionaryManager.updateAIPromptDictionaries(prompts)
        onCreated(newId)
    }

    fun updateAIPrompt(prompt: com.waltermelon.vibedict.data.AIPrompt) = viewModelScope.launch {
        repository.updateAIPrompt(prompt)
        val prompts = repository.aiPrompts.first()
        DictionaryManager.updateAIPromptDictionaries(prompts)
    }

    fun deleteAIPrompt(id: String) = viewModelScope.launch {
        repository.deleteAIPrompt(id)
        val prompts = repository.aiPrompts.first()
        DictionaryManager.updateAIPromptDictionaries(prompts)
    }

    fun migrateCollectionsToHashes() = viewModelScope.launch {
        val currentCollections = repository.collections.first().toMutableList()
        var hasChanges = false
        val loadedDicts = DictionaryManager.loadedDictionaries.toList()

        val updatedCollections = currentCollections.map { collection ->
            val newDictIds = collection.dictionaryIds.mapNotNull { oldId ->
                // 1. Check if it's already a valid Hash ID (exists in loaded dicts)
                if (loadedDicts.any { it.id == oldId }) {
                    return@mapNotNull oldId
                }

                // 2. It's likely a legacy Path ID. Try to find by Path.
                val matchByPath = loadedDicts.find { it.mdxPath == oldId }
                if (matchByPath != null) {
                    hasChanges = true
                    return@mapNotNull matchByPath.id
                }

                // 3. Try to find by Filename (Best effort for moved files)
                // Extract filename from the old ID (which is a URI string)
                val oldUri = Uri.parse(oldId)
                val oldFilename = oldUri.lastPathSegment
                
                if (oldFilename != null) {
                    // Find a loaded dict that has the same filename in its path
                    val matchByName = loadedDicts.find { 
                        val loadedUri = Uri.parse(it.mdxPath ?: "")
                        loadedUri.lastPathSegment == oldFilename 
                    }
                    if (matchByName != null) {
                        hasChanges = true
                        return@mapNotNull matchByName.id
                    }
                }

                // 4. If still not found, keep the old ID (it might be a missing file)
                // We don't want to delete it from the collection yet.
                oldId
            }

            if (newDictIds != collection.dictionaryIds) {
                hasChanges = true
                collection.copy(dictionaryIds = newDictIds)
            } else {
                collection
            }
        }

        if (hasChanges) {
            updatedCollections.forEach { repository.createOrUpdateCollection(it) }
        }
    }

    class SettingsViewModelFactory(private val repository: UserPreferencesRepository) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}