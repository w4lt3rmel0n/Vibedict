package com.waltermelon.vibedict.data

import java.io.Closeable

class MdictEngine : Closeable {

    companion object {
        init {
            // This loads the C++ library. The name must match 'add_library' in CMakeLists.txt
            System.loadLibrary("waltermelon-native")
        }
    }

    // Holds the pointer to the C++ Mdict object
    private var dictionaryHandle: Long = 0

    /**
     * Loads a dictionary file (.mdx or .mdd).
     * @param path Absolute file path to the dictionary.
     * @return True if loaded successfully, False otherwise.
     */
    fun loadDictionary(path: String): Boolean {
        // If a dictionary is already loaded, close it first to prevent memory leaks
        if (dictionaryHandle != 0L) {
            close()
        }
        dictionaryHandle = initDictionaryNative(path)
        return dictionaryHandle != 0L
    }

    /**
     * Loads a dictionary using a File Descriptor (Zero Copy).
     * @param fd The file descriptor.
     * @param isMdd True if this is an MDD file, False for MDX.
     */
    fun loadDictionaryFd(fd: Int, isMdd: Boolean): Boolean {
        if (dictionaryHandle != 0L) {
            close()
        }
        // Pass the isMdd flag to native layer so the C++ side can
        // correctly handle MDD (UTF-16 resource DB) files.
        dictionaryHandle = initDictionaryFdNative(fd, isMdd)
        return dictionaryHandle != 0L
    }

    /**
     * Looks up a word definition.
     * @param word The word to search for.
     * @return A list of HTML content definitions, or empty list if not found.
     */
    fun lookup(word: String): List<String> {
        if (dictionaryHandle == 0L) return emptyList()
        return lookupNative(dictionaryHandle, word)?.toList() ?: emptyList()
    }

    /**
     * Cleans up C++ memory. Call this when the dictionary is no longer needed.
     */
    override fun close() {
        if (dictionaryHandle != 0L) {
            destroyNative(dictionaryHandle)
            dictionaryHandle = 0
        }
    }

    fun getSuggestions(prefix: String): List<String> {
        if (dictionaryHandle == 0L) return emptyList()
        // Call the new native function
        return getSuggestionsNative(dictionaryHandle, prefix)?.toList() ?: emptyList()
    }

    // --- Native JNI Declarations ---
    private external fun initDictionaryNative(path: String): Long
    private external fun initDictionaryFdNative(fd: Int, isMdd: Boolean): Long
    private external fun lookupNative(dictHandle: Long, word: String): Array<String>?
    private external fun destroyNative(dictHandle: Long)
    private external fun getMatchCountNative(dictHandle: Long, word: String): Int
    private external fun getSuggestionsNative(dictHandle: Long, prefix: String): Array<String>?
    private external fun getRegexSuggestionsNative(dictHandle: Long, regex: String): Array<String>?
    
    fun getMatchCount(word: String): Int {
        if (dictionaryHandle == 0L) return 0
        return getMatchCountNative(dictionaryHandle, word)
    }

    fun getRegexSuggestions(regex: String): List<String> {
        if (dictionaryHandle == 0L) return emptyList()
        return getRegexSuggestionsNative(dictionaryHandle, regex)?.toList() ?: emptyList()
    }
}