package com.waltermelon.vibedict.ui.wordresults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log

class DefViewModel(
    private val word: String,
    private val repository: UserPreferencesRepository

) : ViewModel() {

    private val _uiState = MutableStateFlow<DefUiState>(DefUiState.Loading)
    val uiState: StateFlow<DefUiState> = _uiState.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    // --- ADDED ---
    private val _navigateToWord = MutableStateFlow<String?>(null)
    val navigateToWord: StateFlow<String?> = _navigateToWord.asStateFlow()

    fun onNavigationHandled() {
        _navigateToWord.value = null
    }
    // -----------

    init {
        searchWord(word)

        viewModelScope.launch {
            repository.bookmarks.collect { bookmarks ->
                _isBookmarked.value = word in bookmarks
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            repository.toggleBookmark(word)
        }
    }

    private fun searchWord(query: String) {
        viewModelScope.launch {
            Log.e("MdictJNI", "!!! DefViewModel: searchWord coroutine STARTED for query '$query'.")
            _uiState.value = DefUiState.Loading

            // --- UPDATED: Get active collection and filter results ---
            // 1. Get Current Active Collection
            val activeId = repository.activeCollectionId.first()
            val collections = repository.collections.first()
            val currentCollection = collections.find { it.id == activeId }
            val filterIds = currentCollection?.dictionaryIds // This list is NOW ORDERED

            // 2. Get all results
            var rawResults = DictionaryManager.lookupAll(query) // Use 'var'
            var finalQuery = query

            // --- NEW: FALLBACK LOGIC ---
            if (rawResults.isEmpty()) {
                Log.e("MdictJNI", "!!! DefViewModel: Query '$query' empty. Attempting fallback...")
                val baseWord = query.split(" ").firstOrNull()

                // Check if baseWord is valid and different from the original query
                if (baseWord != null && baseWord != query) {
                    val baseResults = DictionaryManager.lookupAll(baseWord)

                    if (baseResults.isNotEmpty()) {
                        Log.e("MdictJNI", "!!! DefViewModel: Fallback SUCCESS. Found '$baseWord'. Triggering nav.")
                        // Success! We found the base word.
                        _navigateToWord.value = baseWord

                        // --- FIX: Set state to Empty to stop the Loading spinner ---
                        _uiState.value = DefUiState.Empty

                        // We stop this coroutine because a new ViewModel
                        // will be created for the new word.
                        return@launch
                    }
                }

                // If fallback failed (or wasn't possible), proceed to "Empty" state
                Log.w("DefViewModel", "Fallback failed for query: '$query'. Setting state to Empty.")
                _uiState.value = DefUiState.Empty
                return@launch
            }
            // --------------------------------

            // --- NEW: Create a Map for fast lookups ---
            val allResultsMap = rawResults.associateBy { it.first } // Map<ID, Triple>

            // 3. Determine the FINAL ordered list of IDs
            val orderedIds = if (filterIds == null || filterIds.isEmpty()) {
                // "All" collection: Use the default order from rawResults
                rawResults.map { it.first }
            } else {
                // Custom collection: Use the ordered list from filterIds
                filterIds
            }
            // ---------------------------------------------------------

            // 4. Create entries by iterating over the ORDERED list
            val entries = orderedIds.mapNotNull { id ->
                val result = allResultsMap[id]
                // Only create an entry if a result exists for this ID
                if (result != null) {
                    val (dictId, defaultName, initialContent) = result

                    // --- Handle @@@LINK= Redirection ---
                    var finalContent = initialContent
                    var redirectCount = 0
                    while (finalContent.trim().startsWith("@@@LINK=") && redirectCount < 5) {
                        val targetWord = finalContent.trim().substringAfter("@@@LINK=").trim()
                        val dict = DictionaryManager.getDictionaryById(id)
                        val newContentList = dict?.mdxEngine?.lookup(targetWord)

                        if (!newContentList.isNullOrEmpty()) {
                            // Take the first result and combine if there are multiple
                            finalContent = newContentList.joinToString("<hr>")
                        } else {
                            break
                        }
                        redirectCount++
                    }
                    // ----------------------------------------

                    // 2. Fetch overrides from Repository
                    val customName = repository.getDictionaryName(id).first()
                    val customCss = repository.getDictionaryCss(id).first()
                    val customJs = repository.getDictionaryJs(id).first()
                    val customFontPaths = repository.getDictionaryFontPaths(id).first()

                    // --- UPDATED: Get expand/style rules ---
                    val isExpanded = id in (currentCollection?.autoExpandIds ?: emptyList())
                    val forceOriginal = repository.getDictionaryForceStyle(id).first()
                    // ----------------------------------------

                    // 3. Determine Final Name
                    val finalName = if (customName.isNotBlank()) customName else defaultName

                    // 4. Determine Final CSS
                    val dictObj = DictionaryManager.getDictionaryById(id)

                    val fileCss = dictObj?.defaultCssContent ?: ""
                    val finalCss = if (customCss.isNotBlank()) customCss else fileCss

                    val fileJs = dictObj?.defaultJsContent ?: ""
                    val finalJs = if (customJs.isNotBlank()) customJs else fileJs

                    DictionaryEntry(
                        id = dictId, // --- PASS ID ---
                        dictionaryName = finalName,
                        iconRes = null,
                        definitionContent = finalContent, // Use the resolved content
                        customCss = finalCss,
                        customJs = finalJs,
                        isExpandedByDefault = isExpanded, // Use collection's rule
                        forceOriginalStyle = forceOriginal,
                        customFontPaths = customFontPaths
                    )
                } else {
                    null // This ID was in the collection but had no result for the word
                }
            }

            if (entries.isEmpty()) {
                _uiState.value = DefUiState.Empty
            } else {
                // --- NEW: Add to history *after* a successful search ---
                repository.addToHistory(finalQuery)
                // ----------------------------------------------------
                _uiState.value = DefUiState.Success(entries)
            }
        }
    }
}

sealed class DefUiState {
    object Loading : DefUiState()
    object Empty : DefUiState()
    data class Success(val results: List<DictionaryEntry>) : DefUiState()
}

class DefViewModelFactory(
    private val word: String,
    private val repository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DefViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DefViewModel(word, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
