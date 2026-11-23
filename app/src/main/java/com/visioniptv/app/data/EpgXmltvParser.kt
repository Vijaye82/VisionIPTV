package com.visioniptv.app.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EpgXmltvParser {
    private fun parseDate(value: String): Long? = try {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = true
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(value)?.time
    } catch (_: Exception) { null }

    fun parse(xml: String): List<Programme> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<Programme>()
        var event = parser.eventType
        var channel = ""; var title = ""; var desc = ""; var start = 0L; var stop = 0L
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel").orEmpty()
                        start = parseDate(parser.getAttributeValue(null, "start").orEmpty()) ?: 0L
                        stop = parseDate(parser.getAttributeValue(null, "stop").orEmpty()) ?: 0L
                        title = ""; desc = ""
                    }
                    "title" -> title = parser.nextText()
                    "desc" -> desc = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme" && channel.isNotBlank() && title.isNotBlank()) {
                    out += Programme(channel, title, start, stop, desc)
                }
            }
            event = parser.next()
        }
        return out
    }

    fun matchChannel(channel: TvChannel, xmltvIds: Set<String>): String? {
        if (channel.tvgId.isNotBlank() && channel.tvgId in xmltvIds) return channel.tvgId
        return xmltvIds.firstOrNull { it.equals(channel.name, ignoreCase = true) }
    }
}
