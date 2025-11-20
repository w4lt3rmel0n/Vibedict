package com.waltermelon.vibedict.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * Data model for a user-defined collection of dictionaries.
 * @param id A unique ID for the collection.
 * @param name The user-visible name.
 * @param dictionaryIds A list of dictionary IDs included in this group. If empty, it implies ALL dictionaries.
 * @param autoExpandIds A list of dictionary IDs *within this collection* that should be expanded by default.
 */
data class DictCollection(
    val id: String,
    val name: String,
    val dictionaryIds: List<String>,
    val autoExpandIds: List<String> // CHANGED from expandAll: Boolean
)

// --- NEW: Data model for Web Search Engine ---
data class WebSearchEngine(
    val id: String,
    val name: String,
    val url: String
)

// --- NEW: Data model for LLM Provider ---
data class LLMProvider(
    val id: String,
    val name: String,
    val type: String, // e.g., "Google"
    val apiKey: String,
    val model: String // e.g., "gemini-pro"
)

// --- NEW: Data model for AI Prompt ---
data class AIPrompt(
    val id: String,
    val name: String,
    val providerId: String,
    val promptTemplate: String, // Use %s for query
    val isHtml: Boolean // true for HTML, false for Plain Text
)

class UserPreferencesRepository(private val context: Context) {

    // ... (rest of Keys and existing readers are unchanged) ...
    private val dataStore = context.dataStore
    private val HISTORY_DELIMITER = "|||"

    private object Keys {
        // --- Existing Keys ---
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val MATERIAL_COLOUR = booleanPreferencesKey("material_colour")
        val LANGUAGE = stringPreferencesKey("language")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_FOLDER = stringPreferencesKey("default_folder_path")
        val INSTANT_SEARCH = booleanPreferencesKey("instant_search")
        val DICTIONARY_DIRS = stringSetPreferencesKey("dictionary_directories")
        val BOOKMARKS = stringSetPreferencesKey("bookmarks")
        val HISTORY = stringPreferencesKey("search_history")
        val WEB_SEARCH_ENGINES = stringPreferencesKey("web_search_engines_json")

        // --- NEW: Collection Keys ---
        val COLLECTIONS = stringPreferencesKey("collections_json")
        val ACTIVE_COLLECTION_ID = stringPreferencesKey("active_collection_id")

        // --- NEW: LLM Keys ---
        val LLM_PROVIDERS = stringPreferencesKey("llm_providers_json")
        val AI_PROMPTS = stringPreferencesKey("ai_prompts_json")

    }

    // --- Existing Readers ---
    val darkMode: Flow<String> = dataStore.data.map { it[Keys.DARK_MODE] ?: "Follow System" }
    val materialColour: Flow<Boolean> = dataStore.data.map { it[Keys.MATERIAL_COLOUR] ?: true }
    val language: Flow<String> = dataStore.data.map { it[Keys.LANGUAGE] ?: "Follow System" }
    val textScale: Flow<Float> = dataStore.data.map { it[Keys.TEXT_SCALE] ?: 0.5f }
    val debugMode: Flow<Boolean> = dataStore.data.map { it[Keys.DEBUG_MODE] ?: false }
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: false }
    val defaultFolder: Flow<String> = dataStore.data.map { it[Keys.DEFAULT_FOLDER] ?: "/new_dict/dictionaries" }
    val instantSearch: Flow<Boolean> = dataStore.data.map { it[Keys.INSTANT_SEARCH] ?: false }
    val dictionaryDirectories: Flow<Set<String>> = dataStore.data.map { it[Keys.DICTIONARY_DIRS] ?: emptySet() }
    val bookmarks: Flow<Set<String>> = dataStore.data.map { it[Keys.BOOKMARKS] ?: emptySet() }

    // --- UPDATED: History Flow ---
    val history: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[Keys.HISTORY]?.split(HISTORY_DELIMITER)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    // --- NEW: Collection Flows ---
    val collections: Flow<List<DictCollection>> = dataStore.data.map { prefs ->
        val jsonStr = prefs[Keys.COLLECTIONS]
        parseCollections(jsonStr)
    }

    val activeCollectionId: Flow<String> = dataStore.data.map {
        it[Keys.ACTIVE_COLLECTION_ID] ?: "default_all"
    }

    val webSearchEngines: Flow<List<WebSearchEngine>> = dataStore.data.map { prefs ->
        parseWebSearchEngines(prefs[Keys.WEB_SEARCH_ENGINES])
    }

    // --- NEW: LLM Flows ---
    val llmProviders: Flow<List<LLMProvider>> = dataStore.data.map { prefs ->
        parseLLMProviders(prefs[Keys.LLM_PROVIDERS])
    }

    val aiPrompts: Flow<List<AIPrompt>> = dataStore.data.map { prefs ->
        parseAIPrompts(prefs[Keys.AI_PROMPTS])
    }

    // --- Existing Writers ---
    suspend fun setDarkMode(mode: String) = dataStore.edit { it[Keys.DARK_MODE] = mode }
    suspend fun setMaterialColour(isEnabled: Boolean) = dataStore.edit { it[Keys.MATERIAL_COLOUR] = isEnabled }
    suspend fun setLanguage(lang: String) = dataStore.edit { it[Keys.LANGUAGE] = lang }
    suspend fun setTextScale(scale: Float) = dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    suspend fun setDebugMode(isEnabled: Boolean) = dataStore.edit { it[Keys.DEBUG_MODE] = isEnabled }
    suspend fun setKeepScreenOn(isEnabled: Boolean) = dataStore.edit { it[Keys.KEEP_SCREEN_ON] = isEnabled }
    suspend fun setDefaultFolder(path: String) = dataStore.edit { it[Keys.DEFAULT_FOLDER] = path }
    suspend fun setInstantSearch(isEnabled: Boolean) = dataStore.edit { it[Keys.INSTANT_SEARCH] = isEnabled }

    suspend fun addDictionaryDirectory(uriString: String) = dataStore.edit { preferences ->
        val currentSet = preferences[Keys.DICTIONARY_DIRS] ?: emptySet()
        preferences[Keys.DICTIONARY_DIRS] = currentSet + uriString
    }

    suspend fun removeDictionaryDirectory(uriString: String) = dataStore.edit { preferences ->
        val currentSet = preferences[Keys.DICTIONARY_DIRS] ?: emptySet()
        preferences[Keys.DICTIONARY_DIRS] = currentSet - uriString
    }

    suspend fun toggleBookmark(word: String) = dataStore.edit { preferences ->
        val currentSet = preferences[Keys.BOOKMARKS] ?: emptySet()
        if (word in currentSet) {
            preferences[Keys.BOOKMARKS] = currentSet - word
        } else {
            preferences[Keys.BOOKMARKS] = currentSet + word
        }
    }

    // --- UPDATED: History Writers ---
    suspend fun addToHistory(word: String) = dataStore.edit { preferences ->
        val currentString = preferences[Keys.HISTORY] ?: ""
        val currentList = if (currentString.isEmpty()) {
            mutableListOf()
        } else {
            currentString.split(HISTORY_DELIMITER).toMutableList()
        }
        // Remove duplicates and re-add to top
        currentList.remove(word)
        currentList.add(0, word)
        // Limit history size
        if (currentList.size > 50) {
            currentList.subList(50, currentList.size).clear()
        }
        preferences[Keys.HISTORY] = currentList.joinToString(HISTORY_DELIMITER)
    }

    suspend fun clearHistory() = dataStore.edit { preferences ->
        preferences.remove(Keys.HISTORY)
    }

    // --- NEW: Collection Management ---

    suspend fun createOrUpdateCollection(collection: DictCollection) = dataStore.edit { prefs ->
        val currentList = parseCollections(prefs[Keys.COLLECTIONS]).toMutableList()
        val index = currentList.indexOfFirst { it.id == collection.id }

        if (index != -1) {
            // Update existing
            currentList[index] = collection
        } else {
            // Add new
            currentList.add(collection)
        }
        prefs[Keys.COLLECTIONS] = serializeCollections(currentList)

        // If this is the first real collection being added, make it active
        if (currentList.count { it.id != "default_all" } == 1) {
            prefs[Keys.ACTIVE_COLLECTION_ID] = collection.id
        }
    }

    suspend fun deleteCollection(id: String) = dataStore.edit { prefs ->
        if (id == "default_all") return@edit // Cannot delete default
        val currentList = parseCollections(prefs[Keys.COLLECTIONS]).toMutableList()
        currentList.removeAll { it.id == id }
        prefs[Keys.COLLECTIONS] = serializeCollections(currentList)

        // If active collection was deleted, reset to default
        if (prefs[Keys.ACTIVE_COLLECTION_ID] == id) {
            prefs[Keys.ACTIVE_COLLECTION_ID] = "default_all"
        }
    }

    suspend fun setActiveCollection(id: String) = dataStore.edit {
        it[Keys.ACTIVE_COLLECTION_ID] = id
    }

    // --- NEW: Web Search Engine Management ---

    suspend fun addWebSearchEngine(engine: WebSearchEngine) = dataStore.edit { prefs ->
        val currentList = parseWebSearchEngines(prefs[Keys.WEB_SEARCH_ENGINES]).toMutableList()
        currentList.add(engine)
        prefs[Keys.WEB_SEARCH_ENGINES] = serializeWebSearchEngines(currentList)
    }

    suspend fun updateWebSearchEngine(engine: WebSearchEngine) = dataStore.edit { prefs ->
        val currentList = parseWebSearchEngines(prefs[Keys.WEB_SEARCH_ENGINES]).toMutableList()
        val index = currentList.indexOfFirst { it.id == engine.id }
        if (index != -1) {
            currentList[index] = engine
            prefs[Keys.WEB_SEARCH_ENGINES] = serializeWebSearchEngines(currentList)
        }
    }

    suspend fun deleteWebSearchEngine(id: String) = dataStore.edit { prefs ->
        val currentList = parseWebSearchEngines(prefs[Keys.WEB_SEARCH_ENGINES]).toMutableList()
        currentList.removeAll { it.id == id }
        prefs[Keys.WEB_SEARCH_ENGINES] = serializeWebSearchEngines(currentList)
    }

    // --- Helpers for JSON Serialization (Using org.json) ---
    private fun parseCollections(json: String?): List<DictCollection> {
        val default = listOf(createDefaultCollection())
        if (json.isNullOrEmpty()) return default

        return try {
            val list = mutableListOf<DictCollection>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                // Skip default if it's somehow in the JSON, we'll add it manually
                if (id == "default_all") continue

                val name = obj.getString("name")
                // val expandAll = obj.optBoolean("expandAll", false) // REMOVED
                val dictIdsArray = obj.getJSONArray("dictIds")
                val dictIds = mutableListOf<String>()
                for (j in 0 until dictIdsArray.length()) {
                    dictIds.add(dictIdsArray.getString(j))
                }

                // --- NEW: Parse autoExpandIds array ---
                val expandIdsArray = obj.optJSONArray("autoExpandIds") ?: JSONArray()
                val expandIds = mutableListOf<String>()
                for (j in 0 until expandIdsArray.length()) {
                    expandIds.add(expandIdsArray.getString(j))
                }
                // ---

                list.add(DictCollection(id, name, dictIds, expandIds)) // UPDATED
            }
            // Add default collection to the start
            default + list
        } catch (e: Exception) {
            e.printStackTrace()
            default
        }
    }

    private fun serializeCollections(list: List<DictCollection>): String {
        val array = JSONArray()
        list.forEach { col ->
            // Don't save the default collection, it's implicit
            if (col.id == "default_all") return@forEach

            val obj = JSONObject()
            obj.put("id", col.id)
            obj.put("name", col.name)
            // obj.put("expandAll", col.expandAll) // REMOVED
            val dictArray = JSONArray(col.dictionaryIds)
            obj.put("dictIds", dictArray)
            // --- NEW: Serialize autoExpandIds array ---
            val expandArray = JSONArray(col.autoExpandIds)
            obj.put("autoExpandIds", expandArray)
            // ---
            array.put(obj)
        }
        return array.toString()
    }

    private fun createDefaultCollection() = DictCollection(
        id = "default_all",
        name = "All Dictionaries",
        dictionaryIds = emptyList(), // Empty list signifies "All"
        autoExpandIds = emptyList() // CHANGED from expandAll = false
    )

    private fun parseWebSearchEngines(json: String?): List<WebSearchEngine> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val list = mutableListOf<WebSearchEngine>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(WebSearchEngine(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    url = obj.getString("url")
                ))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun serializeWebSearchEngines(list: List<WebSearchEngine>): String {
        val array = JSONArray()
        list.forEach { engine ->
            val obj = JSONObject()
            obj.put("id", engine.id)
            obj.put("name", engine.name)
            obj.put("url", engine.url)
            array.put(obj)
        }
        return array.toString()
    }

    // --- Per-Dictionary Config (Expand logic is REMOVED) ---

    private fun getCssKey(dictId: String) = stringPreferencesKey("dict_css_${dictId.hashCode()}")
    private fun getJsKey(dictId: String) = stringPreferencesKey("dict_js_${dictId.hashCode()}")
    private fun getNameKey(dictId: String) = stringPreferencesKey("dict_name_${dictId.hashCode()}")
    private fun getForceStyleKey(dictId: String) = booleanPreferencesKey("dict_force_style_${dictId.hashCode()}")
    private fun getFontPathsKey(dictId: String) = stringPreferencesKey("dict_font_path_${dictId.hashCode()}")

    fun getDictionaryCss(dictId: String): Flow<String> = dataStore.data.map {
        it[getCssKey(dictId)] ?: ""
    }

    fun getDictionaryJs(dictId: String): Flow<String> = dataStore.data.map {
        it[getJsKey(dictId)] ?: ""
    }

    fun getDictionaryName(dictId: String): Flow<String> = dataStore.data.map {
        it[getNameKey(dictId)] ?: ""
    }

    fun getDictionaryForceStyle(dictId: String): Flow<Boolean> = dataStore.data.map {
        it[getForceStyleKey(dictId)] ?: false
    }

    fun getDictionaryFontPaths(dictId: String): Flow<String> = dataStore.data.map {
        it[getFontPathsKey(dictId)] ?: ""
    }

    suspend fun setDictionaryCss(dictId: String, css: String) = dataStore.edit {
        if (css.isEmpty()) {
            it.remove(getCssKey(dictId))
        } else {
            it[getCssKey(dictId)] = css
        }
    }

    suspend fun setDictionaryJs(dictId: String, js: String) = dataStore.edit {
        it[getJsKey(dictId)] = js
    }

    suspend fun setDictionaryName(dictId: String, name: String) = dataStore.edit {
        it[getNameKey(dictId)] = name
    }

    suspend fun setDictionaryForceStyle(dictId: String, forceOriginal: Boolean) = dataStore.edit {
        it[getForceStyleKey(dictId)] = forceOriginal
    }

    suspend fun setDictionaryFontPath(dictId: String, path: String) = dataStore.edit {
        if (path.isEmpty()) {
            it.remove(getFontPathsKey(dictId))
        } else {
            it[getFontPathsKey(dictId)] = path
        }
    }

    suspend fun addDictionaryFontPaths(dictId: String, newPath: String) = dataStore.edit { preferences ->
        val currentPaths = preferences[getFontPathsKey(dictId)] ?: ""
        val pathsList = if (currentPaths.isEmpty()) {
            mutableListOf()
        } else {
            currentPaths.split(",").toMutableList()
        }
        if (!pathsList.contains(newPath)) {
            pathsList.add(newPath)
        }
        preferences[getFontPathsKey(dictId)] = pathsList.joinToString(",")
    }

    suspend fun removeDictionaryFontPaths(dictId: String, pathToRemove: String) = dataStore.edit { preferences ->
        val currentPaths = preferences[getFontPathsKey(dictId)] ?: ""
        if (currentPaths.isEmpty()) return@edit
        
        val pathsList = currentPaths.split(",").toMutableList()
        pathsList.remove(pathToRemove)
        
        if (pathsList.isEmpty()) {
            preferences.remove(getFontPathsKey(dictId))
        } else {
            preferences[getFontPathsKey(dictId)] = pathsList.joinToString(",")
        }
    }
    // --- NEW: LLM Provider Management ---

    suspend fun addLLMProvider(provider: LLMProvider) = dataStore.edit { prefs ->
        val currentList = parseLLMProviders(prefs[Keys.LLM_PROVIDERS]).toMutableList()
        currentList.add(provider)
        prefs[Keys.LLM_PROVIDERS] = serializeLLMProviders(currentList)
    }

    suspend fun updateLLMProvider(provider: LLMProvider) = dataStore.edit { prefs ->
        val currentList = parseLLMProviders(prefs[Keys.LLM_PROVIDERS]).toMutableList()
        val index = currentList.indexOfFirst { it.id == provider.id }
        if (index != -1) {
            currentList[index] = provider
            prefs[Keys.LLM_PROVIDERS] = serializeLLMProviders(currentList)
        }
    }

    suspend fun deleteLLMProvider(id: String) = dataStore.edit { prefs ->
        val currentList = parseLLMProviders(prefs[Keys.LLM_PROVIDERS]).toMutableList()
        currentList.removeAll { it.id == id }
        prefs[Keys.LLM_PROVIDERS] = serializeLLMProviders(currentList)
    }

    // --- NEW: AI Prompt Management ---

    suspend fun addAIPrompt(prompt: AIPrompt) = dataStore.edit { prefs ->
        val currentList = parseAIPrompts(prefs[Keys.AI_PROMPTS]).toMutableList()
        currentList.add(prompt)
        prefs[Keys.AI_PROMPTS] = serializeAIPrompts(currentList)
    }

    suspend fun updateAIPrompt(prompt: AIPrompt) = dataStore.edit { prefs ->
        val currentList = parseAIPrompts(prefs[Keys.AI_PROMPTS]).toMutableList()
        val index = currentList.indexOfFirst { it.id == prompt.id }
        if (index != -1) {
            currentList[index] = prompt
            prefs[Keys.AI_PROMPTS] = serializeAIPrompts(currentList)
        }
    }

    suspend fun deleteAIPrompt(id: String) = dataStore.edit { prefs ->
        val currentList = parseAIPrompts(prefs[Keys.AI_PROMPTS]).toMutableList()
        currentList.removeAll { it.id == id }
        prefs[Keys.AI_PROMPTS] = serializeAIPrompts(currentList)
    }

    // --- NEW: LLM JSON Helpers ---

    private fun parseLLMProviders(json: String?): List<LLMProvider> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val list = mutableListOf<LLMProvider>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(LLMProvider(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = obj.getString("type"),
                    apiKey = obj.getString("apiKey"),
                    model = obj.getString("model")
                ))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun serializeLLMProviders(list: List<LLMProvider>): String {
        val array = JSONArray()
        list.forEach { provider ->
            val obj = JSONObject()
            obj.put("id", provider.id)
            obj.put("name", provider.name)
            obj.put("type", provider.type)
            obj.put("apiKey", provider.apiKey)
            obj.put("model", provider.model)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseAIPrompts(json: String?): List<AIPrompt> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val list = mutableListOf<AIPrompt>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(AIPrompt(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    providerId = obj.getString("providerId"),
                    promptTemplate = obj.getString("promptTemplate"),
                    isHtml = obj.optBoolean("isHtml", false)
                ))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun serializeAIPrompts(list: List<AIPrompt>): String {
        val array = JSONArray()
        list.forEach { prompt ->
            val obj = JSONObject()
            obj.put("id", prompt.id)
            obj.put("name", prompt.name)
            obj.put("providerId", prompt.providerId)
            obj.put("promptTemplate", prompt.promptTemplate)
            obj.put("isHtml", prompt.isHtml)
            array.put(obj)
        }
        return array.toString()
    }
}
