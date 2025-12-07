package com.waltermelon.vibedict.data

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

object FontUtils {

    /**
     * Attempts to read the "Full Font Name" (ID 4) or "Font Family" (ID 1) from a TTF/OTF file.
     * Returns null if parsing fails.
     */
    fun getFontName(fontFile: File): String? {
        try {
            RandomAccessFile(fontFile, "r").use { raf ->
                // 1. Read Offset Table
                // SFNT version (4 bytes), numTables (2 bytes)
                raf.seek(4)
                val numTables = raf.readUnsignedShort()
                raf.skipBytes(6) // searchRange, entrySelector, rangeShift

                // 2. Find the "name" table
                var nameTableOffset = 0L
                for (i in 0 until numTables) {
                    val tag = raf.readInt() // Read 4 bytes as Int
                    val checkSum = raf.readInt()
                    val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                    val length = raf.readInt()

                    // "name" in ASCII is 0x6E616D65
                    if (tag == 0x6E616D65) {
                        nameTableOffset = offset
                        break
                    }
                }

                if (nameTableOffset == 0L) return null

                // 3. Parse the "name" table
                raf.seek(nameTableOffset)
                val format = raf.readUnsignedShort()
                val count = raf.readUnsignedShort()
                val stringOffset = raf.readUnsignedShort()
                val storageOffset = nameTableOffset + stringOffset

                // We prefer NameID 4 (Full Name), fallback to 1 (Family Name)
                var bestName: String? = null

                for (i in 0 until count) {
                    val platformId = raf.readUnsignedShort()
                    val encodingId = raf.readUnsignedShort()
                    val languageId = raf.readUnsignedShort()
                    val nameId = raf.readUnsignedShort()
                    val length = raf.readUnsignedShort()
                    val offset = raf.readUnsignedShort()

                    // We only care about NameID 1 or 4
                    if (nameId == 1 || nameId == 4) {
                        val savePos = raf.filePointer
                        raf.seek(storageOffset + offset)
                        val buffer = ByteArray(length)
                        raf.readFully(buffer)
                        raf.seek(savePos)

                        // Decode based on platform
                        var text: String? = null
                        if (platformId == 3 && (encodingId == 1 || encodingId == 10)) {
                            // Windows Unicode (UTF-16BE)
                            text = String(buffer, Charset.forName("UTF-16BE"))
                        } else if (platformId == 1 && encodingId == 0) {
                            // Mac Roman
                            text = String(buffer, Charset.forName("MacRoman")) // or UTF-8 fallback
                        }

                        if (text != null) {
                            if (nameId == 4) return text // Found Full Name, return immediately
                            if (nameId == 1) bestName = text // Keep Family Name as fallback
                        }
                    }
                }
                return bestName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

}