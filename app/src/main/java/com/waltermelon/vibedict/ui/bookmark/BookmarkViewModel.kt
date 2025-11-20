package com.waltermelon.vibedict.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.waltermelon.vibedict.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarkViewModel(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    // Observe the bookmarks flow, sort it alphabetically, and expose as StateFlow
    val bookmarkedWords: StateFlow<List<String>> = repository.bookmarks
        .map { it.sorted() } // Sort the set into a list
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Removes a word from the bookmarks.
     */
    fun removeBookmark(word: String) {
        viewModelScope.launch {
            // --- FIX: Call toggleBookmark instead of removeBookmark ---
            repository.toggleBookmark(word)
        }
    }
}

// --- Factory ---
class BookmarkViewModelFactory(
    private val repository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookmarkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookmarkViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}