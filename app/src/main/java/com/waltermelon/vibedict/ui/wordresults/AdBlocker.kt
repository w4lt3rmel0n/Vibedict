package com.waltermelon.vibedict.ui.wordresults

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AdBlocker {
    // Use a volatile reference for thread safety when swapping the list
    @Volatile
    private var blockedDomains: Set<String> = HashSet()

    // StevenBlack's Unified Hosts List (Standard + Malware + Adware)
    private const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val CACHE_FILENAME = "hosts_cache.txt"

    // Basic fallback list to work immediately while downloading
    private val defaultBlockList = setOf(
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "adservice.google.com", "adnxs.com", "facebook.com", "amazon-adsystem.com",
        "criteo.com", "outbrain.com", "taboola.com", "popads.net"
    )

    fun init(context: Context) {
        // 1. Load defaults immediately so the app doesn't crash or wait
        blockedDomains = defaultBlockList

        // 2. Run heavy lifting in background
        CoroutineScope(Dispatchers.IO).launch {
            val cacheFile = File(context.filesDir, CACHE_FILENAME)

            // A. If cache exists, load it first for speed
            if (cacheFile.exists()) {
                loadFromFile(cacheFile)
            }

            // B. Check if we need to download a fresh copy
            // (Download if file doesn't exist OR is older than 24 hours)
            val oneDayInMillis = 24 * 60 * 60 * 1000
            val isOld = (System.currentTimeMillis() - cacheFile.lastModified()) > oneDayInMillis

            if (!cacheFile.exists() || isOld) {
                if (downloadHostsFile(cacheFile)) {
                    Log.d("AdBlocker", "Fresh hosts file downloaded.")
                    loadFromFile(cacheFile) // Reload with new data
                }
            }
        }
    }

    private fun downloadHostsFile(destination: File): Boolean {
        return try {
            Log.d("AdBlocker", "Downloading fresh hosts file...")
            val url = URL(HOSTS_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                Log.e("AdBlocker", "Download failed: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e("AdBlocker", "Download error", e)
            false
        }
    }

    private fun loadFromFile(file: File) {
        try {
            val newSet = HashSet<String>()
            // Add defaults to the new set too
            newSet.addAll(defaultBlockList)

            file.forEachLine { line ->
                val trimmed = line.trim()
                // Standard hosts format: "0.0.0.0 example.com"
                if (!trimmed.startsWith("#") && trimmed.isNotEmpty()) {
                    // Split by whitespace
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        // parts[1] is the domain name
                        newSet.add(parts[1])
                    }
                }
            }

            // Atomic swap
            blockedDomains = newSet
            Log.d("AdBlocker", "Loaded ${blockedDomains.size} rules.")
        } catch (e: Exception) {
            Log.e("AdBlocker", "Error parsing local file", e)
        }
    }

    fun isAd(url: String): Boolean {
        val host = Uri.parse(url).host ?: return false

        // Check exact match
        if (blockedDomains.contains(host)) return true

        // Check subdomains (e.g. ads.google.com)
        var parent = host
        while (parent.contains(".")) {
            if (blockedDomains.contains(parent)) return true
            parent = parent.substringAfter(".")
        }
        return false
    }

    // Cosmetic CSS to hide the empty gaps left by blocked ads
    const val COSMETIC_CSS = """
        div[id*='google_ads'], div[class*='ad-container'], 
        iframe[src*='doubleclick'], .adsbygoogle, 
        [id^='div-gpt-ad'], [class^='ad_'], [class*='-ad '], 
        div[data-ad-unit], a[href*='//click.'] { 
            display: none !important; 
            height: 0 !important; 
            visibility: hidden !important; 
        }
    """
}