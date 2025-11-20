package com.waltermelon.vibedict.ui.theme

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

object Screen {
    const val MAIN = "main"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val WORD_RESULT_ROUTE = "word_result/{word}"
    const val DICTIONARY_LIST = "dictionary_list"
    const val DICTIONARY_DETAIL = "dictionary_detail/{dictId}"
    const val BOOKMARK = "bookmark"
    const val COLLECTION_LIST = "collection_list"
    const val COLLECTION_DETAIL_ROUTE = "collection_detail/{collectionId}"
    const val COLLECTION_DETAIL_NEW_ID = "new_collection"
    const val LLM_PROVIDER_LIST = "llm_provider_list"
    const val LLM_PROVIDER_CONFIG = "llm_provider_config/{providerId}"
    const val AI_PROMPT_CONFIG = "ai_prompt_config/{promptId}"

    fun createCollectionDetailRoute(id: String) = "collection_detail/$id"
    fun createRouteForWord(word: String) = "word_result/$word"
    fun createDictionaryDetailRoute(id: String) = "dictionary_detail/$id"
}

// --- UPDATED EXTENSION FUNCTION ---
// Now accepts an optional builder block (e.g., { launchSingleTop = true })
fun NavController.safeNavigate(
    route: String,
    builder: (NavOptionsBuilder.() -> Unit)? = null
) {
    if (this.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        if (builder != null) {
            this.navigate(route, builder)
        } else {
            this.navigate(route)
        }
    }
}