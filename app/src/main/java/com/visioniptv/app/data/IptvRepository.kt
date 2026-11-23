package com.visioniptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class IptvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun loadM3u(account: PlaylistAccount): Pair<List<TvChannel>, String> {
        val urls = listOf(account.m3uUrl).filter { it.isNotBlank() } +
            account.serverSet.candidates().map {
                "$it/get.php?username=${account.username}&password=${account.password}&type=m3u_plus&output=ts"
            }
        var lastError: Exception? = null
        for (url in urls.distinct()) {
            try { return PlaylistParser.parseM3u(get(url)) to url }
            catch (e: Exception) { lastError = e }
        }
        throw IOException("Playlist unavailable on primary and backups", lastError)
    }

    suspend fun loadXtreamLive(account: PlaylistAccount, activeBase: String): List<TvChannel> {
        val json = JSONArray(get("$activeBase/player_api.php?username=${account.username}&password=${account.password}&action=get_live_streams"))
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val id = item.optString("stream_id")
                add(TvChannel(id, item.optString("name", "Channel"),
                    "$activeBase/live/${account.username}/${account.password}/$id.ts",
                    item.optString("stream_icon"), item.optString("category_name", "Live TV"),
                    i + 1, item.optString("epg_channel_id")))
            }
        }
    }

    suspend fun loadXtreamMovies(account: PlaylistAccount, activeBase: String): List<MediaItem> {
        val json = JSONArray(get("$activeBase/player_api.php?username=${account.username}&password=${account.password}&action=get_vod_streams"))
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val id = item.optString("stream_id")
                val ext = item.optString("container_extension", "mp4").ifBlank { "mp4" }
                add(MediaItem(id, item.optString("name", "Movie"),
                    "$activeBase/movie/${account.username}/${account.password}/$id.$ext",
                    item.optString("stream_icon"), item.optString("backdrop_path"),
                    item.optString("category_name"), item.optString("year"),
                    item.optString("plot"), "movie"))
            }
        }
    }

    suspend fun probeServers(servers: ServerSet, username: String, password: String): String {
        var last: Exception? = null
        for (base in servers.candidates()) {
            try {
                get("$base/player_api.php?username=$username&password=$password")
                return base
            } catch (e: Exception) { last = e }
        }
        throw IOException("No IPTV server responded", last)
    }
}
