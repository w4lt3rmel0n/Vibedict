package com.waltermelon.vibedict.data

import android.content.Context
import org.json.JSONObject
import java.io.File

object DictionaryCacheManager {
    private const val CACHE_FILE_NAME = "dictionary_cache.json"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    data class CacheEntry(
        val uri: String,
        val size: Long,
        val lastModified: Long,
        val hash: String,
        val name: String
    )

    fun loadCache(context: Context) {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists()) return

        try {
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()

            cache.clear()
            while (keys.hasNext()) {
                val key = keys.next()
                val entryJson = jsonObject.getJSONObject(key)
                cache[key] = CacheEntry(
                    uri = key,
                    size = entryJson.getLong("size"),
                    lastModified = entryJson.getLong("lastModified"),
                    hash = entryJson.getString("hash"),
                    name = entryJson.getString("name")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If cache is corrupted, just clear it
            cache.clear()
        }
    }

    fun saveCache(context: Context) {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        try {
            val jsonObject = JSONObject()
            cache.forEach { (key, entry) ->
                val entryJson = JSONObject().apply {
                    put("size", entry.size)
                    put("lastModified", entry.lastModified)
                    put("hash", entry.hash)
                    put("name", entry.name)
                }
                jsonObject.put(key, entryJson)
            }
            file.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getEntry(uri: String): CacheEntry? {
        return cache[uri]
    }

    fun putEntry(entry: CacheEntry) {
        cache[entry.uri] = entry
    }

    fun prune(validUris: Set<String>) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!validUris.contains(entry.key)) {
                iterator.remove()
            }
        }
    }
}
