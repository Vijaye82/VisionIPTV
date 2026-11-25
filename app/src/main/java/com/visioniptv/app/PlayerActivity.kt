package com.visioniptv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var retryMessage by mutableStateOf("Connecting to stream…")
    private var playbackError by mutableStateOf(false)
    private var streamUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        streamUrl = intent.getStringExtra("url").orEmpty()
        if (streamUrl.isBlank()) {
            playbackError = true
            retryMessage = "No stream URL was supplied."
        } else {
            player = ExoPlayer.Builder(this).build().also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) retryMessage = "Buffering stream…"
                        if (playbackState == Player.STATE_READY) {
                            playbackError = false
                            retryMessage = ""
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = true
                        if (retryCount < MAX_AUTOMATIC_RETRIES) {
                            retryCount++
                            retryMessage = "Stream interrupted. Retrying ($retryCount/$MAX_AUTOMATIC_RETRIES)…"
                            handler.postDelayed({
                                if (!isFinishing && !isDestroyed) {
                                    exo.seekToDefaultPosition()
                                    exo.prepare()
                                    exo.playWhenReady = true
                                }
                            }, RETRY_DELAY_MS)
                        } else {
                            retryMessage = "Unable to play this stream. Check the provider or try again."
                        }
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(streamUrl))
                exo.prepare()
                exo.playWhenReady = true
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF050A12),
                surface = Color(0xFF101B29),
                primary = Color(0xFF12A8FF)
            )) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    player?.let { exo ->
                        AndroidView(
                            factory = { context -> PlayerView(context).apply { player = exo } },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (playbackError || retryMessage.isNotBlank()) {
                        Column(
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color(0xE6101B29))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(retryMessage, color = Color.White, fontSize = 14.sp)
                            if (playbackError && retryCount >= MAX_AUTOMATIC_RETRIES) {
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = {
                                    retryCount = 0
                                    playbackError = false
                                    handler.removeCallbacksAndMessages(null)
                                    player?.let { exo ->
                                        exo.stop()
                                        exo.setMediaItem(MediaItem.fromUri(streamUrl))
                                        exo.prepare()
                                        exo.playWhenReady = true
                                    }
                                    retryMessage = "Trying the stream again…"
                                }) { Text("Retry stream") }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_AUTOMATIC_RETRIES = 2
        const val RETRY_DELAY_MS = 1800L
    }
}
