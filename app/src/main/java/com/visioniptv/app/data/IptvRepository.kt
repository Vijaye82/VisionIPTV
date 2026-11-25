package com.visioniptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.TimeUnit

class IptvRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun apiUrl(base: String, account: PlaylistAccount, action: String? = null): String {
        val builder = "$base/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
        if (action != null) builder.addQueryParameter("action", action)
        return builder.build().toString()
    }

    suspend fun loadM3u(account: PlaylistAccount): Pair<List<TvChannel>, String> {
        val urls = (listOf(account.m3uUrl) + account.backupM3uUrls)
            .filter { it.isNotBlank() } +
            account.serverSet.candidates().map { base ->
                "$base/get.php?username=${java.net.URLEncoder.encode(account.username, "UTF-8")}" +
                    "&password=${java.net.URLEncoder.encode(account.password, "UTF-8")}&type=m3u_plus&output=ts"
            }
        var lastError: Exception? = null
        for (url in urls.distinct()) {
            try {
                val parsed = PlaylistParser.parseM3u(get(url))
                if (parsed.isEmpty()) throw IOException("Playlist returned no channels")
                return parsed to url
            } catch (e: Exception) { lastError = e }
        }
        throw IOException("Could not load M3U from the primary or backup URLs", lastError)
    }

    suspend fun loadXtreamLiveWithFailover(account: PlaylistAccount): Pair<List<TvChannel>, String> {
        var lastError: Exception? = null
        for (base in account.serverSet.candidates()) {
            try {
                val channels = loadXtreamLive(account, base)
                if (channels.isEmpty()) throw IOException("Server returned no live channels")
                return channels to base
            } catch (e: Exception) { lastError = e }
        }
        throw IOException("Xtream login failed on primary and backup servers", lastError)
    }

    suspend fun loadXtreamLive(account: PlaylistAccount, activeBase: String): List<TvChannel> {
        val json = JSONArray(get(apiUrl(activeBase, account, "get_live_streams")))
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val id = item.optString("stream_id")
                add(TvChannel(id, item.optString("name", "Channel"),
                    "$activeBase/live/${account.username}/${account.password}/$id.ts",
                    item.optString("stream_icon"), item.optString("category_name", "Live TV"),
                    i + 1, item.optString("epg_channel_id"),
                    if (item.optInt("tv_archive", 0) == 1) "$activeBase/timeshift/${account.username}/${account.password}/{duration}/{start}/$id.ts" else "",
                    item.optInt("tv_archive_duration", 0)))
            }
        }
    }

    suspend fun loadXtreamMovies(account: PlaylistAccount, activeBase: String): List<MediaItem> {
        val json = JSONArray(get(apiUrl(activeBase, account, "get_vod_streams")))
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val id = item.optString("stream_id")
                val ext = item.optString("container_extension", "mp4").ifBlank { "mp4" }
                add(MediaItem(id, item.optString("name", "Movie"),
                    "$activeBase/movie/${account.username}/${account.password}/$id.$ext",
                    item.optString("stream_icon"), item.optString("backdrop_path"),
                    item.optString("category_name"), item.optString("year"), item.optString("plot"), "movie"))
            }
        }
    }
    private fun cacheFile(cacheDir: File, sourceUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.xml.gz")
    }

    private suspend fun readCachedXml(cacheDir: File?, sourceUrl: String): String? =
        withContext(Dispatchers.IO) {
            if (cacheDir == null) return@withContext null
            val file = cacheFile(cacheDir, sourceUrl)
            if (!file.isFile) return@withContext null
            try {
                GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) { null }
        }

    private suspend fun writeCachedXml(cacheDir: File?, sourceUrl: String, xml: String) =
        withContext(Dispatchers.IO) {
            if (cacheDir == null || xml.length > 100_000_000) return@withContext
            try {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val target = cacheFile(cacheDir, sourceUrl)
                val temp = File(cacheDir, target.name + ".tmp")
                GZIPOutputStream(temp.outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use {
                    it.write(xml)
                }
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
            } catch (_: Exception) {
                // Cache failure must never interrupt EPG or live TV.
            }
        }

    /**
     * Tries XMLTV sources in priority order. Every successful feed is cached locally
     * (compressed); if a feed is offline, its last successful copy is parsed instead.
     */
    suspend fun loadEpgFallback(
        sourceUrls: List<String>,
        inputChannels: List<TvChannel>,
        cacheDir: File? = null
    ): Pair<List<TvChannel>, List<Programme>> {
        val mergedChannels = inputChannels.toMutableList()
        val programmes = mutableListOf<Programme>()
        val seenProgrammeKeys = mutableSetOf<String>()

        for (sourceUrl in sourceUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()) {
            val xml = try {
                get(sourceUrl).also { onlineXml ->
                    val parsed = EpgXmltvParser.parseData(onlineXml)
                    if (parsed.channels.isNotEmpty() || parsed.programmes.isNotEmpty()) {
                        writeCachedXml(cacheDir, sourceUrl, onlineXml)
                    }
                }
            } catch (_: Exception) {
                readCachedXml(cacheDir, sourceUrl)
            } ?: continue

            try {
                val data = EpgXmltvParser.parseData(xml)
                val byId = data.channels.associateBy { it.id }
                val byName = data.channels.associateBy { it.displayName.trim().lowercase() }

                for (index in mergedChannels.indices) {
                    val channel = mergedChannels[index]
                    val matched = if (channel.tvgId.isNotBlank()) byId[channel.tvgId] else null
                    val nameMatched = matched ?: byName[channel.name.trim().lowercase()]
                    if (channel.logo.isBlank() && !nameMatched?.iconUrl.isNullOrBlank()) {
                        mergedChannels[index] = channel.copy(logo = nameMatched!!.iconUrl)
                    }
                }

                val programmeChannelMap = mutableMapOf<String, String>()
                data.channels.forEach { epgChannel ->
                    val matchingAppChannel = inputChannels.firstOrNull { channel ->
                        (channel.tvgId.isNotBlank() && channel.tvgId == epgChannel.id) ||
                            channel.name.equals(epgChannel.displayName, ignoreCase = true)
                    }
                    if (matchingAppChannel != null) {
                        programmeChannelMap[epgChannel.id] =
                            matchingAppChannel.tvgId.ifBlank { matchingAppChannel.name }
                    }
                }
                data.programmes.forEach { programme ->
                    val mappedId = programmeChannelMap[programme.channelId] ?: programme.channelId
                    val mappedProgramme = programme.copy(channelId = mappedId)
                    val key = "${mappedProgramme.channelId}|${mappedProgramme.startMillis}|${mappedProgramme.title}"
                    if (seenProgrammeKeys.add(key)) programmes += mappedProgramme
                }
            } catch (_: Exception) {
                // Ignore malformed feed/cache and continue to the next source.
            }
        }
        return mergedChannels to programmes
    }

}
