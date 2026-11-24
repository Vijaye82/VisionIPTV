package com.visioniptv.app.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EpgXmltvParser {
    private fun parseDate(value: String): Long? = try {
        val clean = value.trim()
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = true
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(clean)?.time
    } catch (_: Exception) { null }

    fun parse(xml: String): List<Programme> = parseData(xml).programmes

    fun parseData(xml: String): EpgData {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val programmes = mutableListOf<Programme>()
        val channels = mutableListOf<EpgChannelInfo>()
        var event = parser.eventType
        var currentChannelId = ""
        var displayName = ""
        var iconUrl = ""
        var inDisplayName = false
        var inProgramme = false
        var programmeChannel = ""
        var title = ""
        var desc = ""
        var start = 0L
        var stop = 0L

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> {
                        currentChannelId = parser.getAttributeValue(null, "id").orEmpty()
                        displayName = ""
                        iconUrl = ""
                    }
                    "display-name" -> inDisplayName = true
                    "icon" -> {
                        val src = parser.getAttributeValue(null, "src").orEmpty()
                        if (src.isNotBlank() && iconUrl.isBlank()) iconUrl = src
                    }
                    "programme" -> {
                        inProgramme = true
                        programmeChannel = parser.getAttributeValue(null, "channel").orEmpty()
                        start = parseDate(parser.getAttributeValue(null, "start").orEmpty()) ?: 0L
                        stop = parseDate(parser.getAttributeValue(null, "stop").orEmpty()) ?: 0L
                        title = ""
                        desc = ""
                    }
                    "title" -> if (inProgramme) title = parser.nextText()
                    "desc" -> if (inProgramme) desc = parser.nextText()
                }
                XmlPullParser.TEXT -> if (inDisplayName && displayName.isBlank()) {
                    displayName = parser.text?.trim().orEmpty()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "display-name" -> inDisplayName = false
                    "channel" -> {
                        if (currentChannelId.isNotBlank()) {
                            channels += EpgChannelInfo(currentChannelId, displayName.ifBlank { currentChannelId }, iconUrl)
                        }
                        currentChannelId = ""
                    }
                    "programme" -> {
                        if (inProgramme && programmeChannel.isNotBlank() && title.isNotBlank()) {
                            programmes += Programme(programmeChannel, title, start, stop, desc)
                        }
                        inProgramme = false
                    }
                }
            }
            event = parser.next()
        }
        return EpgData(programmes, channels)
    }

    fun matchChannel(channel: TvChannel, xmltvIds: Set<String>): String? {
        if (channel.tvgId.isNotBlank() && channel.tvgId in xmltvIds) return channel.tvgId
        return xmltvIds.firstOrNull { it.equals(channel.name, ignoreCase = true) }
    }
}
