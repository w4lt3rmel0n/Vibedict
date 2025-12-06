package com.waltermelon.vibedict.ui.wordresults

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream

import org.xiph.speex.SpeexDecoder
import java.io.ByteArrayInputStream

fun playSound(context: Context, data: ByteArray) {
    if (data.isEmpty()) return

    if (isOgg(data)) {
        if (SpeexConverter.isSpeexFile(data)) {
            playSpx(context, data)
        } else {
            playRawAudio(context, data, "ogg")
        }
        return
    }

    playRawAudio(context, data, "mp3")
}

fun isOgg(data: ByteArray): Boolean {
    // Check for OggS header (0x4F 0x67 0x67 0x53)
    return data.size >= 4 && data[0] == 0x4F.toByte() && data[1] == 0x67.toByte() && data[2] == 0x67.toByte() && data[3] == 0x53.toByte()
}



fun playSpx(context: Context, data: ByteArray) {
    try {
        val wavData = decodeSpxToWav(data)
        playRawAudio(context, wavData, "wav")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun playRawAudio(context: Context, data: ByteArray, extension: String) {
    var mp: MediaPlayer? = null
    try {
        // Create a temporary file to store the audio data
        val tempFile = File.createTempFile("temp_audio", ".$extension", context.cacheDir)
        val fos = FileOutputStream(tempFile)
        fos.write(data)
        fos.close()

        mp = MediaPlayer()
        mp.setDataSource(tempFile.absolutePath)
        mp.prepare()
        mp.start()

        // Clean up the temporary file after playback is complete
        mp.setOnCompletionListener {
            it.release()
            tempFile.delete()
        }

    } catch (e: Exception) {
        e.printStackTrace()
        // Release the player if an error occurs
        mp?.release()
    }
}

fun decodeSpxToWav(spxData: ByteArray): ByteArray {
    return SpeexConverter.decode(spxData)
}