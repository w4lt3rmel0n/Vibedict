package com.waltermelon.vibedict.ui.wordresults

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream

fun playSound(context: Context, data: ByteArray) {
    var mp: MediaPlayer? = null
    try {
        // Create a temporary file to store the audio data
        val tempMp3 = File.createTempFile("temp_audio", "mp3", context.cacheDir)
        val fos = FileOutputStream(tempMp3)
        fos.write(data)
        fos.close()

        mp = MediaPlayer()
        mp.setDataSource(tempMp3.absolutePath)
        mp.prepare()
        mp.start()

        // Clean up the temporary file after playback is complete
        mp.setOnCompletionListener {
            it.release()
            tempMp3.delete()
        }

    } catch (e: Exception) {
        e.printStackTrace()
        // Release the player if an error occurs
        mp?.release()
    }
}