package com.visioniptv.app

import android.net.Uri
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
    private var serverIndex = 0
    private var serverUrls: List<String> = emptyList()
    private var retryMessage by mutableStateOf("Connecting to stream…")
    private var playbackError by mutableStateOf(false)
    private var currentUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val originalUrl = intent.getStringExtra("url").orEmpty()
        val serverCandidates = intent.getStringArrayListExtra("server_candidates").orEmpty()
        serverUrls = buildServerUrls(originalUrl, serverCandidates)
        if (serverUrls.isEmpty()) {
            playbackError = true
            retryMessage = "No stream URL was supplied."
        } else {
            currentUrl = serverUrls.first()
            player = ExoPlayer.Builder(this).build().also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_BUFFERING) retryMessage = "Buffering stream…"
                        if (playbackState == Player.STATE_READY) {
                            playbackError = false
                            retryMessage = ""
                            retryCount = 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = true
                        if (retryCount < MAX_RETRIES_PER_SERVER) {
                            retryCount++
                            retryMessage = "Stream interrupted. Retrying ($retryCount/$MAX_RETRIES_PER_SERVER)…"
                            handler.postDelayed({ retryCurrentServer() }, RETRY_DELAY_MS)
                        } else if (serverIndex + 1 < serverUrls.size) {
                            serverIndex++
                            retryCount = 0
                            currentUrl = serverUrls[serverIndex]
                            retryMessage = "Primary stream unavailable. Switching to backup ${serverIndex}…"
                            handler.postDelayed({ playCurrentUrl() }, SWITCH_DELAY_MS)
                        } else {
                            retryMessage = "All configured compatible servers failed. Check your provider or try again."
                        }
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(currentUrl))
                exo.prepare()
                exo.playWhenReady = true
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF050A12), surface = Color(0xFF101B29), primary = Color(0xFF12A8FF))) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    player?.let { exo -> AndroidView(factory = { context -> PlayerView(context).apply { player = exo } }, modifier = Modifier.fillMaxSize()) }
                    if (playbackError || retryMessage.isNotBlank()) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101B29)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(retryMessage, color = Color.White, fontSize = 14.sp)
                            if (playbackError && serverIndex == serverUrls.lastIndex && retryCount >= MAX_RETRIES_PER_SERVER) {
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = {
                                    handler.removeCallbacksAndMessages(null)
                                    retryCount = 0
                                    playbackError = false
                                    retryMessage = "Trying the stream again…"
                                    playCurrentUrl()
                                }) { Text("Retry stream") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildServerUrls(original: String, candidates: List<String>): List<String> {
        if (original.isBlank()) return emptyList()
        val source = Uri.parse(original)
        if (source.scheme.isNullOrBlank() || source.host.isNullOrBlank()) return listOf(original)
        val path = source.encodedPath.orEmpty()
        val query = source.encodedQuery
        val rewritten = candidates.mapNotNull { raw ->
            try {
                val base = Uri.parse(raw.trim().trimEnd('/'))
                if (base.scheme.isNullOrBlank() || base.host.isNullOrBlank()) null
                else base.buildUpon().encodedPath(path).encodedQuery(query).fragment(null).build().toString()
            } catch (_: Exception) { null }
        }
        // Keep the exact URL that was selected during initial connection first.
        return (listOf(original) + rewritten).distinct()
    }

    private fun retryCurrentServer() {
        if (isFinishing || isDestroyed) return
        player?.let { exo -> exo.seekToDefaultPosition(); exo.prepare(); exo.playWhenReady = true }
    }

    private fun playCurrentUrl() {
        if (isFinishing || isDestroyed) return
        player?.let { exo ->
            exo.stop()
            exo.setMediaItem(MediaItem.fromUri(currentUrl))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); player?.release(); player = null; super.onDestroy() }

    private companion object {
        const val MAX_RETRIES_PER_SERVER = 2
        const val RETRY_DELAY_MS = 1800L
        const val SWITCH_DELAY_MS = 700L
    }
}