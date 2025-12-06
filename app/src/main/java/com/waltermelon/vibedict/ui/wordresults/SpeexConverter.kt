package com.waltermelon.vibedict.ui.wordresults

import org.xiph.speex.SpeexDecoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SpeexConverter {

    fun isSpeexFile(data: ByteArray): Boolean {
        try {
            val reader = OggReader(data)
            val headerPacket = reader.nextPacket() ?: return false
            val header = ByteBuffer.wrap(headerPacket).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8)
            header.get(magic)
            return String(magic).trim() == "Speex"
        } catch (e: Exception) {
            return false
        }
    }

    fun decode(spxData: ByteArray): ByteArray {
        val reader = OggReader(spxData)
        val headerPacket = reader.nextPacket() ?: throw Exception("No header packet")
        
        // Parse Header (80 bytes)
        val header = ByteBuffer.wrap(headerPacket).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8)
        header.get(magic)
        if (String(magic).trim() != "Speex") throw Exception("Not a Speex file")
        
        header.position(36)
        val sampleRate = header.getInt()
        val mode = header.getInt()
        header.position(48)
        val channels = header.getInt()
        
        // Skip Comment Packet (usually the second packet)
        reader.nextPacket()

        val decoder = SpeexDecoder()
        decoder.init(mode, sampleRate, channels, true)
        
        val pcmOutput = ByteArrayOutputStream()
        
        while (true) {
            val packet = reader.nextPacket() ?: break
            // Process packet
            decoder.processData(packet, 0, packet.size)
            if (decoder.processedDataByteSize > 0) {
                val frameSize = decoder.processedDataByteSize
                val decodedFrame = ByteArray(frameSize)
                decoder.getProcessedData(decodedFrame, 0)
                pcmOutput.write(decodedFrame)
            }
        }
        
        val pcmData = pcmOutput.toByteArray()
        return addWavHeader(pcmData, sampleRate, channels)
    }
    
    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val bitrate = sampleRate * channels * 16 / 8 // 16 bit
        
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size
        header.putShort(1) // AudioFormat (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(bitrate)
        header.putShort((channels * 16 / 8).toShort()) // BlockAlign
        header.putShort(16) // BitsPerSample
        header.put("data".toByteArray())
        header.putInt(pcmData.size)
        
        val out = ByteArrayOutputStream()
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
    
    private class OggReader(val data: ByteArray) {
        var pos = 0
        var currentPacketBuffer = ByteArrayOutputStream()
        
        // State for current page iteration
        var currentSegmentTable = IntArray(0)
        var currentSegmentIndex = 0
        
        fun nextPacket(): ByteArray? {
             while (pos < data.size || currentSegmentIndex < currentSegmentTable.size) {
                 
                 // If we need a new page
                 if (currentSegmentIndex >= currentSegmentTable.size) {
                     if (pos + 27 > data.size) return null // End of data or truncated header
                     
                     // Check Capture Pattern
                     if (data[pos] != 0x4F.toByte() || data[pos+1] != 0x67.toByte() || 
                         data[pos+2] != 0x67.toByte() || data[pos+3] != 0x53.toByte()) {
                         // Sync lost, maybe should search? For now, strict.
                         return null
                     }
                     
                     pos += 26 // Skip to 'Page Segments'
                     val pageSegments = data[pos].toInt() and 0xFF
                     pos++
                     
                     // Read Segment Table
                     if (pos + pageSegments > data.size) return null
                     currentSegmentTable = IntArray(pageSegments)
                     for (i in 0 until pageSegments) {
                         currentSegmentTable[i] = data[pos].toInt() and 0xFF
                         pos++
                     }
                     currentSegmentIndex = 0
                 }
                 
                 // Process segments in current page
                 while (currentSegmentIndex < currentSegmentTable.size) {
                     val size = currentSegmentTable[currentSegmentIndex]
                     currentSegmentIndex++
                     
                     if (pos + size > data.size) return null
                     currentPacketBuffer.write(data, pos, size)
                     pos += size
                     
                     if (size < 255) {
                         // End of packet
                         val packet = currentPacketBuffer.toByteArray()
                         currentPacketBuffer.reset()
                         return packet
                     }
                 }
             }
             return null
        }
    }
}
