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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import coil.compose.AsyncImage
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
                loadPlaylist = { account -> loadM3u(account) }
            )
        }
    }

    private suspend fun loadM3u(account: PlaylistAccount): Pair<List<TvChannel>, String> {
        return IptvRepository().loadM3u(account)
    }
}

@Composable
private fun VisionHome(
    play: (String) -> Unit,
    loadPlaylist: suspend (PlaylistAccount) -> Pair<List<TvChannel>, String>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context, "vision_preferences", masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    val hasSavedConnection = remember { prefs.getBoolean("connection_configured", false) }
    var showSplash by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(if (hasSavedConnection) "Home" else "Settings") }
    var connectionType by remember { mutableStateOf(prefs.getString("connection_type", "M3U") ?: "M3U") }
    var playlistUrl by remember { mutableStateOf(prefs.getString("playlist_url", "") ?: "") }
    var backupM3u1 by remember { mutableStateOf(prefs.getString("backup_m3u1", "") ?: "") }
    var backupM3u2 by remember { mutableStateOf(prefs.getString("backup_m3u2", "") ?: "") }
    var primaryServer by remember { mutableStateOf(prefs.getString("primary_server", "") ?: "") }
    var backupServer1 by remember { mutableStateOf(prefs.getString("backup_server1", "") ?: "") }
    var backupServer2 by remember { mutableStateOf(prefs.getString("backup_server2", "") ?: "") }
    var backupServer3 by remember { mutableStateOf(prefs.getString("backup_server3", "") ?: "") }
    var backupServer4 by remember { mutableStateOf(prefs.getString("backup_server4", "") ?: "") }
    var accountUsername by remember { mutableStateOf(prefs.getString("account_username", "") ?: "") }
    var accountPassword by remember { mutableStateOf(prefs.getString("account_password", "") ?: "") }
    var primaryEpgUrl by remember { mutableStateOf(prefs.getString("primary_epg_url", "") ?: "") }
    var backupEpgUrl by remember { mutableStateOf(prefs.getString("backup_epg_url", "") ?: "") }
    var epgProgrammes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    var epgStatus by remember { mutableStateOf("EPG not loaded") }
    var activeServerLabel by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var status by remember { mutableStateOf("Add an authorised M3U playlist in Settings to begin.") }
    var showLiveCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_live_country_subcategories", true)) }
    var showMovieCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_movie_country_subcategories", true)) }
    var showSeriesCountrySubcategories by remember { mutableStateOf(prefs.getBoolean("show_series_country_subcategories", true)) }
    var activeCategory by remember { mutableStateOf("All") }
    var favouriteChannelIds by remember {
        mutableStateOf(prefs.getStringSet("favourite_channel_ids", emptySet())?.toSet() ?: emptySet())
    }
    fun setChannelFavourite(channel: TvChannel, favourite: Boolean) {
        val updated = favouriteChannelIds.toMutableSet()
        if (favourite) updated.add(channel.id) else updated.remove(channel.id)
        favouriteChannelIds = updated
        prefs.edit().putStringSet("favourite_channel_ids", updated).apply()
    }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val nav = listOf("Home", "Movies", "Series", "Live TV", "TV Guide", "Catch-up", "My List", "Settings")

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        showSplash = false
        try { firstFocus.requestFocus() } catch (_: Exception) { }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = SurfaceDark,
        onSurface = Color.White, onPrimary = Ink
    )) {
        if (showSplash) {
            VisionSplash()
        } else Row(Modifier.fillMaxSize().background(Ink)) {
            Column(
                Modifier.width(190.dp).fillMaxHeight()
                    .background(Color(0xFF0A111A))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("VISION", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = Accent, fontSize = 12.sp, letterSpacing = 5.sp)
                Text("VERSION 2.1.4", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                nav.forEachIndexed { index, item ->
                    var focused by remember { mutableStateOf(false) }
                    val isSelected = selected == item
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .then(if (index == if (!hasSavedConnection) nav.lastIndex else 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .background(
                                color = when {
                                    focused -> Color(0xFF35D5C4)
                                    isSelected -> Color(0xFF263D50)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(13.dp)
                            )
                            .then(
                                if (focused) Modifier.border(2.dp, Color(0xFF8CF3E8), RoundedCornerShape(13.dp))
                                else if (isSelected) Modifier.border(1.dp, Color(0xFF4B7188), RoundedCornerShape(13.dp))
                                else Modifier
                            )
                            .clickable { selected = item; activeCategory = "All" }
                            .focusable()
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).height(22.dp)
                            .background(
                                if (isSelected || focused) if (focused) Ink else Accent else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            ))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item,
                            color = if (focused) Ink else Color.White,
                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxSize()
                    .then(if (selected != "Live TV") Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 12.dp)
            ) {
                when (selected) {
                    "Movies" -> CinemaBrowse("Movies", showMovieCountrySubcategories)
                    "Series" -> CinemaBrowse("TV Shows", showSeriesCountrySubcategories)
                    "Live TV" -> {
                        Text("LIVE TV", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("${channels.size} channels  •  Live entertainment", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveTab("Favourites", favouriteChannelIds.isNotEmpty())
                            LiveTab("All Channels", true)
                            LiveTab("Categories", showLiveCountrySubcategories)
                        }
                        Spacer(Modifier.height(9.dp))
                        if (channels.isEmpty()) {
                            EmptyPanel("Your live channels will appear here", "Add your M3U or Xtream account in Settings to load channels.")
                        } else {
                            val categories = listOf("All") + channels.map { it.group }.distinct().sorted()
                            if (showLiveCountrySubcategories) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(categories) { category ->
                                        FilterChip(
                                            selected = activeCategory == category,
                                            onClick = { activeCategory = category },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(7.dp))
                            }
                            val visibleChannels = if (showLiveCountrySubcategories && activeCategory != "All")
                                channels.filter { it.group == activeCategory } else channels
                            val favouriteChannels = channels.filter { favouriteChannelIds.contains(it.id) }
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LazyColumn(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (favouriteChannels.isNotEmpty()) {
                                        item { SectionHeading("★  FAVOURITE CHANNELS (${favouriteChannels.size})") }
                                        items(favouriteChannels, key = { "fav-${it.id}" }) { channel ->
                                            FocusChannel(channel, play, true) { setChannelFavourite(channel, it) }
                                        }
                                    }
                                    item { SectionHeading("ALL CHANNELS (${visibleChannels.size})") }
                                    items(visibleChannels, key = { "all-${it.id}" }) { channel ->
                                        FocusChannel(channel, play, favouriteChannelIds.contains(channel.id)) {
                                            setChannelFavourite(channel, it)
                                        }
                                    }
                                }
                                Column(Modifier.weight(0.42f)) {
                                    LivePreviewPanel(channels.firstOrNull())
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text("SETTINGS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Connections & options", color = Muted, fontSize = 13.sp)
                        Text("Choose M3U or Xtream Codes and configure backup addresses.", color = Muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = connectionType == "M3U", onClick = { connectionType = "M3U" }, label = { Text("M3U") })
                            FilterChip(selected = connectionType == "Xtream", onClick = { connectionType = "Xtream" }, label = { Text("Xtream Codes") })
                        }
                        Spacer(Modifier.height(8.dp))
                        if (connectionType == "M3U") {
                            Text("M3U PLAYLIST URLS", color = Accent, fontWeight = FontWeight.Bold)
                            OutlinedTextField(playlistUrl, { playlistUrl = it }, label = { Text("Primary M3U URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupM3u1, { backupM3u1 = it }, label = { Text("Backup M3U URL 1 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupM3u2, { backupM3u2 = it }, label = { Text("Backup M3U URL 2 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            Text("For a self-contained M3U link, username and password can be left blank.", color = Muted, fontSize = 12.sp)
                        } else {
                            Text("XTREAM SERVER URLS", color = Accent, fontWeight = FontWeight.Bold)
                            OutlinedTextField(primaryServer, { primaryServer = it }, label = { Text("Primary server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer1, { backupServer1 = it }, label = { Text("Backup server URL 1 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer2, { backupServer2 = it }, label = { Text("Backup server URL 2 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer3, { backupServer3 = it }, label = { Text("Backup server URL 3 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer4, { backupServer4 = it }, label = { Text("Backup server URL 4 (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("LOGIN DETAILS", color = Accent, fontWeight = FontWeight.Bold)
                        OutlinedTextField(accountUsername, { accountUsername = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        OutlinedTextField(accountPassword, { accountPassword = it }, label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done))
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            scope.launch {
                                status = "Connecting…"
                                activeServerLabel = ""
                                try {
                                    val account = PlaylistAccount(
                                        name = "My playlist",
                                        serverSet = ServerSet(primaryServer, backupServer1, backupServer2, backupServer3, backupServer4),
                                        username = accountUsername,
                                        password = accountPassword,
                                        m3uUrl = playlistUrl,
                                        backupM3uUrls = listOf(backupM3u1, backupM3u2)
                                    )
                                    val result = if (connectionType == "M3U") {
                                        loadPlaylist(account)
                                    } else {
                                        IptvRepository().loadXtreamLiveWithFailover(account)
                                    }
                                    channels = result.first
                                    activeServerLabel = result.second
                                    prefs.edit()
                                        .putString("connection_type", connectionType)
                                        .putString("playlist_url", playlistUrl)
                                        .putString("backup_m3u1", backupM3u1)
                                        .putString("backup_m3u2", backupM3u2)
                                        .putString("primary_server", primaryServer)
                                        .putString("backup_server1", backupServer1)
                                        .putString("backup_server2", backupServer2)
                                        .putString("backup_server3", backupServer3)
                                        .putString("backup_server4", backupServer4)
                                        .putString("account_username", accountUsername)
                                        .putString("account_password", accountPassword)
                                        .putBoolean("connection_configured", true)
                                        .apply()
                                    selected = "Home"
                                    status = "Connected • ${channels.size} live channels"
                                    val providerEpgUrl = if (connectionType == "Xtream" && primaryServer.isNotBlank()) {
                                        val u = java.net.URLEncoder.encode(accountUsername, "UTF-8")
                                        val p = java.net.URLEncoder.encode(accountPassword, "UTF-8")
                                        "${primaryServer.trimEnd('/')}/xmltv.php?username=$u&password=$p"
                                    } else ""
                                    val epgUrls = listOf(providerEpgUrl, primaryEpgUrl, backupEpgUrl).filter { it.isNotBlank() }
                                    if (epgUrls.isNotEmpty()) {
                                        epgStatus = "Checking provider EPG and backup sources…"
                                        try {
                                            val epgResult = IptvRepository().loadEpgFallback(
                                                epgUrls, channels,
                                                java.io.File(context.filesDir, "epg-cache")
                                            )
                                            channels = epgResult.first
                                            epgProgrammes = epgResult.second
                                            epgStatus = "Loaded ${epgProgrammes.size} programme entries"
                                        } catch (_: Exception) {
                                            epgStatus = "Alternative EPG unavailable; live TV remains connected"
                                        }
                                    }
                                } catch (e: Exception) {
                                    status = e.message ?: "Connection failed"
                                }
                            }
                        }) { Text("Connect & Load Live TV") }
                        Text(status, color = Color.White, fontSize = 13.sp)
                        if (activeServerLabel.isNotBlank()) Text("Active source: $activeServerLabel", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Backup URLs should belong to the same compatible IPTV service and accept the same login. Connection details are encrypted on this device.", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("EPG SOURCES & CHANNEL LOGOS", color = Accent, fontWeight = FontWeight.Bold)
                        Text("If your provider's guide is empty or incomplete, Vision IPTV can try these XMLTV sources in order and fill missing programmes and channel icons. Successful feeds are compressed and cached on this device for offline fallback.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(primaryEpgUrl, { primaryEpgUrl = it }, label = { Text("Alternative XMLTV / EPG URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        OutlinedTextField(backupEpgUrl, { backupEpgUrl = it }, label = { Text("Backup XMLTV / EPG URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        Button(onClick = {
                            prefs.edit().putString("primary_epg_url", primaryEpgUrl)
                                .putString("backup_epg_url", backupEpgUrl).apply()
                            scope.launch {
                                epgStatus = "Checking EPG sources…"
                                try {
                                    val result = IptvRepository().loadEpgFallback(
                                        listOf(primaryEpgUrl, backupEpgUrl), channels,
                                        java.io.File(context.filesDir, "epg-cache")
                                    )
                                    channels = result.first
                                    epgProgrammes = result.second
                                    epgStatus = if (result.second.isEmpty()) "No programme data found in configured sources"
                                        else "Loaded ${result.second.size} programme entries"
                                } catch (e: Exception) { epgStatus = e.message ?: "EPG source check failed" }
                            }
                        }) { Text("Test & Load EPG Sources") }
                        Text(epgStatus, color = Muted, fontSize = 11.sp)
                        Text("The last successful XMLTV download is kept on this device and reused if that source is temporarily unavailable.", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("CATEGORY & SUBCATEGORY OPTIONS", color = Accent, fontWeight = FontWeight.Bold)
                        Text("Country subcategory visibility", color = Muted, fontSize = 12.sp)
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
                        Spacer(Modifier.height(12.dp))
                        Text("VISION IPTV BUILD 2.1.4 • EPG CACHE ENABLED", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    "TV Guide" -> GuideScreen(channels, epgProgrammes)
                    "Catch-up" -> CatchupScreen()
                    "My List" -> {
                        Text("MY LIST / FAVOURITES", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Accent, shape = RoundedCornerShape(20.dp)) {
                                Text("My Favourites", color = Ink, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp))
                            }
                            Surface(color = SurfaceDark, shape = RoundedCornerShape(20.dp)) {
                                Text("Continue Watching", color = Color.White,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Favourite channels", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        val savedChannels = channels.filter { favouriteChannelIds.contains(it.id) }
                        if (savedChannels.isEmpty()) {
                            EmptyPanel("No favourites yet", "Open Live TV and select the star beside a channel to add it here.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                savedChannels.forEach { channel ->
                                    FocusChannel(channel, play, true) { setChannelFavourite(channel, it) }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Saved movies & series", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        PosterShelf(listOf("Your saved titles", "Recently watched", "Recommended"))
                    }
                    else -> HomeDashboard(onNavigate = { selected = it })
                }
            }
        }
    }
}


@Composable
private fun VisionSplash() {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF07111D), Color(0xFF102B3A), Color(0xFF07111D)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VISION", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
            Text("IPTV", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 12.sp)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text("YOUR ENTERTAINMENT, YOUR WAY", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun HomeDashboard(onNavigate: (String) -> Unit) {
    Text("HOME", color = Accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    HeroBanner()
    Spacer(Modifier.height(13.dp))
    SectionHeading("CONTINUE WATCHING")
    PosterShelf(listOf("The Last of Us", "House of the Dragon", "Breaking Bad", "Recently Watched"))
    Spacer(Modifier.height(12.dp))
    SectionHeading("NEW & TRENDING")
    PosterShelf(listOf("Featured movie", "Popular series", "New release", "Recommended"))
    Spacer(Modifier.height(12.dp))
    SectionHeading("EXPLORE VISION")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("Movies", "TV Shows", "Live TV", "TV Guide")) { name ->
            FocusTile(name) { onNavigate(if (name == "TV Shows") "Series" else name) }
        }
    }
}

@Composable
private fun CinemaBrowse(title: String, showCategories: Boolean) {
    Text(title.uppercase(), color = Accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    HeroBanner(title)
    Spacer(Modifier.height(11.dp))
    if (showCategories) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("Popular", "New Releases", "Action", "Comedy", "Drama", "Sci-Fi", "Documentary")) { label ->
                FilterChip(selected = label == "Popular", onClick = { }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(9.dp))
    }
    SectionHeading("NEW & TRENDING")
    PosterShelf(listOf("Featured", "Popular", "Recently Added", "Recommended", "New Release"))
    Spacer(Modifier.height(12.dp))
    SectionHeading("CONTINUE WATCHING")
    PosterShelf(listOf("Continue Watching", "Recently Played", "Your List", "Next Episode"))
    Spacer(Modifier.height(12.dp))
    SectionHeading("BROWSE BY GENRE")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("Action", "Comedy", "Drama", "Crime", "Sci-Fi", "Family")) { GenreTile(it) }
    }
}

@Composable
private fun HeroBanner(title: String = "Featured for you") {
    Box(
        Modifier.fillMaxWidth().height(174.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF102331), Color(0xFF30424F), Color(0xFF111923))), RoundedCornerShape(11.dp))
            .border(1.dp, Color(0xFF33495A), RoundedCornerShape(11.dp))
            .padding(19.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f)) {
            Text("VISION ORIGINAL", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Discover movies and series from your connected provider.", color = Color(0xFFE1E7EC), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("EXPLORE NOW   •   DETAILS", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeading(label: String) {
    Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun PosterShelf(labels: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(labels) { PosterCard(it) }
    }
}

@Composable
private fun PosterCard(label: String) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(132.dp).onFocusChanged { focused = it.isFocused }.focusable()) {
        Box(
            Modifier.fillMaxWidth().height(100.dp)
                .background(Brush.linearGradient(if (focused) listOf(Accent, Color(0xFF1A5260)) else listOf(Color(0xFF344B5D), Color(0xFF17222F), Color(0xFF493B45))), RoundedCornerShape(7.dp))
                .then(if (focused) Modifier.border(3.dp, Accent, RoundedCornerShape(7.dp)) else Modifier)
                .clickable { }
        ) {
            Text(label.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GenreTile(label: String) {
    Surface(color = SurfaceDark, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C4558))) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp))
    }
}

@Composable
private fun LiveTab(label: String, active: Boolean) {
    Surface(color = if (active) Accent else SurfaceDark, shape = RoundedCornerShape(18.dp),
        border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C4558))) {
        Text(label, color = if (active) Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}

@Composable
private fun LivePreviewPanel(channel: TvChannel?) {
    Column(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp))
        .border(1.dp, Color(0xFF263B4C), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Box(Modifier.fillMaxWidth().height(126.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF27465A), Color(0xFF101923))), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center) {
            Text("LIVE PREVIEW", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        Text(channel?.name ?: "Select a channel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("NOW  •  Live programme information", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text("NEXT", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Next programme information appears when EPG is connected.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun GuideScreen(channels: List<TvChannel>, programmes: List<Programme>) {
    val now = System.currentTimeMillis()
    Text("TV GUIDE (EPG)", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Worldwide programme guide • local device time", color = Muted, fontSize = 12.sp)
    if (programmes.isEmpty()) {
        Text("No EPG data loaded yet. Add an alternative XMLTV URL in Settings.", color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveTab("Today", true); LiveTab("Tomorrow", false); LiveTab("7 Days", false)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().background(Color(0xFF13202B), RoundedCornerShape(7.dp)).padding(8.dp)) {
        Text("CHANNEL", color = Accent, modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("NOW", color = Accent, modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("NEXT", color = Accent, modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    if (channels.isEmpty()) {
        EmptyPanel("Your TV guide will appear here", "Connect a playlist and configure an XMLTV/EPG source in Settings.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            channels.take(40).forEach { channel ->
                val channelProgrammes = programmes.filter {
                    (it.channelId == channel.tvgId && channel.tvgId.isNotBlank()) ||
                        it.channelId.equals(channel.name, ignoreCase = true)
                }.sortedBy { it.startMillis }
                val current = channelProgrammes.firstOrNull { it.startMillis <= now && it.stopMillis > now }
                val next = channelProgrammes.firstOrNull { it.startMillis > now }
                Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(6.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1.2f), maxLines = 1)
                    Text(current?.title ?: "No EPG data", color = if (current == null) Muted else Color.White,
                        fontSize = 10.sp, modifier = Modifier.weight(1.2f), maxLines = 2)
                    Text(next?.title ?: "—", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1.2f), maxLines = 2)
                }
            }
        }
    }
}
@Composable
private fun CatchupScreen() {
    Text("CATCH-UP", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Watch programmes again when your provider supports archive playback.", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("BBC", "ITV", "Channel 4", "Channel 5", "Sky")) { LiveTab(it, it == "BBC") }
    }
    Spacer(Modifier.height(14.dp))
    SectionHeading("RECENTLY AIRED")
    PosterShelf(listOf("Drama highlights", "News bulletin", "Entertainment", "Documentary", "Sports"))
    Spacer(Modifier.height(14.dp))
    SectionHeading("POPULAR ON CATCH-UP")
    PosterShelf(listOf("Recently aired", "Missed episodes", "Weekend highlights", "More to watch"))
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
private fun FocusChannel(
    channel: TvChannel,
    play: (String) -> Unit,
    isFavourite: Boolean,
    onFavouriteChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var focused by remember { mutableStateOf(false) }
        Button(
            onClick = { play(channel.streamUrl) },
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (focused) Accent else SurfaceDark,
                contentColor = if (focused) Ink else Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(model = channel.logo, contentDescription = "${channel.name} logo",
                        modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(channel.number.toString().padStart(3, '0'),
                    color = if (focused) Ink else Accent, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.name, fontWeight = FontWeight.SemiBold)
                    Text(channel.group, color = if (focused) Ink else Muted, fontSize = 11.sp)
                }
                Text("WATCH", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        var starFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { onFavouriteChange(!isFavourite) },
            modifier = Modifier.size(width = 54.dp, height = 48.dp)
                .onFocusChanged { starFocused = it.isFocused },
            shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (starFocused) Accent else SurfaceDark,
                contentColor = if (starFocused) Ink else if (isFavourite) Accent else Color.White
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(if (isFavourite) "★" else "☆", fontSize = 23.sp, fontWeight = FontWeight.Bold)
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
