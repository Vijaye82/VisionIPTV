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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visioniptv.app.data.*
import kotlinx.coroutines.launch

private val Ink = Color(0xFF050A12)
private val SurfaceDark = Color(0xFF101B29)
private val Accent = Color(0xFF12A8FF)
private val Muted = Color(0xFF9AAFC3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(trace))
                val report = buildString {
                    appendLine("Vision IPTV crash report")
                    appendLine("Thread: " + thread.name)
                    appendLine("Time: " + java.time.Instant.now())
                    appendLine()
                    append(trace.toString())
                }
                java.io.File(filesDir, "vision-crash-report.txt").writeText(report)
            } catch (_: Exception) { }
            previousHandler?.uncaughtException(thread, error)
        }
        setContent {
            VisionHome(
                play = { url ->
                    val key = androidx.security.crypto.MasterKey.Builder(this)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
                    val saved = androidx.security.crypto.EncryptedSharedPreferences.create(
                        this, "vision_preferences", key,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    val backups = if (saved.getString("connection_type", "M3U") == "Xtream") {
                        listOf("primary_server", "backup_server1", "backup_server2", "backup_server3", "backup_server4")
                            .mapNotNull { saved.getString(it, null) }.filter { it.isNotBlank() }
                    } else emptyList()
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", url)
                        .putStringArrayListExtra("server_candidates", ArrayList(backups)))
                },
                playContent = { url, title, id, kind, resumePosition ->
                    val key = androidx.security.crypto.MasterKey.Builder(this)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
                    val saved = androidx.security.crypto.EncryptedSharedPreferences.create(
                        this, "vision_preferences", key,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    val backups = if (saved.getString("connection_type", "M3U") == "Xtream") {
                        listOf("primary_server", "backup_server1", "backup_server2", "backup_server3", "backup_server4")
                            .mapNotNull { saved.getString(it, null) }.filter { it.isNotBlank() }
                    } else emptyList()
                    startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", url)
                        .putExtra("content_title", title).putExtra("content_id", id).putExtra("content_kind", kind)
                        .putExtra("resume_position_ms", resumePosition)
                        .putStringArrayListExtra("server_candidates", ArrayList(backups)))
                },
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
    playContent: (String, String, String, String, Long) -> Unit,
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
    var tmdbToken by remember { mutableStateOf(prefs.getString("tmdb_read_token", "") ?: "") }
    var movies by remember { mutableStateOf<List<CatalogTitle>>(emptyList()) }
    var series by remember { mutableStateOf<List<CatalogTitle>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<CatalogTitle?>(null) }
    var selectedSeriesDetails by remember { mutableStateOf<SeriesDetails?>(null) }
    var seriesEpisodesLoading by remember { mutableStateOf(false) }
    var seriesEpisodesError by remember { mutableStateOf("") }
    var watchHistoryRefresh by remember { mutableIntStateOf(0) }
    var savedMediaKeys by remember { mutableStateOf(prefs.getStringSet("saved_media_keys", emptySet())?.toSet() ?: emptySet()) }
    fun toggleSavedMedia(item: CatalogTitle) {
        val key = item.kind + "|" + item.id
        val updated = savedMediaKeys.toMutableSet()
        if (updated.contains(key)) updated.remove(key) else updated.add(key)
        savedMediaKeys = updated
        prefs.edit().putStringSet("saved_media_keys", updated).apply()
    }
    var mediaStatus by remember { mutableStateOf("Connect an Xtream account to load your Movies and Series library.") }
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
    var pendingProtectedSection by remember { mutableStateOf<String?>(null) }
    var parentalPinInput by remember { mutableStateOf("") }
    var parentalPinError by remember { mutableStateOf("") }
    val parentalProtectedSections = setOf("Movies", "Series", "Catch-up")
    fun requestSection(section: String) {
        val configured = prefs.getString("parental_pin_hash", "").orEmpty().isNotBlank()
        if (prefs.getBoolean("setting_parental_enabled", false) && parentalProtectedSections.contains(section) && configured) {
            pendingProtectedSection = section
            parentalPinInput = ""
            parentalPinError = ""
        } else { selected = section; activeCategory = "All" }
    }

    suspend fun reconnectSavedConnection() {
        if (!hasSavedConnection || !prefs.getBoolean("setting_auto_connect", true)) return
        status = "Restoring your saved connection…"
        try {
            val account = PlaylistAccount(
                name = "My playlist",
                serverSet = ServerSet(primaryServer, backupServer1, backupServer2, backupServer3, backupServer4),
                username = accountUsername,
                password = accountPassword,
                m3uUrl = playlistUrl,
                backupM3uUrls = listOf(backupM3u1, backupM3u2),
                epgUrls = listOf(primaryEpgUrl, backupEpgUrl).filter { it.isNotBlank() }
            )
            val result = if (connectionType == "M3U") {
                loadPlaylist(account)
            } else {
                IptvRepository().loadXtreamLiveWithFailover(account)
            }
            channels = result.first
            activeServerLabel = result.second
            movies = emptyList()
            series = emptyList()
            selectedMedia = null

            if (connectionType == "Xtream") {
                mediaStatus = "Loading your Movies and Series…"
                try {
                    val catalog = MediaCatalogRepository()
                    movies = catalog.loadMovies(account, activeServerLabel)
                    series = catalog.loadSeries(account, activeServerLabel)
                    mediaStatus = "${movies.size} movies • ${series.size} series loaded"
                } catch (catalogError: Exception) {
                    mediaStatus = "Live TV connected; catalogue unavailable: ${catalogError.message ?: "provider error"}"
                }
            } else {
                mediaStatus = "M3U connection restored. Movies and Series require an Xtream-compatible catalogue."
            }

            val providerEpgUrl = if (connectionType == "Xtream" && activeServerLabel.isNotBlank()) {
                val u = java.net.URLEncoder.encode(accountUsername, "UTF-8")
                val p = java.net.URLEncoder.encode(accountPassword, "UTF-8")
                "${activeServerLabel.trimEnd('/')}/xmltv.php?username=$u&password=$p"
            } else ""
            val epgUrls = listOf(providerEpgUrl, primaryEpgUrl, backupEpgUrl).filter { it.isNotBlank() }
            if (epgUrls.isNotEmpty()) {
                epgStatus = "Refreshing programme guide…"
                try {
                    val epgResult = IptvRepository().loadEpgFallback(
                        epgUrls, channels, java.io.File(context.filesDir, "epg-cache")
                    )
                    channels = epgResult.first
                    epgProgrammes = epgResult.second
                    epgStatus = "Loaded ${epgProgrammes.size} programme entries"
                } catch (_: Exception) {
                    epgStatus = "Guide unavailable; live channels remain connected"
                }
            }
            status = "Reconnected • ${channels.size} live channels"
        } catch (e: Exception) {
            status = "Reconnect failed: ${e.message ?: "check your connection settings"}"
            mediaStatus = "Reconnect required. Open Settings and try Connect again."
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        showSplash = false
        try { firstFocus.requestFocus() } catch (_: Exception) { }
        reconnectSavedConnection()
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = SurfaceDark,
        onSurface = Color.White, onPrimary = Ink
    )) {
        if (showSplash) {
            VisionSplash()
        } else {
            if (pendingProtectedSection != null) {
                AlertDialog(
                    onDismissRequest = { pendingProtectedSection = null },
                    title = { Text("Parental PIN required") },
                    text = {
                        Column {
                            Text("Enter your PIN to open ${pendingProtectedSection}.")
                            OutlinedTextField(
                                value = parentalPinInput,
                                onValueChange = { parentalPinInput = it.filter(Char::isDigit).take(8); parentalPinError = "" },
                                label = { Text("4–8 digit PIN") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                singleLine = true
                            )
                            if (parentalPinError.isNotBlank()) Text(parentalPinError, color = Color(0xFFFF7777))
                        }
                    },
                    confirmButton = { TextButton(onClick = {
                        val ok = parentalPinInput.length in 4..8 && verifyParentalPin(
                            parentalPinInput, prefs.getString("parental_pin_salt", "").orEmpty(),
                            prefs.getString("parental_pin_hash", "").orEmpty())
                        if (ok) {
                            selected = pendingProtectedSection ?: "Home"
                            activeCategory = "All"
                            pendingProtectedSection = null
                            parentalPinInput = ""
                        } else parentalPinError = "Incorrect PIN. Try again."
                    }) { Text("Unlock") } },
                    dismissButton = { TextButton(onClick = { pendingProtectedSection = null }) { Text("Cancel") } }
                )
            }
            Row(Modifier.fillMaxSize().background(Ink)) {
            Column(
                Modifier.width(190.dp).fillMaxHeight()
                    .background(Color(0xFF071321))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text("VISION", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("IPTV", color = Accent, fontSize = 12.sp, letterSpacing = 5.sp)
                Text("VISION IPTV", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
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
                                    isSelected -> Color(0xFF073B68)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(13.dp)
                            )
                            .then(
                                if (focused) Modifier.border(2.dp, Color(0xFF8CF3E8), RoundedCornerShape(13.dp))
                                else if (isSelected) Modifier.border(1.dp, Color(0xFF168FE0), RoundedCornerShape(13.dp))
                                else Modifier
                            )
                            .clickable { requestSection(item) }
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
                    "Movies" -> CatalogScreen("Movies", movies, selectedMedia, mediaStatus,
                        onSelect = { item ->
                            selectedMedia = item
                            if (tmdbToken.isNotBlank() && item.tmdbId == null) scope.launch {
                                try { selectedMedia = MediaCatalogRepository().enrich(item, tmdbToken) ?: item }
                                catch (_: Exception) { selectedMedia = item }
                            }
                        }, onPlay = { playContent(it.streamUrl, it.title, it.id, "movie", 0L) }, isSaved = { savedMediaKeys.contains("movie|" + it.id) }, onToggleSaved = { toggleSavedMedia(it) }, onBack = { selectedMedia = null })
                    "Series" -> CatalogScreen("Series", series, selectedMedia, mediaStatus,
                        seriesDetails = selectedSeriesDetails,
                        episodesLoading = seriesEpisodesLoading,
                        episodesError = seriesEpisodesError,
                        onSelect = { item ->
                            selectedMedia = item
                            selectedSeriesDetails = null
                            seriesEpisodesError = ""
                            seriesEpisodesLoading = true
                            scope.launch {
                                try {
                                    val account = PlaylistAccount(
                                        name = "My playlist",
                                        serverSet = ServerSet(primaryServer, backupServer1, backupServer2, backupServer3, backupServer4),
                                        username = accountUsername,
                                        password = accountPassword
                                    )
                                    val details = MediaCatalogRepository().loadSeriesDetails(account, activeServerLabel, item.id)
                                    selectedSeriesDetails = details
                                    if (details.episodes.isEmpty()) seriesEpisodesError = "Your provider returned no episodes for this series."
                                } catch (e: Exception) {
                                    seriesEpisodesError = "Could not load episodes: ${e.message ?: "provider error"}"
                                } finally {
                                    seriesEpisodesLoading = false
                                }
                            }
                            if (tmdbToken.isNotBlank() && item.tmdbId == null) scope.launch {
                                try { selectedMedia = MediaCatalogRepository().enrich(item, tmdbToken) ?: item }
                                catch (_: Exception) { selectedMedia = item }
                            }
                        }, onPlay = { playContent(it.streamUrl, it.title, it.id, "movie", 0L) },
                        onEpisodePlay = { episode ->
                            playContent(episode.streamUrl, "${selectedMedia?.title.orEmpty()} - ${episode.title}",
                                episode.id, "episode:${selectedMedia?.id.orEmpty()}", 0L)
                        },
                        isSaved = { savedMediaKeys.contains("tv|" + it.id) },
                        onToggleSaved = { toggleSavedMedia(it) },
                        onBack = { selectedMedia = null; selectedSeriesDetails = null; seriesEpisodesError = "" })
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
                    "TV Guide" -> GuideScreen(channels, epgProgrammes)
                    "Catch-up" -> CatchupScreen(
                        channels = channels,
                        programmes = epgProgrammes,
                        onPlayArchive = { channel, programme ->
                            val durationMinutes = ((programme.stopMillis - programme.startMillis) / 60_000L).coerceAtLeast(1L)
                            val startUtc = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(java.util.Date(programme.startMillis))
                            val startEpoch = programme.startMillis / 1000L
                            val endEpoch = programme.stopMillis / 1000L
                            val template = channel.archiveTemplate
                            val archiveUrl = if (template.contains("{")) {
                                template.replace("{duration}", durationMinutes.toString())
                                    .replace("{start}", startUtc).replace("{utc}", startEpoch.toString())
                                    .replace("{utcend}", endEpoch.toString()).replace("{timestamp}", startEpoch.toString())
                                    .replace("{channel}", channel.id).replace("{id}", channel.id).replace("{stream}", channel.id)
                            } else template
                            if (archiveUrl.isNotBlank()) playContent(
                                archiveUrl, channel.name + " • " + programme.title,
                                channel.id + "|" + programme.startMillis, "catchup", 0L
                            )
                        }
                    )
                    "Settings" -> {
                        Text("SETTINGS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("Connections & options", color = Muted, fontSize = 13.sp)
                        Text("Choose a category below. Settings are saved on this device.", color = Muted)
                        Spacer(Modifier.height(10.dp))
                        SettingsHub(prefs = prefs)
                        Spacer(Modifier.height(16.dp))
                        Text("TV SERVICE CONNECTION", color = Accent, fontWeight = FontWeight.Bold)
                        Text("Configure your playlist, account credentials and compatible backup servers.", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = connectionType == "M3U", onClick = { connectionType = "M3U" }, label = { Text("M3U") })
                            FilterChip(selected = connectionType == "Xtream", onClick = { connectionType = "Xtream" }, label = { Text("Xtream Codes") })
                        }
                        Spacer(Modifier.height(8.dp))
                        if (connectionType == "M3U") {
                            Text("M3U PLAYLIST URLS", color = Accent, fontWeight = FontWeight.Bold)
                            OutlinedTextField(playlistUrl, { playlistUrl = it }, label = { Text("Primary M3U URL") }, placeholder = { Text("Paste your main playlist link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupM3u1, { backupM3u1 = it }, label = { Text("Backup M3U URL 1 (optional)") }, placeholder = { Text("Paste first backup playlist link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupM3u2, { backupM3u2 = it }, label = { Text("Backup M3U URL 2 (optional)") }, placeholder = { Text("Paste second backup playlist link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            Text("For a self-contained M3U link, username and password can be left blank.", color = Muted, fontSize = 12.sp)
                        } else {
                            Text("XTREAM SERVER URLS", color = Accent, fontWeight = FontWeight.Bold)
                            OutlinedTextField(primaryServer, { primaryServer = it }, label = { Text("Primary server URL") }, placeholder = { Text("e.g. https://your-provider.com:port") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer1, { backupServer1 = it }, label = { Text("Backup server URL 1 (optional)") }, placeholder = { Text("Paste compatible backup server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer2, { backupServer2 = it }, label = { Text("Backup server URL 2 (optional)") }, placeholder = { Text("Paste compatible backup server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer3, { backupServer3 = it }, label = { Text("Backup server URL 3 (optional)") }, placeholder = { Text("Paste compatible backup server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                            OutlinedTextField(backupServer4, { backupServer4 = it }, label = { Text("Backup server URL 4 (optional)") }, placeholder = { Text("Paste compatible backup server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("LOGIN DETAILS", color = Accent, fontWeight = FontWeight.Bold)
                        OutlinedTextField(accountUsername, { accountUsername = it }, label = { Text("Username") }, placeholder = { Text("Enter your IPTV username") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        OutlinedTextField(accountPassword, { accountPassword = it }, label = { Text("Password") }, placeholder = { Text("Enter your IPTV password") },
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
                                    movies = emptyList()
                                    series = emptyList()
                                    selectedMedia = null
                                    if (connectionType == "Xtream") {
                                        mediaStatus = "Loading Movies and Series from your provider…"
                                        try {
                                            val catalog = MediaCatalogRepository()
                                            movies = catalog.loadMovies(account, activeServerLabel)
                                            series = catalog.loadSeries(account, activeServerLabel)
                                            mediaStatus = "${movies.size} movies • ${series.size} series loaded"
                                        } catch (catalogError: Exception) {
                                            mediaStatus = "Live TV connected; VOD catalogue unavailable: ${catalogError.message ?: "provider error"}"
                                        }
                                    } else {
                                        mediaStatus = "M3U playlists provide Live TV only here. Xtream login is needed for Movies and Series."
                                    }
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
                                        .putString("primary_epg_url", primaryEpgUrl)
                                        .putString("backup_epg_url", backupEpgUrl)
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
                        Text("MOVIE & SERIES INFORMATION", color = Accent, fontWeight = FontWeight.Bold)
                        Text("Optional TMDb API Read Access Token. This enriches provider titles with artwork, ratings, descriptions, genres and cast. Keep this token private.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(tmdbToken, { tmdbToken = it }, label = { Text("TMDb API Read Access Token") },
                            placeholder = { Text("Paste your TMDb Bearer token") }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Button(onClick = {
                            prefs.edit().putString("tmdb_read_token", tmdbToken.trim()).apply()
                            mediaStatus = "TMDb token saved. Open a movie or series to load its extra information."
                        }) { Text("Save TMDb Token") }
                        Text("TMDb credits will be shown in About. This product uses the TMDb API but is not endorsed or certified by TMDb.", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("EPG SOURCES & CHANNEL LOGOS", color = Accent, fontWeight = FontWeight.Bold)
                        Text("If your provider's guide is empty or incomplete, Vision IPTV can try these XMLTV sources in order and fill missing programmes and channel icons. Successful feeds are compressed and cached on this device for offline fallback.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(primaryEpgUrl, { primaryEpgUrl = it }, label = { Text("Alternative XMLTV / EPG URL") }, placeholder = { Text("Paste primary XMLTV guide link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
                        OutlinedTextField(backupEpgUrl, { backupEpgUrl = it }, label = { Text("Backup XMLTV / EPG URL") }, placeholder = { Text("Paste backup XMLTV guide link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next))
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
                        Text("DIAGNOSTICS", color = Accent, fontWeight = FontWeight.Bold)
                        Text("If Vision IPTV closes unexpectedly, reopen the app and share the technical crash report here. It does not include your saved IPTV password.", color = Muted, fontSize = 12.sp)
                        Button(onClick = {
                            val reportFile = java.io.File(context.filesDir, "vision-crash-report.txt")
                            val report = if (reportFile.exists()) reportFile.readText() else "No previous crash report is available. Reproduce the crash, reopen Vision IPTV, then share this report."
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Vision IPTV crash report")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Vision IPTV diagnostics"))
                        }) { Text("Share crash report") }
                        Text("VISION IPTV BUILD 2.1.6 • EPG CACHE ENABLED", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    "TV Guide" -> GuideScreen(channels, epgProgrammes)
                    "Catch-up" -> CatchupScreen(
                        channels = channels,
                        programmes = epgProgrammes,
                        onPlayArchive = { channel, programme ->
                            val durationMinutes = ((programme.stopMillis - programme.startMillis) / 60_000L).coerceAtLeast(1L)
                            val startUtc = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(java.util.Date(programme.startMillis))
                            val startEpoch = programme.startMillis / 1000L
                            val endEpoch = programme.stopMillis / 1000L
                            val template = channel.archiveTemplate
                            val archiveUrl = if (template.contains("{")) {
                                template.replace("{duration}", durationMinutes.toString())
                                    .replace("{start}", startUtc).replace("{utc}", startEpoch.toString())
                                    .replace("{utcend}", endEpoch.toString()).replace("{timestamp}", startEpoch.toString())
                                    .replace("{channel}", channel.id).replace("{id}", channel.id).replace("{stream}", channel.id)
                            } else template
                            if (archiveUrl.isNotBlank()) playContent(
                                archiveUrl, channel.name + " • " + programme.title,
                                channel.id + "|" + programme.startMillis, "catchup", 0L
                            )
                        }
                    )
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
                        val historyRefreshSnapshot = watchHistoryRefresh
                        val watchPrefs = context.getSharedPreferences("vision_watch_progress", android.content.Context.MODE_PRIVATE)
                        val watchHistory = try {
                            org.json.JSONArray(watchPrefs.getString("watch_history", "[]") ?: "[]")
                        } catch (_: Exception) { org.json.JSONArray() }
                        if (watchHistory.length() > 0) {
                            Spacer(Modifier.height(14.dp))
                            Text("CONTINUE WATCHING", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (index in 0 until watchHistory.length()) {
                                    val entry = watchHistory.optJSONObject(index) ?: continue
                                    val watchedId = entry.optString("content_id")
                                    val watchedTitle = entry.optString("title")
                                    val watchedKind = entry.optString("kind")
                                    val watchedPosition = entry.optLong("position_ms")
                                    val watchedDuration = entry.optLong("duration_ms")
                                    if (watchedId.isBlank() || watchedTitle.isBlank() || watchedPosition <= 0L) continue
                                    val percent = if (watchedDuration > 0L) (watchedPosition * 100L / watchedDuration).coerceIn(0L, 100L) else 0L
                                    Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(9.dp)).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(watchedTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text(if (watchedDuration > 0L) "$percent% watched • ${watchedPosition / 60000L} min" else "${watchedPosition / 60000L} min watched",
                                                color = Muted, fontSize = 11.sp)
                                        }
                                        Button(onClick = {
                                            if (watchedKind == "movie") {
                                                val item = movies.firstOrNull { it.id == watchedId }
                                                if (item != null) playContent(item.streamUrl, item.title, item.id, "movie", watchedPosition)
                                                else mediaStatus = "This movie is not in the currently loaded provider catalogue."
                                            } else if (watchedKind.startsWith("episode:")) {
                                                val seriesId = watchedKind.removePrefix("episode:")
                                                scope.launch {
                                                    try {
                                                        val account = PlaylistAccount(
                                                            name = "My playlist",
                                                            serverSet = ServerSet(primaryServer, backupServer1, backupServer2, backupServer3, backupServer4),
                                                            username = accountUsername, password = accountPassword
                                                        )
                                                        val details = MediaCatalogRepository().loadSeriesDetails(account, activeServerLabel, seriesId)
                                                        val episode = details.episodes.firstOrNull { it.id == watchedId }
                                                        if (episode != null) playContent(episode.streamUrl, watchedTitle, episode.id, watchedKind, watchedPosition)
                                                        else mediaStatus = "The saved episode is no longer available from this provider."
                                                    } catch (e: Exception) {
                                                        mediaStatus = "Could not resume episode: ${e.message ?: "provider error"}"
                                                    }
                                                }
                                            }
                                        }) { Text("Resume") }
                                        TextButton(onClick = {
                                            val remaining = org.json.JSONArray()
                                            for (i in 0 until watchHistory.length()) {
                                                val saved = watchHistory.optJSONObject(i) ?: continue
                                                if (saved.optString("key") != entry.optString("key")) remaining.put(saved)
                                            }
                                            watchPrefs.edit().putString("watch_history", remaining.toString()).apply()
                                            watchHistoryRefresh++
                                        }) { Text("Remove") }
                                    }
                                }
                            }
                            TextButton(onClick = {
                                watchPrefs.edit().remove("watch_history").apply()
                                watchHistoryRefresh++
                            }) { Text("Clear watch history") }
                        } else {
                            EmptyPanel("Nothing to continue yet", "Movies and episodes you start watching will appear here.")
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
                        val savedTitles = (movies + series).filter { savedMediaKeys.contains(it.kind + "|" + it.id) }
                        if (savedTitles.isEmpty()) {
                            EmptyPanel("Your list is empty", "Open a movie or series and choose Add to My List. Saved titles stay on this device.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                savedTitles.forEach { item ->
                                    Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(9.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.width(48.dp).height(68.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text(if (item.kind == "movie") "Movie" else "Series", color = Muted, fontSize = 11.sp)
                                        }
                                        TextButton(onClick = {
                                            selectedMedia = item
                                            selected = if (item.kind == "movie") "Movies" else "Series"
                                            if (item.kind == "tv") {
                                                selectedSeriesDetails = null
                                                seriesEpisodesError = ""
                                                seriesEpisodesLoading = true
                                                scope.launch {
                                                    try {
                                                        val account = PlaylistAccount(
                                                            name = "My playlist",
                                                            serverSet = ServerSet(primaryServer, backupServer1, backupServer2, backupServer3, backupServer4),
                                                            username = accountUsername,
                                                            password = accountPassword
                                                        )
                                                        val details = MediaCatalogRepository().loadSeriesDetails(account, activeServerLabel, item.id)
                                                        selectedSeriesDetails = details
                                                        if (details.episodes.isEmpty()) {
                                                            seriesEpisodesError = "Your provider returned no episodes for this series."
                                                        }
                                                    } catch (e: Exception) {
                                                        seriesEpisodesError = "Could not load episodes: ${e.message ?: "provider error"}"
                                                    } finally {
                                                        seriesEpisodesLoading = false
                                                    }
                                                }
                                            }
                                        }) { Text("Open") }
                                        TextButton(onClick = { toggleSavedMedia(item) }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                    else -> Column {
                        HomeDashboard(
                            movies = movies,
                            series = series,
                            channels = channels,
                            programmes = epgProgrammes,
                            onNavigate = { selected = it },
                            onPlayMovie = { item -> playContent(item.streamUrl, item.title, item.id, "movie", 0L) },
                            onPlayChannel = { channel -> play(channel.streamUrl) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = Muted, fontSize = 11.sp)
                        if (activeServerLabel.isNotBlank()) {
                            Text("Connected source: $activeServerLabel", color = Accent, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        }
    }
}


private fun parentalPinHash(pin: String, salt: ByteArray): String {
    val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
    return try {
        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            .encoded.joinToString("") { "%02x".format(it) }
    } finally { spec.clearPassword() }
}

private fun verifyParentalPin(pin: String, saltHex: String, expectedHash: String): Boolean = try {
    val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val actual = parentalPinHash(pin, salt)
    java.security.MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))
} catch (_: Exception) { false }

@Composable
private fun ParentalControlsPanel(prefs: android.content.SharedPreferences) {
    var enabled by remember { mutableStateOf(prefs.getBoolean("setting_parental_enabled", false)) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var changing by remember { mutableStateOf(false) }
    val configured = prefs.getString("parental_pin_hash", "").orEmpty().isNotBlank()
    Text("Protect selected entertainment sections with a local PIN.", color = Muted, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(SurfaceDark, RoundedCornerShape(9.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Enable parental controls", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(if (configured) "PIN configured on this device" else "Set a PIN before enabling protection", color = Muted, fontSize = 10.sp)
        }
        Switch(checked = enabled, onCheckedChange = {
            if (it && !configured) message = "Set a PIN first, then enable protection."
            else { enabled = it; prefs.edit().putBoolean("setting_parental_enabled", it).apply()
                message = if (it) "Protection enabled." else "Protection disabled." }
        })
    }
    Text("Protected sections: Movies, Series and Catch-up. Live TV and TV Guide remain accessible.", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    if (configured && !changing) {
        OutlinedButton(onClick = { changing = true; currentPin = ""; newPin = ""; confirmPin = ""; message = "" }) { Text("Change parental PIN") }
    } else {
        if (configured) OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit).take(8) },
            label = { Text("Current PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword), singleLine = true)
        OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(8) },
            label = { Text(if (configured) "New PIN (4–8 digits)" else "Create PIN (4–8 digits)") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword), singleLine = true)
        OutlinedTextField(confirmPin, { confirmPin = it.filter(Char::isDigit).take(8) },
            label = { Text("Confirm PIN") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val validCurrent = !configured || verifyParentalPin(currentPin,
                    prefs.getString("parental_pin_salt", "").orEmpty(), prefs.getString("parental_pin_hash", "").orEmpty())
                when {
                    !validCurrent -> message = "Current PIN is incorrect."
                    newPin.length !in 4..8 -> message = "Choose a PIN with 4 to 8 digits."
                    newPin != confirmPin -> message = "The PIN entries do not match."
                    else -> {
                        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                        val saltHex = salt.joinToString("") { "%02x".format(it) }
                        prefs.edit().putString("parental_pin_salt", saltHex).putString("parental_pin_hash", parentalPinHash(newPin, salt)).apply()
                        if (!configured) { enabled = true; prefs.edit().putBoolean("setting_parental_enabled", true).apply() }
                        changing = false; currentPin = ""; newPin = ""; confirmPin = ""
                        message = "PIN saved securely."
                    }
                }
            }) { Text(if (configured) "Save new PIN" else "Save PIN") }
            if (configured) TextButton(onClick = { changing = false; message = "" }) { Text("Cancel") }
        }
    }
    if (message.isNotBlank()) Text(message, color = Accent, fontSize = 11.sp)
    Text("PIN is stored as a salted PBKDF2 hash in encrypted app preferences. If forgotten, clearing app data resets local settings.", color = Muted, fontSize = 10.sp)
}

@Composable
private fun PersistentSettingToggle(prefs: android.content.SharedPreferences, key: String, label: String, defaultValue: Boolean = false, description: String? = null) {
    var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        SettingToggle(label, checked) {
            checked = it
            prefs.edit().putBoolean(key, it).apply()
        }
        if (description != null) Text(description, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp, bottom = 4.dp))
    }
}

@Composable
private fun SettingsHub(prefs: android.content.SharedPreferences) {
    val categories = listOf("General", "Appearance", "Playback", "Subtitles", "TV Guide", "TV Services", "Channels & Library", "Recordings", "Parental Controls", "Account", "Help & About")
    var selectedCategory by remember { mutableStateOf("General") }
    Text("SETTINGS CATEGORIES  •  SELECT ONE", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(categories) { category ->
            val active = selectedCategory == category
            Surface(
                onClick = { selectedCategory = category },
                color = if (active) Accent else Color(0xFF142638),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    if (active) 2.dp else 1.dp,
                    if (active) Color.White else Color(0xFF36516A)
                )
            ) {
                Text(
                    (if (active) "✓  " else "") + category,
                    color = if (active) Ink else Color.White,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().background(Color(0xFF102A40), RoundedCornerShape(12.dp)).border(2.dp, Accent, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text("CURRENT SECTION", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(selectedCategory.uppercase(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        when (selectedCategory) {
            "General" -> {
                PersistentSettingToggle(prefs, "setting_auto_connect", "Reconnect to saved playlist on startup", true, "Uses the saved connection when automatic restoration is available.")
                PersistentSettingToggle(prefs, "setting_resume_last", "Return to Home on startup", true)
                PersistentSettingToggle(prefs, "setting_confirm_exit", "Confirm before exiting")
                PersistentSettingToggle(prefs, "setting_show_clock", "Show clock in the app", true)
                PersistentSettingToggle(prefs, "setting_keep_screen_on", "Keep screen awake during playback", true)
            }
            "Appearance" -> {
                PersistentSettingToggle(prefs, "setting_compact_rows", "Use compact channel rows")
                PersistentSettingToggle(prefs, "setting_show_logos", "Show channel logos when available", true)
                PersistentSettingToggle(prefs, "setting_animations", "Enable interface animations", true)
                PersistentSettingToggle(prefs, "setting_large_text", "Use larger interface text")
                Text("Vision uses its dark navy and electric-blue theme in this build. More theme choices will follow.", color = Muted, fontSize = 11.sp)
            }
            "Playback" -> {
                PersistentSettingToggle(prefs, "setting_auto_retry", "Retry a stream when playback fails", true)
                PersistentSettingToggle(prefs, "setting_remember_player", "Remember last playback choice", true)
                PersistentSettingToggle(prefs, "setting_audio_passthrough", "Allow audio passthrough when supported")
                Text("Decoder and buffer selection are not exposed by the current player build yet.", color = Muted, fontSize = 11.sp)
            }
            "Subtitles" -> {
                PersistentSettingToggle(prefs, "setting_subtitles_enabled", "Enable subtitles when available", true)
                PersistentSettingToggle(prefs, "setting_subtitles_cc", "Prefer closed captions")
                Text("Subtitle language, size, delay and online search need player-track and subtitle-provider integration.", color = Muted, fontSize = 11.sp)
            }
            "TV Guide" -> {
                PersistentSettingToggle(prefs, "setting_epg_auto_refresh", "Refresh EPG when connecting", true)
                PersistentSettingToggle(prefs, "setting_epg_show_empty", "Show channels without programme data", true)
                PersistentSettingToggle(prefs, "setting_epg_local_time", "Display programme times in device timezone", true)
                Text("XMLTV primary/backup URLs and EPG testing are available in the TV Service section below.", color = Muted, fontSize = 11.sp)
            }
            "TV Services" -> {
                PersistentSettingToggle(prefs, "setting_service_failover", "Try configured backup servers", true)
                PersistentSettingToggle(prefs, "setting_service_save", "Save connection details securely", true)
                Text("M3U and Xtream credentials, primary/backup URLs, and XMLTV sources are configured below.", color = Muted, fontSize = 11.sp)
            }
            "Channels & Library" -> {
                PersistentSettingToggle(prefs, "setting_hide_empty_groups", "Hide empty channel groups", true)
                PersistentSettingToggle(prefs, "setting_sort_channels", "Sort channels by name")
                PersistentSettingToggle(prefs, "setting_show_country_groups", "Show country/category subgroups", true)
                PersistentSettingToggle(prefs, "setting_keep_favourites", "Keep favourites on this device", true)
            }
            "Recordings" -> {
                PersistentSettingToggle(prefs, "setting_recording_notifications", "Notify when recording support is available")
                Text("Recording is not implemented yet. It requires a compatible provider API or recording destination; these switches do not start recordings.", color = Muted, fontSize = 11.sp)
            }
            "Parental Controls" -> { ParentalControlsPanel(prefs) }
            "Account" -> {
                PersistentSettingToggle(prefs, "setting_account_local_backup", "Include preferences in local backup", true)
                Text("Credentials remain encrypted in app storage. Cloud sync and account sign-in require a backend and are not available in this build.", color = Muted, fontSize = 11.sp)
            }
            "Help & About" -> {
                Text("Vision IPTV • Settings foundation", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Crash reports can be shared using Diagnostics further down this page. Update checks and full device diagnostics are still to be added.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    title: String,
    items: List<CatalogTitle>,
    selected: CatalogTitle?,
    status: String,
    onSelect: (CatalogTitle) -> Unit,
    onPlay: (CatalogTitle) -> Unit,
    onEpisodePlay: (SeriesEpisode) -> Unit = {},
    seriesDetails: SeriesDetails? = null,
    episodesLoading: Boolean = false,
    episodesError: String = "",
    isSaved: (CatalogTitle) -> Boolean = { false },
    onToggleSaved: (CatalogTitle) -> Unit = {},
    onBack: () -> Unit
) {
    if (selected != null) {
        Text("‹  BACK TO $title", color = Accent, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() }.padding(vertical = 8.dp).focusable())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            AsyncImage(model = selected.posterUrl, contentDescription = selected.title + " poster",
                modifier = Modifier.width(190.dp).height(270.dp))
            Column(Modifier.weight(1f)) {
                Text(selected.title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(selected.year.takeIf { it.isNotBlank() }, selected.genres.takeIf { it.isNotBlank() }, selected.runtime.takeIf { it.isNotBlank() }).joinToString(" • "),
                    color = Muted, fontSize = 13.sp)
                if (selected.rating != null) Text("TMDb ★ ${"%.1f".format(selected.rating)}  •  ${selected.voteCount} votes", color = Accent, fontWeight = FontWeight.Bold)
                if (selected.director.isNotBlank()) Text("Director: ${selected.director}", color = Muted, fontSize = 12.sp)
                if (selected.cast.isNotBlank()) Text("Cast: ${selected.cast}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(selected.overview.ifBlank { "No synopsis is available from your provider or metadata source." }, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { onToggleSaved(selected) }) { Text(if (isSaved(selected)) "Remove from My List" else "＋ Add to My List") }
                Spacer(Modifier.height(8.dp))
                if (selected.kind == "movie" && selected.streamUrl.isNotBlank()) {
                    Button(onClick = { onPlay(selected) }) { Text("Play movie") }
                } else if (selected.kind == "tv") {
                    Spacer(Modifier.height(10.dp))
                    Text("SEASONS & EPISODES", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    when {
                        episodesLoading -> Text("Loading episodes from your provider…", color = Muted, fontSize = 12.sp)
                        episodesError.isNotBlank() -> Text(episodesError, color = Muted, fontSize = 12.sp)
                        seriesDetails == null -> Text("Episode information is not available yet.", color = Muted, fontSize = 12.sp)
                        seriesDetails.episodes.isEmpty() -> Text("No playable episodes were returned.", color = Muted, fontSize = 12.sp)
                        else -> {
                            val seasons = seriesDetails.episodes.groupBy { it.seasonNumber }.toSortedMap()
                            seasons.forEach { (season, seasonEpisodes) ->
                                Text(if (season > 0) "Season $season" else "Specials", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                seasonEpisodes.forEach { episode ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(SurfaceDark, RoundedCornerShape(8.dp)).padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AsyncImage(model = episode.stillUrl.ifBlank { selected.posterUrl }, contentDescription = null,
                                            modifier = Modifier.width(74.dp).height(48.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Episode ${episode.episodeNumber}: ${episode.title}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            if (episode.duration.isNotBlank()) Text(episode.duration, color = Muted, fontSize = 10.sp)
                                            if (episode.plot.isNotBlank()) Text(episode.plot, color = Muted, fontSize = 10.sp, maxLines = 2)
                                        }
                                        Button(onClick = { onEpisodePlay(episode) }) { Text("Play") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (selected.tmdbId == null) Text("No confident TMDb match found. Provider information is shown instead.", color = Muted, fontSize = 11.sp)
            }
        }
    } else {
        Text(title.uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(status, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptyPanel("No $title loaded", status)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { "${it.kind}-${it.id}" }) { item ->
                    Column(Modifier.width(142.dp).clickable { onSelect(item) }.focusable()) {
                        AsyncImage(model = item.posterUrl, contentDescription = item.title,
                            modifier = Modifier.fillMaxWidth().height(205.dp))
                        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(item.year, color = Muted, fontSize = 11.sp)
                        Text(if (isSaved(item)) "★ SAVED" else "＋ ADD TO MY LIST", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onToggleSaved(item) }.padding(top = 5.dp))
                    }
                }
            }
            Text("Select a title to view its information.", color = Muted, fontSize = 11.sp)
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
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_vision_launcher),
                contentDescription = "Vision IPTV logo",
                modifier = Modifier.size(116.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VISION", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Text("IPTV", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text("YOUR ENTERTAINMENT, YOUR WAY", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun HomeDashboard(
    movies: List<CatalogTitle>,
    series: List<CatalogTitle>,
    channels: List<TvChannel>,
    programmes: List<Programme>,
    onNavigate: (String) -> Unit,
    onPlayMovie: (CatalogTitle) -> Unit,
    onPlayChannel: (TvChannel) -> Unit
) {
    val featured = movies.firstOrNull { it.backdropUrl.isNotBlank() } ?: movies.firstOrNull()
    val trendingMovies = movies.sortedByDescending { it.rating ?: 0.0 }.take(12)
    val recentMovies = movies.take(12)
    val recentSeries = series.take(12)
    val now = System.currentTimeMillis()
    val onNow = programmes.count { it.startMillis <= now && it.stopMillis > now }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("WELCOME TO VISION", color = Accent, fontSize = 11.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
            Text("Your entertainment, your way", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("${movies.size} movies  •  ${series.size} series  •  ${channels.size} live channels",
                color = Muted, fontSize = 11.sp)
        }
        Surface(color = Color(0xFF102B25), shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F765F))) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Color(0xFF38D889), RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Text("LIVE NOW: $onNow", color = Color(0xFFB8F4D5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (featured != null) {
        Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(12.dp))) {
            if (featured.backdropUrl.isNotBlank()) {
                AsyncImage(model = featured.backdropUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else if (featured.posterUrl.isNotBlank()) {
                AsyncImage(model = featured.posterUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE050A12), Color(0x99050A12), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f).padding(18.dp)) {
                Text("FEATURED FROM YOUR LIBRARY", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text(featured.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2)
                Text(listOf(featured.year, featured.category).filter { it.isNotBlank() }.joinToString("  •  "),
                    color = Muted, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(9.dp))
                Button(onClick = { onPlayMovie(featured) }) { Text("WATCH NOW") }
            }
        }
    } else {
        HeroBanner("Your library starts here", onClick = { onNavigate("Movies") })
    }
    Spacer(Modifier.height(17.dp))
    if (trendingMovies.isNotEmpty()) {
        SectionHeading("TOP MOVIES")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(trendingMovies, key = { "home-movie-${it.id}" }) { item ->
                HomeMediaCard(item.title, item.posterUrl, item.year, item.rating?.let { "★ %.1f".format(it) }.orEmpty(),
                    onClick = { onPlayMovie(item) })
            }
        }
        Spacer(Modifier.height(15.dp))
    }
    if (recentSeries.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("SERIES TO EXPLORE")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onNavigate("Series") }) { Text("SEE ALL") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recentSeries, key = { "home-series-${it.id}" }) { item ->
                HomeMediaCard(item.title, item.posterUrl, item.year, item.category,
                    onClick = { onNavigate("Series") })
            }
        }
        Spacer(Modifier.height(15.dp))
    }
    if (recentMovies.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("RECENTLY ADDED MOVIES")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onNavigate("Movies") }) { Text("SEE ALL") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recentMovies, key = { "home-recent-${it.id}" }) { item ->
                HomeMediaCard(item.title, item.posterUrl, item.year, item.category,
                    onClick = { onPlayMovie(item) })
            }
        }
        Spacer(Modifier.height(15.dp))
    }
    if (channels.isNotEmpty()) {
        SectionHeading("LIVE TV")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(channels.take(10), key = { "home-channel-${it.id}" }) { channel ->
                Column(Modifier.width(145.dp).background(SurfaceDark, RoundedCornerShape(9.dp))
                    .clickable { onPlayChannel(channel) }.focusable().padding(10.dp)) {
                    if (channel.logo.isNotBlank()) AsyncImage(model = channel.logo, contentDescription = null,
                        modifier = Modifier.size(42.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
                    Text(channel.group, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(15.dp))
    }
    SectionHeading("EXPLORE VISION")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("Live TV", "Movies", "Series", "TV Guide", "Catch-up", "My List")) { name ->
            FocusTile(name) { onNavigate(name) }
        }
    }
    if (movies.isEmpty() && series.isEmpty() && channels.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        EmptyPanel("Connect your TV service", "Add an authorised M3U or Xtream account in Settings. Your provider's real catalogue and channels will appear here.")
        Button(onClick = { onNavigate("Settings") }) { Text("OPEN SETTINGS") }
    }
}

@Composable
private fun HomeMediaCard(title: String, poster: String, subtitle: String, meta: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(132.dp).onFocusChanged { focused = it.isFocused }.focusable()
        .clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().height(154.dp).background(SurfaceDark, RoundedCornerShape(8.dp))
            .then(if (focused) Modifier.border(3.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)) {
            if (poster.isNotBlank()) {
                AsyncImage(model = poster, contentDescription = title, modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF344B5D), Color(0xFF17222F)))),
                    contentAlignment = Alignment.Center) {
                    Text("VISION", color = Muted, fontWeight = FontWeight.Black)
                }
            }
        }
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, modifier = Modifier.padding(top = 5.dp))
        Text(listOf(subtitle, meta).filter { it.isNotBlank() }.joinToString(" • "),
            color = Muted, fontSize = 9.sp, maxLines = 1)
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
private fun HeroBanner(title: String = "Featured for you", onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(174.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable { onClick() }.focusable() else Modifier)
            .background(Brush.horizontalGradient(listOf(Color(0xFF102331), Color(0xFF30424F), Color(0xFF111923))), RoundedCornerShape(11.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) Accent else Color(0xFF33495A), RoundedCornerShape(11.dp))
            .padding(19.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f)) {
            Text("VISION ORIGINAL", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Discover movies and series from your connected provider.", color = Color(0xFFE1E7EC), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(if (onClick != null) "SELECT TO EXPLORE MOVIES" else "EXPLORE NOW", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    var selectedDay by remember { mutableIntStateOf(0) }
    var selectedChannelId by remember { mutableStateOf("") }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    var query by remember { mutableStateOf("") }
    val zone = remember { java.util.TimeZone.getDefault() }
    val dayOffset = if (selectedDay == 7) 0 else selectedDay
    val rangeDays = if (selectedDay == 7) 7 else 1
    val calendar = remember(dayOffset) {
        java.util.Calendar.getInstance(zone).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        }
    }
    val dayStart = calendar.timeInMillis
    val dayEnd = dayStart + rangeDays * 24L * 60L * 60L * 1000L
    val dayLabel = if (selectedDay == 7) "Next 7 days" else java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(dayStart))
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).apply { timeZone = zone } }
    fun programmesFor(channel: TvChannel): List<Programme> {
        val ids = listOf(channel.tvgId, channel.id, channel.name).filter { it.isNotBlank() }
        return programmes.filter { programme ->
            ids.any { id -> programme.channelId.equals(id, ignoreCase = true) } &&
                programme.stopMillis > dayStart && programme.startMillis < dayEnd
        }.distinctBy { "${it.channelId}|${it.startMillis}|${it.title}" }
            .sortedBy { it.startMillis }
    }
    val visibleChannels = channels.filter { channel ->
        (selectedChannelId.isBlank() || channel.id == selectedChannelId) &&
            (query.isBlank() || channel.name.contains(query, true) || channel.group.contains(query, true))
    }
    val dayProgrammeCount = programmes.count { it.stopMillis > dayStart && it.startMillis < dayEnd }
    val todayNow = System.currentTimeMillis()
    Text("TV GUIDE", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Programme schedules • device local time (${zone.id})", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Today" to 0, "Tomorrow" to 1, "7 Days" to 7).forEach { (label, offset) ->
            FilterChip(
                selected = selectedDay == offset,
                onClick = { selectedDay = offset; selectedProgramme = null },
                label = { Text(label) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(dayLabel, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        Text("$dayProgrammeCount programmes", color = Muted, fontSize = 11.sp)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true, label = { Text("Find a channel") },
        placeholder = { Text("Search channel name or category") }
    )
    Spacer(Modifier.height(8.dp))
    if (programmes.isEmpty()) {
        EmptyPanel("No EPG data loaded", "Connect your playlist, then add or test an XMLTV source in Settings. The guide will show schedules supplied by that feed.")
    } else if (channels.isEmpty()) {
        EmptyPanel("No channels available", "Load a playlist to match its channels with the programme guide.")
    } else if (visibleChannels.isEmpty()) {
        EmptyPanel("No matching channels", "Try another channel name or clear the search.")
    } else {
        Text("Showing ${visibleChannels.size} channels with schedule data where available", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        visibleChannels.take(100).forEach { channel ->
            val schedule = programmesFor(channel)
            val current = schedule.firstOrNull { it.startMillis <= todayNow && it.stopMillis > todayNow }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF263B4C), RoundedCornerShape(8.dp))
                    .padding(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(model = channel.logo, contentDescription = "${channel.name} logo",
                            modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(channel.group, color = Muted, fontSize = 10.sp, maxLines = 1)
                    }
                    if (current != null) {
                        Text("ON NOW", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (schedule.isEmpty()) {
                    Text("No programme data for this date", color = Muted, fontSize = 11.sp)
                } else {
                    schedule.take(8).forEach { programme ->
                        val isCurrent = programme.startMillis <= todayNow && programme.stopMillis > todayNow
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .then(if (selectedProgramme == programme) Modifier.border(1.dp, Accent, RoundedCornerShape(5.dp)) else Modifier)
                                .clickable { selectedProgramme = if (selectedProgramme == programme) null else programme }
                                .focusable().padding(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(timeFormat.format(java.util.Date(programme.startMillis)),
                                color = if (isCurrent) Accent else Muted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                            Column(Modifier.weight(1f)) {
                                Text(programme.title, color = if (isCurrent) Accent else Color.White,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text("${timeFormat.format(java.util.Date(programme.startMillis))} – ${timeFormat.format(java.util.Date(programme.stopMillis))}",
                                    color = Muted, fontSize = 9.sp)
                                if (selectedProgramme == programme && programme.description.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(programme.description, color = Color(0xFFD0DCE6), fontSize = 10.sp)
                                }
                            }
                            if (selectedProgramme == programme) Text("DETAILS", color = Accent, fontSize = 9.sp)
                        }
                    }
                    if (schedule.size > 8) Text("+ ${schedule.size - 8} more programmes in this day", color = Muted, fontSize = 10.sp)
                }
            }
        }
        if (visibleChannels.size > 100) Text("Showing first 100 channels. Search to narrow the list.", color = Muted, fontSize = 10.sp)
    }
}
@Composable
private fun CatchupScreen(
    channels: List<TvChannel>,
    programmes: List<Programme>,
    onPlayArchive: (TvChannel, Programme) -> Unit
) {
    var selectedChannel by remember { mutableStateOf("All channels") }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    val channelByProgrammeId = remember(channels) {
        programmesChannelMap(channels)
    }
    val availableChannels = channels.filter { it.archiveTemplate.isNotBlank() }
    val now = System.currentTimeMillis()
    val replayItems = programmes.mapNotNull { programme ->
        val channel = channelByProgrammeId[programme.channelId] ?: return@mapNotNull null
        if (programme.stopMillis > now || programme.stopMillis <= programme.startMillis) return@mapNotNull null
        if (channel.archiveDays > 0 && programme.startMillis < now - channel.archiveDays * 86_400_000L) return@mapNotNull null
        channel to programme
    }.filter { selectedChannel == "All channels" || it.first.id == selectedChannel }
        .sortedByDescending { it.second.startMillis }
    Text("CATCH-UP TV", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text("Replay programmes listed in your EPG when your provider exposes archive playback.", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Text("${availableChannels.size} archive-enabled channels • ${replayItems.size} replayable programmes in your guide",
        color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    if (channels.isEmpty()) {
        EmptyPanel("Connect your TV service", "Add an M3U or Xtream account in Settings to check archive support.")
    } else if (programmes.isEmpty()) {
        EmptyPanel("No programme guide loaded", "Catch-up needs EPG programme times. Load your provider guide or an XMLTV source in Settings.")
    } else if (availableChannels.isEmpty()) {
        EmptyPanel("No archive support detected", "Your current playlist has not declared a catch-up source and your Xtream service has not marked any channels as archive-enabled.")
    } else {
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                FilterChip(selected = selectedChannel == "All channels", onClick = { selectedChannel = "All channels" },
                    label = { Text("All channels") })
            }
            items(availableChannels) { channel ->
                FilterChip(selected = selectedChannel == channel.id, onClick = { selectedChannel = channel.id },
                    label = { Text(channel.name, maxLines = 1) })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (replayItems.isEmpty()) {
            EmptyPanel("No replay programmes available", "The loaded guide has no past programmes within the archive window declared by your provider.")
        } else {
            replayItems.take(150).forEach { (channel, programme) ->
                val expanded = selectedProgramme == programme
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(SurfaceDark, RoundedCornerShape(9.dp))
                        .border(if (expanded) 1.dp else 0.dp, if (expanded) Accent else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable { selectedProgramme = if (expanded) null else programme }
                        .focusable().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (channel.logo.isNotBlank()) AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(42.dp))
                    Column(Modifier.weight(1f)) {
                        Text(programme.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                        Text(channel.name + " • " + java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(programme.startMillis)),
                            color = Muted, fontSize = 10.sp)
                        Text("Duration: " + ((programme.stopMillis - programme.startMillis) / 60_000L).coerceAtLeast(1L) + " min",
                            color = Muted, fontSize = 10.sp)
                        if (expanded && programme.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(programme.description, color = Color(0xFFD0DCE6), fontSize = 11.sp)
                        }
                    }
                    Button(onClick = { onPlayArchive(channel, programme) }) { Text("Replay") }
                }
            }
        }
    }
}

private fun programmesChannelMap(channels: List<TvChannel>): Map<String, TvChannel> {
    val result = mutableMapOf<String, TvChannel>()
    channels.forEach { channel ->
        result[channel.id] = channel
        if (channel.tvgId.isNotBlank()) result[channel.tvgId] = channel
        result[channel.name] = channel
    }
    return result
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
