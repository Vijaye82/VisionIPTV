package com.visioniptv.app.data

data class ServerSet(val primary: String, val backup1: String = "", val backup2: String = "") {
    fun candidates(): List<String> = listOf(primary, backup1, backup2)
        .map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
}
data class PlaylistAccount(
    val name: String, val serverSet: ServerSet, val username: String = "",
    val password: String = "", val m3uUrl: String = "", val epgUrls: List<String> = emptyList()
)
data class TvChannel(
    val id: String, val name: String, val streamUrl: String, val logo: String = "",
    val group: String = "All channels", val number: Int = 0, val tvgId: String = ""
)
data class MediaItem(
    val id: String, val title: String, val streamUrl: String, val poster: String = "",
    val backdrop: String = "", val category: String = "", val year: String = "",
    val description: String = "", val kind: String = "movie"
)
data class Programme(
    val channelId: String, val title: String, val startMillis: Long,
    val stopMillis: Long, val description: String = ""
)
