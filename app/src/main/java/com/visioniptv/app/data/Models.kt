package com.visioniptv.app.data

data class ServerSet(
    val primary: String,
    val backup1: String = "",
    val backup2: String = "",
    val backup3: String = "",
    val backup4: String = ""
) {
    fun candidates(): List<String> = listOf(primary, backup1, backup2, backup3, backup4)
        .map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
}

data class PlaylistAccount(
    val name: String,
    val serverSet: ServerSet,
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val backupM3uUrls: List<String> = emptyList(),
    val epgUrls: List<String> = emptyList()
)

data class TvChannel(
    val id: String, val name: String, val streamUrl: String, val logo: String = "",
    val group: String = "All channels", val number: Int = 0, val tvgId: String = "",
    val archiveTemplate: String = "", val archiveDays: Int = 0
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

data class EpgChannelInfo(
    val id: String,
    val displayName: String,
    val iconUrl: String = ""
)

data class EpgData(
    val programmes: List<Programme>,
    val channels: List<EpgChannelInfo>
)
