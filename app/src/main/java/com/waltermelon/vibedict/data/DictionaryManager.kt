package com.waltermelon.vibedict.data

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import androidx.documentfile.provider.DocumentFile

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.security.MessageDigest
import android.util.Log

object DictionaryManager {
    val loadedDictionaries = mutableListOf<LoadedDictionary>()
    var loadedProviders: List<LLMProvider> = emptyList() // Stores the API keys
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    var isInitialized = false

    data class LoadedDictionary(
        val id: String,
        val name: String,
        val mdxEngine: MdictEngine?,
        val mddEngines: List<MdictEngine>,
        val mdxPath: String?,
        val mddPaths: List<String>,
        val defaultCssContent: String = "",
        val defaultJsContent: String = "",
        val webUrl: String? = null,
        val aiPrompt: AIPrompt? = null
    )

    // --- Helper: Compute partial hash for unique identification ---
    private fun computeFileHash(fd: FileDescriptor): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(4096)
            val fileSize = Os.fstat(fd).st_size

            // 1. Read Header (First 4KB)
            Os.lseek(fd, 0L, OsConstants.SEEK_SET)
            var bytesRead = Os.read(fd, buffer, 0, buffer.size)
            if (bytesRead > 0) digest.update(buffer, 0, bytesRead)

            // 2. Read Middle (4KB from middle)
            if (fileSize > 8192) {
                Os.lseek(fd, fileSize / 2, OsConstants.SEEK_SET)
                bytesRead = Os.read(fd, buffer, 0, buffer.size)
                if (bytesRead > 0) digest.update(buffer, 0, bytesRead)
            }

            // 3. Read Footer (Last 4KB)
            if (fileSize > 4096) {
                Os.lseek(fd, maxOf(0L, fileSize - 4096), OsConstants.SEEK_SET)
                bytesRead = Os.read(fd, buffer, 0, buffer.size)
                if (bytesRead > 0) digest.update(buffer, 0, bytesRead)
            }

            // 4. Include File Size
            val sizeBytes = java.nio.ByteBuffer.allocate(8).putLong(fileSize).array()
            digest.update(sizeBytes)

            // Reset position for MdictEngine
            Os.lseek(fd, 0L, OsConstants.SEEK_SET)

            // Convert to Hex String
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            "unknown_hash_${System.currentTimeMillis()}"
        }
    }

    // --- Helper: Non-Recursive File Listing ---
    private fun listFiles(dir: DocumentFile): List<DocumentFile> {
        val allFiles = mutableListOf<DocumentFile>()
        val files = dir.listFiles()
        for (file in files) {
            // Exclude hidden files/directories (starting with .)
            if (file.name?.startsWith(".") == true) continue

            // Only add files, ignore directories (Non-Recursive)
            if (!file.isDirectory && file.name != null) {
                allFiles.add(file)
            }
        }
        return allFiles
    }

    suspend fun reloadDictionaries(
        context: Context,
        folderUris: Set<String>,
        webEngines: List<WebSearchEngine> = emptyList(),
        aiPrompts: List<AIPrompt> = emptyList(),
        llmProviders: List<LLMProvider> = emptyList()
    ) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            // Load Cache
            DictionaryCacheManager.loadCache(context)

            // Save providers for later use in lookup
            loadedProviders = llmProviders

            // Close existing local engines
            loadedDictionaries.forEach {
                it.mdxEngine?.close()
                it.mddEngines.forEach { engine -> engine.close() }
            }
            loadedDictionaries.clear()

            // 1a. Load Web Engines
            webEngines.forEach { engine ->
                loadedDictionaries.add(
                    LoadedDictionary(
                        id = engine.id,
                        name = engine.name,
                        mdxEngine = null,
                        mddEngines = emptyList(),
                        mdxPath = null,
                        mddPaths = emptyList(),
                        webUrl = engine.url
                    )
                )
            }

            // 1b. Load AI Prompts
            aiPrompts.forEach { prompt ->
                loadedDictionaries.add(
                    LoadedDictionary(
                        id = prompt.id,
                        name = prompt.name,
                        mdxEngine = null,
                        mddEngines = emptyList(),
                        mdxPath = null,
                        mddPaths = emptyList(),
                        aiPrompt = prompt
                    )
                )
            }

            // 2. Load Local Dictionaries (PARALLELIZED)
            
            // 2. Load Local Dictionaries (PARALLELIZED & STRICT PAIRING)
            
            // Step A: Process each folder independently
            val loadedLocalDicts = folderUris.map { uriString ->
                async {
                    try {
                        val uri = Uri.parse(uriString)
                        val dir = DocumentFile.fromTreeUri(context, uri)
                        if (dir != null && dir.canRead()) {
                            // 1. Get files in THIS folder only (Non-Recursive)
                            val filesInFolder = listFiles(dir)

                            // 2. Group by dictionary name
                            val baseNameRegex = "(\\.\\d+)?\\.(mdx|mdd|css)$".toRegex(RegexOption.IGNORE_CASE)
                            val fileGroups = filesInFolder.groupBy { it.name!!.replace(baseNameRegex, "") }

                            // 3. Process groups
                            fileGroups.mapNotNull { (baseName, files) ->
                                try {
                                    var mdxEngine: MdictEngine? = null
                                    var mdxPath: String? = null
                                    var dictId = "" // Will be the Hash
                                    var cssContent = ""
                                    var jsContent = ""

                                    val mddEngines = mutableListOf<MdictEngine>()
                                    val mddPaths = mutableListOf<String>()

                                    // Process MDX
                                    val mdxFile = files.find { it.name!!.endsWith(".mdx", ignoreCase = true) }
                                    if (mdxFile != null) {
                                        try {
                                            // --- CACHE CHECK ---
                                            val fileUri = mdxFile.uri.toString()
                                            val fileSize = mdxFile.length()
                                            val lastModified = mdxFile.lastModified()
                                            val cachedEntry = DictionaryCacheManager.getEntry(fileUri)

                                            if (cachedEntry != null &&
                                                cachedEntry.size == fileSize &&
                                                cachedEntry.lastModified == lastModified
                                            ) {
                                                // Cache Hit
                                                dictId = cachedEntry.hash
                                                val pfd = context.contentResolver.openFileDescriptor(mdxFile.uri, "r")
                                                if (pfd != null) {
                                                    val fdInt = pfd.detachFd()
                                                    val engine = MdictEngine()
                                                    if (engine.loadDictionaryFd(fdInt, false)) {
                                                        mdxEngine = engine
                                                        mdxPath = fileUri
                                                    } else {
                                                        engine.close()
                                                    }
                                                }
                                            } else {
                                                // Cache Miss
                                                val pfd = context.contentResolver.openFileDescriptor(mdxFile.uri, "r")
                                                if (pfd != null) {
                                                    // --- COMPUTE HASH ---
                                                    val fdObj = pfd.fileDescriptor
                                                    dictId = computeFileHash(fdObj)
                                                    // --------------------

                                                    // Update Cache
                                                    DictionaryCacheManager.putEntry(
                                                        DictionaryCacheManager.CacheEntry(
                                                            uri = fileUri,
                                                            size = fileSize,
                                                            lastModified = lastModified,
                                                            hash = dictId,
                                                            name = baseName
                                                        )
                                                    )

                                                    val fdInt = pfd.detachFd()
                                                    val engine = MdictEngine()
                                                    if (engine.loadDictionaryFd(fdInt, false)) {
                                                        mdxEngine = engine
                                                        mdxPath = fileUri
                                                    } else {
                                                        engine.close()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }

                                    // Process MDD
                                    val mddFiles = files.filter { it.name!!.endsWith(".mdd", ignoreCase = true) }
                                    mddFiles.forEach { mddFile ->
                                        try {
                                            val pfd = context.contentResolver.openFileDescriptor(mddFile.uri, "r")
                                            if (pfd != null) {
                                                val fd = pfd.detachFd()
                                                val engine = MdictEngine()
                                                if (engine.loadDictionaryFd(fd, true)) {
                                                    mddEngines.add(engine)
                                                    mddPaths.add(mddFile.uri.toString())
                                                } else {
                                                    engine.close()
                                                }
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }

                                    // Process CSS
                                    val cssFile = files.find { it.name!!.endsWith(".css", ignoreCase = true) }
                                    if (cssFile != null) {
                                        try {
                                            context.contentResolver.openInputStream(cssFile.uri)?.use { inputStream ->
                                                cssContent = inputStream.bufferedReader().readText()
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }

                                    // Process JS
                                    val jsFile = files.find { it.name!!.endsWith(".js", ignoreCase = true) }
                                    if (jsFile != null) {
                                        try {
                                            context.contentResolver.openInputStream(jsFile.uri)?.use { inputStream ->
                                                jsContent = inputStream.bufferedReader().readText()
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }

                                    if (mdxEngine != null || mddEngines.isNotEmpty()) {
                                        // Fallback ID if MDX is missing
                                        if (dictId.isEmpty()) {
                                            dictId = mddFiles.firstOrNull()?.uri.toString() ?: "unknown_${System.currentTimeMillis()}"
                                        }

                                        LoadedDictionary(
                                            id = dictId,
                                            name = baseName,
                                            mdxEngine = mdxEngine,
                                            mddEngines = mddEngines,
                                            mdxPath = mdxPath,
                                            mddPaths = mddPaths,
                                            defaultCssContent = cssContent,
                                            defaultJsContent = jsContent
                                        )
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList<LoadedDictionary>()
                    }
                }
            }.awaitAll().flatten()

            // --- NEW: Prune Cache (Simplified for now, can be improved) ---
            // Since we don't have a flat list of all files upfront anymore, we can skip aggressive pruning
            // or do it based on the loaded dictionaries.
            val validMdxUris = loadedLocalDicts.mapNotNull { it.mdxPath }.toSet()
            DictionaryCacheManager.prune(validMdxUris)
            // ------------------------

            // Step D: Add to main list (checking for duplicates)
            loadedLocalDicts.forEach { dict ->
                if (loadedDictionaries.none { it.id == dict.id }) {
                    loadedDictionaries.add(dict)
                } else {
                    // Duplicate found, close resources
                    dict.mdxEngine?.close()
                    dict.mddEngines.forEach { it.close() }
                }
            }
            
            // Save Cache
            DictionaryCacheManager.saveCache(context)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isLoading.value = false
            isInitialized = true
        }
    }

    fun updateWebDictionaries(engines: List<WebSearchEngine>) {
        loadedDictionaries.removeAll { it.webUrl != null }
        engines.forEach { engine ->
            loadedDictionaries.add(0,
                LoadedDictionary(
                    id = engine.id,
                    name = engine.name,
                    mdxEngine = null,
                    mddEngines = emptyList(),
                    mdxPath = null,
                    mddPaths = emptyList(),
                    webUrl = engine.url
                )
            )
        }
    }

    fun updateAIPromptDictionaries(prompts: List<AIPrompt>) {
        loadedDictionaries.removeAll { it.aiPrompt != null }
        prompts.forEach { prompt ->
            loadedDictionaries.add(0,
                LoadedDictionary(
                    id = prompt.id,
                    name = prompt.name,
                    mdxEngine = null,
                    mddEngines = emptyList(),
                    mdxPath = null,
                    mddPaths = emptyList(),
                    aiPrompt = prompt
                )
            )
        }
    }

    // --- NEW: Update LLM Providers Cache ---
    fun updateLLMProviders(providers: List<LLMProvider>) {
        loadedProviders = providers
    }

    fun cleanup() {
        loadedDictionaries.forEach {
            it.mdxEngine?.close()
            it.mddEngines.forEach { engine -> engine.close() }
        }
        loadedDictionaries.clear()
    }

    suspend fun lookup(dictId: String, word: String): List<String>? = withContext(Dispatchers.IO) {
        val dict = getDictionaryById(dictId) ?: return@withContext null
        try {
            if (dict.webUrl != null) {
                // Handle Web Search
                val url = dict.webUrl.replace("%s", word)
                return@withContext listOf("@@WEB_URL@@$url")

            } else if (dict.aiPrompt != null) {
                // --- Handle AI Prompt ---
                val promptText = dict.aiPrompt.promptTemplate.replace("%s", word)
                val provider = loadedProviders.find { it.id == dict.aiPrompt.providerId }

                val aiResponse = if (provider != null && provider.apiKey.isNotBlank()) {
                    try {
                        provider.generateContent(promptText)
                    } catch (e: Exception) {
                        "Error: ${e.localizedMessage}"
                    }
                } else {
                    "Configuration Error: Provider not found or API Key missing."
                }

                return@withContext listOf(
                    if (dict.aiPrompt.isHtml) {
                        "<div class='ai-wrapper'>$aiResponse</div>"
                    } else {
                        "<pre>$aiResponse</pre>"
                    }
                )

            } else {
                dict.mdxEngine?.let { engine ->
                    // Helper function to resolve redirects recursively with size safety
                    fun resolve(w: String, depth: Int, currentSize: Long): Pair<List<String>, Long> {
                        if (depth > 5) return Pair(emptyList(), currentSize)
                        if (currentSize > 5 * 1024 * 1024) return Pair(emptyList(), currentSize) // 5MB Limit

                        val defs = engine.lookup(w)
                        val finalDefs = mutableListOf<String>()
                        var size = currentSize

                        for (d in defs) {
                            if (size > 25 * 1024 * 1024) break // Stop if we've accumulated too much data (Global Limit: 25MB)

                            if (d.startsWith("@@@LINK=")) {
                                val target = d.substringAfter("@@@LINK=").trim()
                                val (resolved, newSize) = resolve(target, depth + 1, size)
                                finalDefs.addAll(resolved)
                                size = newSize
                            } else {
                                val entrySize = d.length * 2L
                                // Safety Check: If a single entry is huge (> 10MB), skip it or truncate it to prevent OOM
                                if (entrySize > 10 * 1024 * 1024) {
                                    finalDefs.add("<div class='error'>Content too large to display (${entrySize / 1024 / 1024} MB).</div>")
                                    // Add a small amount to size to reflect the error message
                                    size += 200 
                                    continue
                                }

                                // Safety Check: Will adding this exceed the global limit?
                                if (size + entrySize > 25 * 1024 * 1024) {
                                    finalDefs.add("<div class='error'>Total content limit exceeded. Some definitions are hidden.</div>")
                                    size += 200
                                    break
                                }

                                finalDefs.add(d)
                                size += entrySize 
                            }
                        }
                        return Pair(finalDefs, size)
                    }

                    val (definitions, _) = resolve(word, 0, 0L)
                    if (definitions.isNotEmpty()) {
                        // --- DEDUPLICATION & RETURN LIST ---
                        // Sort by length descending to prioritize "primary" (content-rich) entries
                        return@withContext definitions.distinct().sortedByDescending { it.length }
                    }

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun lookupAll(word: String): List<Triple<String, String, List<String>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Triple<String, String, List<String>>>()
        loadedDictionaries.toList().forEach { dict ->
            val content = lookup(dict.id, word)
            if (content != null) {
                results.add(Triple(dict.id, dict.name, content))
            }
        }
        return@withContext results
    }

    fun getResourceByKey(key: String): ByteArray? {
        Log.d("DictionaryManager", "getResourceByKey: $key")
        // Fallback for legacy calls or if ID is unknown (though we should avoid this)
        val variations = listOf(
            "\\" + key.replace('/', '\\'),
            key.replace('/', '\\'),
            "\\" + key.substringAfterLast('/'),
            key.substringAfterLast('/')
        ).distinct()

        val dictSnapshot = loadedDictionaries.toList()

        for (v in variations) {
            for (dict in dictSnapshot) {
                dict.mddEngines.forEach { engine ->
                    val hexDataList = engine.lookup(v)
                    if (hexDataList.isNotEmpty()) {
                        val hexData = hexDataList.first()
                        if (hexData.isNotBlank()) {
                            Log.d("DictionaryManager", "Found resource for key: $key (variation: $v)")
                            return HexUtils.hexStringToByteArray(hexData)
                        }
                    }
                }
            }
        }
        return null
    }

    // --- NEW: Scoped Resource Lookup ---
    fun getResource(dictId: String, key: String): ByteArray? {
        Log.d("DictionaryManager", "getResource: dictId=$dictId, key=$key")
        val dict = loadedDictionaries.find { it.id == dictId } ?: return null

        val variations = listOf(
            "\\" + key.replace('/', '\\'),
            key.replace('/', '\\'),
            "\\" + key.substringAfterLast('/'),
            key.substringAfterLast('/')
        ).distinct()

        for (v in variations) {
            dict.mddEngines.forEach { engine ->
                val hexDataList = engine.lookup(v)
                if (hexDataList.isNotEmpty()) {
                    val hexData = hexDataList.first()
                    if (hexData.isNotBlank()) {
                        Log.d("DictionaryManager", "Found resource for key: $key (variation: $v) in dict: ${dict.name}")
                        return HexUtils.hexStringToByteArray(hexData)
                    }
                }
            }
        }
        return null
    }

    suspend fun getSuggestionsRaw(prefix: String, limitToIds: List<String>? = null): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val allSuggestions = mutableListOf<Pair<String, String>>()
        val dictsToSearch = if (limitToIds.isNullOrEmpty()) loadedDictionaries.toList() else loadedDictionaries.filter { it.id in limitToIds }
        
        dictsToSearch.forEach { dict ->
            try {
                dict.mdxEngine?.let { engine ->
                    val suggestions = engine.getSuggestions(prefix)
                    suggestions.forEach { word ->
                        allSuggestions.add(Pair(word, dict.id))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext allSuggestions
    }

    suspend fun getRegexSuggestionsRaw(regex: String, limitToIds: List<String>? = null): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val allSuggestions = mutableListOf<Pair<String, String>>()
        val dictsToSearch = if (limitToIds.isNullOrEmpty()) loadedDictionaries.toList() else loadedDictionaries.filter { it.id in limitToIds }

        dictsToSearch.forEach { dict ->
            try {
                dict.mdxEngine?.let { engine ->
                    val suggestions = engine.getRegexSuggestions(regex)
                    suggestions.forEach { word ->
                        allSuggestions.add(Pair(word, dict.id))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext allSuggestions
    }

    private val _searchProgress = MutableStateFlow(0f)
    val searchProgress: StateFlow<Float> = _searchProgress.asStateFlow()

    suspend fun getFullTextSuggestionsRaw(query: String, limitToIds: List<String>? = null): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        _searchProgress.value = 0f
        val allSuggestions = mutableListOf<Pair<String, String>>()
        val dicts = if (limitToIds.isNullOrEmpty()) loadedDictionaries.toList() else loadedDictionaries.filter { it.id in limitToIds }
        if (dicts.isEmpty()) return@withContext emptyList()

        val progressMap = java.util.concurrent.ConcurrentHashMap<String, Float>()
        
        // Parallelize search
        val deferredResults = dicts.map { dict ->
            async {
                try {
                    dict.mdxEngine?.let { engine ->
                        val listener = object : com.waltermelon.vibedict.data.MdictEngine.ProgressListener {
                            override fun onProgress(progress: Float) {
                                progressMap[dict.id] = progress
                                // Calculate average progress
                                val totalProgress = progressMap.values.sum() / dicts.size
                                _searchProgress.value = totalProgress
                            }
                        }
                        val suggestions = engine.getFullTextSuggestions(query, listener)
                        // Ensure 100% progress for this dict upon completion
                        progressMap[dict.id] = 1.0f
                        _searchProgress.value = progressMap.values.sum() / dicts.size
                        
                        suggestions.map { word -> Pair(word, dict.id) }
                    } ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Pair<String, String>>()
                }
            }
        }

        deferredResults.awaitAll().flatten()
    }

    fun getDictionaryById(id: String): LoadedDictionary? {
        return loadedDictionaries.find { it.id == id }
    }

    suspend fun extractDictionary(
        context: Context,
        dictId: String,
        targetDir: Uri,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dict = getDictionaryById(dictId) ?: return@withContext
        val engines = dict.mddEngines
        if (engines.isEmpty()) return@withContext

        val rootDir = DocumentFile.fromTreeUri(context, targetDir) ?: return@withContext

        // Create base folder
        // Since createDirectory might fail if it exists (DocumentFile behavior varies), check findFile first
        val baseFolder = rootDir.findFile(dict.name) ?: rootDir.createDirectory(dict.name) ?: run {
             // Fallback: if name is invalid or something, try sanitized
             val sanitized = dict.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
             rootDir.findFile(sanitized) ?: rootDir.createDirectory(sanitized)
        } ?: return@withContext

        val allKeys = mutableSetOf<String>()
        engines.forEach { allKeys.addAll(it.getAllKeys()) }

        val total = allKeys.size
        if (total == 0) {
            onProgress(1.0f, "No files found")
            return@withContext
        }

        var current = 0
        val dirCache = mutableMapOf<String, DocumentFile>()
        dirCache[""] = baseFolder

        for (key in allKeys) {
            // key e.g. \foo\bar.png
            // Normalize path
            val path = key.replace("\\", "/").trimStart('/')
            val parts = path.split("/")
            if (parts.isEmpty()) continue

            // Determine parent directory
            // We need to resolve the directory structure
            var currentPath = ""
            var currentDir = baseFolder

            // Traverse directories
            for (i in 0 until parts.size - 1) {
                val dirName = parts[i]
                val nextPath = if (currentPath.isEmpty()) dirName else "$currentPath/$dirName"
                
                val cached = dirCache[nextPath]
                if (cached != null) {
                    currentDir = cached
                } else {
                    val nextDir = currentDir.findFile(dirName) ?: currentDir.createDirectory(dirName)
                    if (nextDir != null) {
                        dirCache[nextPath] = nextDir
                        currentDir = nextDir
                    } else {
                        // Failed to create dir, skip file
                        break
                    }
                }
                currentPath = nextPath
            }

            // Create and Write file
            val fileName = parts.last()
            // Check if file exists to avoid overwrite? Use create file which usually creates distinct name or fails?
            // SAF createDirectory throws? No, returns null.
            // createFile(mime, name).
            
            // To overwrite we need to find and delete or open output stream.
            // Let's assume we want to overwrite if exists or just create new.
            // findFile is costly.
            
            // Optimization: Just try to create. 
            // NOTE: createFile in SAF might create "name (1)" if exists.
            // If we want exact extraction we should probably check existence if it's critical.
            // Given performance, let's just create. If re-extracting, duplicates might occur.
            // For now, let's check existence because "Extract" implies overwriting usually or at least not spamming (1).
            
            val existing = currentDir.findFile(fileName)
            val file = existing ?: currentDir.createFile("*/*", fileName)
            
            if (file != null) {
                val content = getResource(dictId, key)
                if (content != null) {
                    try {
                        context.contentResolver.openOutputStream(file.uri, "w")?.use { // "w" = write and truncate
                            it.write(content)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            current++
            if (current % 10 == 0) {
                onProgress(current.toFloat() / total, path)
            }
        }
        onProgress(1.0f, "Done")
    }
}