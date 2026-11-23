package com.visioniptv.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visioniptv.app.data.*
import kotlinx.coroutines.launch

private val Night = Color(0xFF080B12)
private val Teal = Color(0xFF36D6C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VisionHome { url ->
            startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", url))
        } }
    }
}

@Composable
fun VisionHome(play: (String) -> Unit) {
    var selected by remember { mutableStateOf("Home") }
    var playlistUrl by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var status by remember { mutableStateOf("Add an authorised M3U playlist to begin.") }
    val scope = rememberCoroutineScope()
    val repo = remember { IptvRepository() }
    val nav = listOf("Home", "Live TV", "Movies", "Series", "TV Guide", "Catch-up", "My List", "Settings")

    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Night, surface = Color(0xFF151C29))) {
        Row(Modifier.fillMaxSize().background(Night)) {
            Column(Modifier.width(190.dp).fillMaxHeight().padding(18.dp)) {
                Text("VISION", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = Teal, fontSize = 13.sp, letterSpacing = 5.sp)
                Spacer(Modifier.height(28.dp))
                nav.forEach { item ->
                    Text(item, color = if (selected == item) Teal else Color.LightGray,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { selected = item },
                        fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Text(selected, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                when (selected) {
                    "Settings" -> {
                        Text("Playlist setup", color = Teal, fontSize = 18.sp)
                        OutlinedTextField(playlistUrl, { playlistUrl = it },
                            label = { Text("M3U URL") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            scope.launch {
                                status = "Loading playlist…"
                                try {
                                    val account = PlaylistAccount("My playlist", ServerSet(""), m3uUrl = playlistUrl)
                                    channels = repo.loadM3u(account).first
                                    status = "Loaded ${channels.size} channels"
                                } catch (e: Exception) { status = e.message ?: "Could not load playlist" }
                            }
                        }) { Text("Load playlist") }
                        Text(status, color = Color.LightGray)
                        Text("Xtream account setup, backup URL configuration and encrypted credential storage remain to be wired into Settings.", color = Color.Gray)
                    }
                    "Live TV" -> {
                        Text("${channels.size} channels loaded", color = Color.Gray)
                        LazyColumn { items(channels) { ch ->
                            Row(Modifier.fillMaxWidth().clickable { play(ch.streamUrl) }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.number.toString().padStart(3, '0'), color = Teal, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ch.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(ch.group, color = Color.Gray, fontSize = 12.sp)
                                }
                                Text("PLAY", color = Teal)
                            }
                        } }
                    }
                    "Movies", "Series" -> Text("Netflix-style VOD and series browsing UI is the next implementation layer. Repository models and movie endpoint are included as a foundation.", color = Color.Gray)
                    "TV Guide" -> Text("Worldwide XMLTV parser and tvg-id-first matching helper are included. Source selection, downloads, caching and guide grid remain to be connected.", color = Color.Gray)
                    "Catch-up" -> Text("Catch-up playback depends on archive support and the provider-specific timeshift URL format.", color = Color.Gray)
                    else -> {
                        Text("Your entertainment, in one place.", color = Color.LightGray, fontSize = 18.sp)
                        Spacer(Modifier.height(22.dp))
                        Text("Continue watching", color = Teal, fontSize = 18.sp)
                        Text("Load a playlist in Settings, then browse Live TV.", color = Color.Gray)
                    }
                }
            }
        }
    }
}
