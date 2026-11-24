package com.visioniptv.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visioniptv.app.data.*
import kotlinx.coroutines.launch

private val Ink = Color(0xFF070B12)
private val SurfaceDark = Color(0xFF111A24)
private val Accent = Color(0xFF35D5C4)
private val Muted = Color(0xFF9AA8B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisionHome(
                play = { url -> startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", url)) },
                loadPlaylist = { url -> loadM3u(url) }
            )
        }
    }

    private suspend fun loadM3u(url: String): List<TvChannel> {
        val repo = IptvRepository()
        val account = PlaylistAccount("My playlist", ServerSet(""), m3uUrl = url)
        return repo.loadM3u(account).first
    }
}

@Composable
private fun VisionHome(
    play: (String) -> Unit,
    loadPlaylist: suspend (String) -> List<TvChannel>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vision_preferences", android.content.Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf("Home") }
    var playlistUrl by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var status by remember { mutableStateOf("Add an authorised M3U playlist in Settings to begin.") }
    var showLiveCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_live_country_subcategories", true)) }
    var showMovieCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_movie_country_subcategories", true)) }
    var showSeriesCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_series_country_subcategories", true)) }
    var activeCategory by remember { mutableStateOf("All") }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val nav = listOf("Home", "Movies", "Series", "Live TV", "TV Guide", "Catch-up", "My List", "Settings")

    LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) { } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = SurfaceDark,
        onSurface = Color.White, onPrimary = Ink
    )) {
        Row(Modifier.fillMaxSize().background(Ink)) {
            Column(
                Modifier.width(190.dp).fillMaxHeight()
                    .background(Color(0xFF0A111A)).padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                Text("VISION", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = Accent, fontSize = 12.sp, letterSpacing = 5.sp)
                Spacer(Modifier.height(22.dp))
                nav.forEachIndexed { index, item ->
                    var focused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { selected = item; activeCategory = "All" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                focused -> Accent
                                selected == item -> Color(0xFF203442)
                                else -> Color.Transparent
                            },
                            contentColor = if (focused) Ink else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(item, modifier = Modifier.fillMaxWidth(),
                            fontWeight = if (selected == item || focused) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 12.dp)) {
                when (selected) {
                    "Movies" -> CinemaBrowse("Movies", showMovieCountrySubcategories)
                    "Series" -> CinemaBrowse("TV Shows", showSeriesCountrySubcategories)
                    "Live TV" -> {
                        Text("LIVE TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("${channels.size} channels", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        if (channels.isEmpty()) {
                            EmptyPanel("Your live channels will appear here", "Add your M3U playlist in Settings to load channels.")
                        } else {
                            val categories = listOf("All") + channels.map { it.group }.distinct().sorted()
                            if (showLiveCountrySubcategories) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories) { category ->
                                        FilterChip(
                                            selected = activeCategory == category,
                                            onClick = { activeCategory = category },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            val visibleChannels = if (showLiveCountrySubcategories && activeCategory != "All")
                                channels.filter { it.group == activeCategory } else channels
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                items(visibleChannels) { channel ->
                                    FocusChannel(channel, play)
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text("SETTINGS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Personalise your Vision IPTV experience", color = Muted)
                        Spacer(Modifier.height(18.dp))
                        Text("PLAYLIST", color = Accent, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = playlistUrl, onValueChange = { playlistUrl = it },
                            label = { Text("M3U playlist URL") }, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                status = "Loading playlist…"
                                try {
                                    channels = loadPlaylist(playlistUrl)
                                    status = "Loaded ${channels.size} channels"
                                } catch (e: Exception) {
                                    status = e.message ?: "Could not load playlist"
                                }
                            }
                        }) { Text("Load playlist") }
                        Text(status, color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(22.dp))
                        Text("INTERFACE", color = Accent, fontWeight = FontWeight.Bold)
                        Text("Show or hide country-based subcategories independently in Live TV, Movies and Series.", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        SettingToggle("Show Live TV country subcategories", showLiveCountrySubcategories) {
                            showLiveCountrySubcategories = it
                            prefs.edit().putBoolean("show_live_country_subcategories", it).apply()
                            activeCategory = "All"
                        }
                        SettingToggle("Show Movies country subcategories", showMovieCountrySubcategories) {
                            showMovieCountrySubcategories = it
                            prefs.edit().putBoolean("show_movie_country_subcategories", it).apply()
                        }
                        SettingToggle("Show Series country subcategories", showSeriesCountrySubcategories) {
                            showSeriesCountrySubcategories = it
                            prefs.edit().putBoolean("show_series_country_subcategories", it).apply()
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("More account, backup-server, EPG and parental-control settings will be added in later development.", color = Muted, fontSize = 12.sp)
                    }
                    "TV Guide" -> {
                        Text("TV GUIDE", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        EmptyPanel("Worldwide TV guide", "XMLTV parsing foundation is present; source setup and the full guide grid are still to be implemented.")
                    }
                    "Catch-up" -> {
                        Text("CATCH-UP", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        EmptyPanel("Catch-up TV", "Availability and playback depend on your provider's archive support.")
                    }
                    "My List" -> {
                        Text("MY LIST", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        EmptyPanel("Your saved titles", "Your favourites library will appear here when catalogue browsing is connected.")
                    }
                    else -> {
                        Text("VISION IPTV", color = Accent, fontSize = 12.sp, letterSpacing = 3.sp)
                        HeroBanner()
                        Spacer(Modifier.height(18.dp))
                        Text("Explore Vision", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(listOf("Movies", "TV Shows", "Live TV", "TV Guide")) { name ->
                                FocusTile(name) { selected = if (name == "TV Shows") "Series" else name }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaBrowse(title: String, showCategories: Boolean) {
    Text(title.uppercase(), color = Color.White, fontSize = 12.sp, letterSpacing = 3.sp)
    Spacer(Modifier.height(7.dp))
    HeroBanner()
    Spacer(Modifier.height(13.dp))
    if (showCategories) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "New & Trending", "Drama", "Comedy", "Action", "Documentary", "Recently Added")) { label ->
                var focused by remember { mutableStateOf(false) }
                FilterChip(
                    selected = label == "All", onClick = { },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Text("New and Trending", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("Featured", "Popular", "Recently Added", "Continue Watching", "Recommended")) { label ->
            PosterCard(label)
        }
    }
    Spacer(Modifier.height(13.dp))
    Text("Continue Watching", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("Keep Watching", "Your Series", "Recently Played", "My List")) { label ->
            PosterCard(label)
        }
    }
}

@Composable
private fun HeroBanner() {
    Box(
        Modifier.fillMaxWidth().height(190.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF102331), Color(0xFF30424F), Color(0xFF111923))
                ), RoundedCornerShape(12.dp)
            )
            .border(1.dp, Color(0xFF33495A), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f)) {
            Text("VISION ORIGINAL", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(7.dp))
            Text("Featured for you", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Discover movies and series from your connected provider.", color = Color(0xFFE1E7EC), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("EXPLORE NOW", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PosterCard(label: String) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(142.dp).onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Box(
            Modifier.fillMaxWidth().height(92.dp)
                .background(
                    Brush.linearGradient(
                        if (focused) listOf(Accent, Color(0xFF1A5260))
                        else listOf(Color(0xFF344B5D), Color(0xFF17222F), Color(0xFF493B45))
                    ), RoundedCornerShape(7.dp)
                )
                .then(if (focused) Modifier.border(3.dp, Accent, RoundedCornerShape(7.dp)) else Modifier)
                .clickable { }
        ) {
            Text(label.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun FocusTile(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = Modifier.width(145.dp).height(64.dp).onFocusChanged { focused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Accent else SurfaceDark,
            contentColor = if (focused) Ink else Color.White
        )
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun FocusChannel(channel: TvChannel, play: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = { play(channel.streamUrl) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) Accent else SurfaceDark,
            contentColor = if (focused) Ink else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(channel.number.toString().padStart(3, '0'), color = if (focused) Ink else Accent,
                modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontWeight = FontWeight.SemiBold)
                Text(channel.group, color = if (focused) Ink else Muted, fontSize = 11.sp)
            }
            Text("WATCH", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).background(SurfaceDark, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EmptyPanel(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).background(SurfaceDark, RoundedCornerShape(12.dp)).padding(20.dp)
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = Muted, fontSize = 13.sp)
    }
}
