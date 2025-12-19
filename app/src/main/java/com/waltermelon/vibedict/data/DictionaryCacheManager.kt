package com.waltermelon.vibedict.data

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor
import java.security.MessageDigest

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

    /**
     * Retrieves the dictionary hash from cache if valid, or computes it and updates the cache.
     * @return The unique hash ID for the dictionary.
     */
    fun getOrComputeHash(
        context: Context,
        uri: Uri,
        fileSize: Long,
        lastModified: Long,
        baseName: String
    ): String {
        val uriString = uri.toString()
        val cachedEntry = getEntry(uriString)

        if (cachedEntry != null &&
            cachedEntry.size == fileSize &&
            cachedEntry.lastModified == lastModified
        ) {
            return cachedEntry.hash
        }

        // Cache Miss or Stale -> Compute Hash
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val hash = if (pfd != null) {
                val computedHash = computeFileHash(pfd.fileDescriptor)
                pfd.close() 
                computedHash
            } else {
                "unknown_hash_${System.currentTimeMillis()}"
            }

            // Update Cache
            putEntry(
                CacheEntry(
                    uri = uriString,
                    size = fileSize,
                    lastModified = lastModified,
                    hash = hash,
                    name = baseName
                )
            )
            hash
        } catch (e: Exception) {
            e.printStackTrace()
            "unknown_hash_${System.currentTimeMillis()}"
        }
    }

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

            // Reset position just in case (though caller usually opens fresh FD or handles position)
            Os.lseek(fd, 0L, OsConstants.SEEK_SET)

            // Convert to Hex String
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            "unknown_hash_${System.currentTimeMillis()}"
        }
    }
}
