package com.kaoyan.wordhelper.util

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

class PronunciationPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onError: (String) -> Unit = {}) {
        release()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(url)
            player.setOnPreparedListener { prepared ->
                prepared.start()
            }
            player.setOnCompletionListener {
                release()
            }
            player.setOnErrorListener { _, _, _ ->
                release()
                onError("发音播放失败")
                true
            }
            player.prepareAsync()
        } catch (_: Throwable) {
            release()
            onError("发音播放失败")
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun rememberPronunciationPlayer(): PronunciationPlayer {
    val player = remember { PronunciationPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    return player
}
