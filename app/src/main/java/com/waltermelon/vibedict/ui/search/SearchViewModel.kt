package com.waltermelon.vibedict.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.data.UserPreferencesRepository
import com.waltermelon.vibedict.data.DictCollection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Data model for merged results
data class MergedSearchResult(val word: String, val sources: List<String>)

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    // UI State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MergedSearchResult>>(emptyList())
    val searchResults: StateFlow<List<MergedSearchResult>> = _searchResults.asStateFlow()

    // Repository Flows
    val searchHistory = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isRegexEnabled = repository.isRegexEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isFullText = repository.isFullText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val collections = repository.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeCollectionId = repository.activeCollectionId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null

    init {
        // Observe changes that trigger search
        viewModelScope.launch {
            combine(
                _searchQuery,
                isRegexEnabled,
                isFullText,
                activeCollectionId,
                collections // We need collections to find the active one
            ) { query, regex, fullText, activeId, allCollections ->
                SearchRequest(query, regex, fullText, activeId, allCollections)
            }.collectLatest { request ->
                performSearch(request)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
    
    fun setRegexEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRegexEnabled(enabled)
        }
    }

    fun setFullText(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFullText(enabled)
        }
    }
    
    fun setActiveCollection(id: String) {
        viewModelScope.launch {
            repository.setActiveCollection(id)
        }
    }

    private suspend fun performSearch(request: SearchRequest) {
        val (query, isRegex, isFullText, activeId, allCollections) = request

        // Helper to check if a string contains CJK characters
        fun isCjk(s: String): Boolean {
            if (s.isEmpty()) return false
            val c = s.codePointAt(0)
            return (c in 0x2E80..0x2EFF) || // CJK Radicals Supplement
                   (c in 0x3000..0x303F) || // CJK Symbols and Punctuation
                   (c in 0x3040..0x309F) || // Hiragana
                   (c in 0x30A0..0x30FF) || // Katakana
                   (c in 0x3100..0x312F) || // Bopomofo
                   (c in 0x3200..0x32FF) || // Enclosed CJK Letters and Months
                   (c in 0x3400..0x4DBF) || // CJK Unified Ideographs Extension A
                   (c in 0x4E00..0x9FFF) || // CJK Unified Ideographs
                   (c in 0xF900..0xFAFF)    // CJK Compatibility Ideographs
        }

        val shouldSearch = query.length > 1 || (query.isNotEmpty() && isCjk(query))

        if (shouldSearch) {
            _isSearching.value = true
            // Debounce
            delay(if (isFullText) 500 else 50)

            android.util.Log.d("SearchViewModel", "Searching for: '$query', Regex: $isRegex, FullText: $isFullText")

            // 1. Get Active Collection Filter
            val collection = allCollections.find { it.id == activeId }
            val filterIds = collection?.dictionaryIds

            // 2. Perform Suggestion Lookup
            val rawSuggestions = if (isRegex) {
                DictionaryManager.getRegexSuggestionsRaw(query, filterIds)
            } else if (isFullText) {
                DictionaryManager.getFullTextSuggestionsRaw(query, filterIds)
            } else {
                DictionaryManager.getSuggestionsRaw(query, filterIds)
            }

            android.util.Log.d("SearchViewModel", "Found ${rawSuggestions.size} suggestions")

            // 3. Apply Collection Filter (Now handled in DictionaryManager)
            val filteredSuggestions = rawSuggestions

            // 4. Map to SearchResult and group by word
            val groupedByWord = filteredSuggestions.groupBy(
                keySelector = { it.first }, // group by Word
                valueTransform = { it.second } // value is List<DictID>
            )

            // 5. Get display names and map to final MergedSearchResult
            
            // Collect all unique dictionary IDs involved in the results
            val allDictIds = groupedByWord.values.flatten().distinct()
            
            // Fetch custom names for these IDs
            val customNamesMap = repository.getDisplayNames(allDictIds).first()

            val finalResults = groupedByWord.map { (word, dictIds) ->
                // Get the unique display names for this word
                val displayNames = dictIds.map { id ->
                    val customName = customNamesMap[id]
                    if (!customName.isNullOrBlank()) {
                        customName
                    } else {
                        DictionaryManager.getDictionaryById(id)?.name ?: "Unknown"
                    }
                }.distinct()

                MergedSearchResult(word, displayNames)
            }

            _searchResults.value = finalResults
            _isSearching.value = false
        } else {
            _searchResults.value = emptyList()
            _isSearching.value = false
        }
    }

    data class SearchRequest(
        val query: String,
        val isRegex: Boolean,
        val isFullText: Boolean,
        val activeId: String?,
        val collections: List<DictCollection>
    )

    class SearchViewModelFactory(private val repository: UserPreferencesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
