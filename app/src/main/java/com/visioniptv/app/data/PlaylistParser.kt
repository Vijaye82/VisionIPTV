package com.visioniptv.app.data

object PlaylistParser {
    fun parseM3u(text: String): List<TvChannel> {
        val result = mutableListOf<TvChannel>()
        var pending: Map<String, String> = emptyMap()
        var title = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = Regex("([\\w-]+)=\"([^\"]*)\"")
                        .findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    title = line.substringAfterLast(",", "Channel").trim().ifBlank { "Channel" }
                    pending = attrs
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    result += TvChannel(
                        id = pending["tvg-id"].orEmpty().ifBlank { title },
                        name = title.ifBlank { "Channel ${result.size + 1}" },
                        streamUrl = line, logo = pending["tvg-logo"].orEmpty(),
                        group = pending["group-title"].orEmpty().ifBlank { "All channels" },
                        number = result.size + 1, tvgId = pending["tvg-id"].orEmpty(),
                        archiveTemplate = pending["catchup-source"].orEmpty().ifBlank { pending["catchup"].orEmpty() },
                        archiveDays = pending["catchup-days"]?.toIntOrNull() ?: 0
                    )
                    pending = emptyMap()
                    title = ""
                }
            }
        }
        return result
    }
}
