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

    private val _resultsMutex = kotlinx.coroutines.sync.Mutex()

    private fun searchWord(query: String) {
        viewModelScope.launch {
            Log.e("MdictJNI", "!!! DefViewModel: searchWord coroutine STARTED for query '$query'.")
            
            // 1. Get Active Dictionaries & Filter IDs
            val activeId = repository.activeCollectionId.first()
            val collections = repository.collections.first()
            val currentCollection = collections.find { it.id == activeId }
            val filterIds = currentCollection?.dictionaryIds
            
            // Get all available dictionaries (loaded in memory)
            val allLoadedDicts = DictionaryManager.loadedDictionaries
            
            // Determine which dictionaries to show
            val targetDicts = if (filterIds.isNullOrEmpty()) {
                allLoadedDicts
            } else {
                // Filter and order based on collection
                filterIds.mapNotNull { id -> allLoadedDicts.find { it.id == id } }
            }

            if (targetDicts.isEmpty()) {
                 _uiState.value = DefUiState.Empty
                 return@launch
            }

            // 2. Initialize UI State with Loading Placeholders
            val initialEntries = targetDicts.map { dict ->
                 DictionaryEntry(
                     id = dict.id,
                     dictionaryName = dict.name, 
                     definitionContent = "",
                     customCss = "",
                     customJs = "",
                     isExpandedByDefault = true, 
                     forceOriginalStyle = false,
                     isLoading = true // MARK AS LOADING
                 )
            }
            
            _uiState.value = DefUiState.Success(initialEntries)
            
            // 3. Launch Individual Lookups
            val total = initialEntries.size
            var completedCount = 0
            var foundAny = false
            
            initialEntries.forEach { entry ->
                launch {
                    // Fetch Preferences
                    val customName = repository.getDictionaryName(entry.id).first()
                    val customCss = repository.getDictionaryCss(entry.id).first()
                    val customJs = repository.getDictionaryJs(entry.id).first()
                    val customFontPaths = repository.getDictionaryFontPaths(entry.id).first()
                    val isExpanded = entry.id in (currentCollection?.autoExpandIds ?: emptyList())
                    val forceOriginal = repository.getDictionaryForceStyle(entry.id).first()
                    
                    val dictObj = DictionaryManager.getDictionaryById(entry.id)
                    val fileCss = dictObj?.defaultCssContent ?: ""
                    val finalCss = if (customCss.isNotBlank()) customCss else fileCss
                    val fileJs = dictObj?.defaultJsContent ?: ""
                    val finalJs = if (customJs.isNotBlank()) customJs else fileJs
                    val finalName = if (customName.isNotBlank()) customName else entry.dictionaryName

                    // Perform Lookup
                    val content = DictionaryManager.lookup(entry.id, query)
                    
                    // Update State
                    _resultsMutex.lock()
                    try {
                        val currentState = _uiState.value
                        if (currentState is DefUiState.Success) {
                            val currentList = currentState.results.toMutableList()
                            val index = currentList.indexOfFirst { it.id == entry.id }
                            
                            if (index != -1) {
                                if (content != null) {
                                    // --- Handle @@@LINK= Redirection ---
                                    var finalContent: String = content
                                    var redirectCount = 0
                                    while (finalContent.trim().startsWith("@@@LINK=") && redirectCount < 5) {
                                        val targetWord = finalContent.trim().substringAfter("@@@LINK=").trim()
                                        val dict = DictionaryManager.getDictionaryById(entry.id)
                                        val newContentList = dict?.mdxEngine?.lookup(targetWord)

                                        if (!newContentList.isNullOrEmpty()) {
                                            finalContent = newContentList.joinToString("<hr>")
                                        } else {
                                            break
                                        }
                                        redirectCount++
                                    }
                                    // ----------------------------------------

                                    currentList[index] = currentList[index].copy(
                                        dictionaryName = finalName,
                                        definitionContent = finalContent,
                                        customCss = finalCss,
                                        customJs = finalJs,
                                        isExpandedByDefault = isExpanded,
                                        forceOriginalStyle = forceOriginal,
                                        customFontPaths = customFontPaths,
                                        isLoading = false
                                    )
                                    foundAny = true
                                } else {
                                    // Remove if no result
                                    currentList.removeAt(index)
                                }
                                
                                if (currentList.isEmpty() && completedCount == total - 1) {
                                     // Will be handled in the completion check below
                                     _uiState.value = DefUiState.Success(emptyList()) // Temporary empty list
                                } else {
                                     _uiState.value = DefUiState.Success(currentList)
                                }
                            }
                        }
                        
                        completedCount++
                        if (completedCount == total) {
                            if (!foundAny) {
                                // --- FALLBACK LOGIC ---
                                Log.e("MdictJNI", "!!! DefViewModel: Query '$query' empty. Attempting fallback...")
                                val baseWord = query.split(" ").firstOrNull()

                                if (baseWord != null && baseWord != query) {
                                    // We need to check if baseWord has results. 
                                    // Since we are inside a coroutine, we can't easily call searchWord again without resetting everything.
                                    // But we can just check quickly.
                                    val baseResults = DictionaryManager.lookupAll(baseWord) // This is blocking/suspend but fine here
                                    
                                    if (baseResults.isNotEmpty()) {
                                        Log.e("MdictJNI", "!!! DefViewModel: Fallback SUCCESS. Found '$baseWord'. Triggering nav.")
                                        _navigateToWord.value = baseWord
                                        _uiState.value = DefUiState.Empty
                                    } else {
                                        _uiState.value = DefUiState.Empty
                                    }
                                } else {
                                    _uiState.value = DefUiState.Empty
                                }
                            } else {
                                repository.addToHistory(query)
                            }
                        }
                    } finally {
                        _resultsMutex.unlock()
                    }
                }
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
