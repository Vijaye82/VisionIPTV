package com.visioniptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        player = ExoPlayer.Builder(this).build().also {
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.playWhenReady = true
        }
        setContent {
            AndroidView(factory = { context ->
                PlayerView(context).apply { player = this@PlayerActivity.player }
            }, modifier = Modifier.fillMaxSize())
        }
    }
    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
