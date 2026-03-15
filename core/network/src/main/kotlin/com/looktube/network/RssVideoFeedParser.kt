package com.looktube.network

import com.looktube.model.VideoSummary
import com.looktube.model.toHeuristicShowTitleOrNull
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

class RssVideoFeedParser {
    fun parse(xml: String): List<VideoSummary> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val documentBuilder = factory.newDocumentBuilder()
        val document = documentBuilder.parse(InputSource(StringReader(xml)))
        val nodes = document.getElementsByTagName("item")
        val channelTitle = document.getElementsByTagName("channel")
            .item(0)
            ?.childNodes
            ?.let { childNodes ->
                (0 until childNodes.length)
                    .mapNotNull(childNodes::item)
                    .firstOrNull { it.nodeName == "title" }
                    ?.textContent
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            }

        return buildList {
            for (index in 0 until nodes.length) {
                val item = nodes.item(index) as? Element ?: continue
                val title = item.readText("title") ?: "Untitled"
                val description = item.readText("description") ?: ""
                val feedCategory = item.readText("category") ?: "Uncategorized"
                add(
                    VideoSummary(
                        id = item.readText("guid") ?: "item-$index",
                        title = title,
                        description = description,
                        isPremium = feedCategory.contains("Premium", ignoreCase = true) ||
                            channelTitle?.contains("Premium", ignoreCase = true) == true,
                        feedCategory = feedCategory,
                        playbackUrl = item.readAttribute("media:content", "url")
                            ?: item.readAttribute("enclosure", "url")
                            ?: item.readText("link"),
                        seriesTitle = feedCategory.takeUnless(String::isGenericFeedCategory)
                            ?.toHeuristicShowTitleOrNull()
                            ?: title.inferSeriesTitle(),
                        thumbnailUrl = item.readAttribute("media:thumbnail", "url")
                            ?: description.extractFirstImageUrl(),
                        publishedAtEpochMillis = item.readText("pubDate")
                            ?.toEpochMillisOrNull()
                            ?: item.readText("dc:date")?.toEpochMillisOrNull(),
                        durationSeconds = item.readAttribute("media:content", "duration")
                            ?.toLongOrNull()
                            ?: item.readText("itunes:duration")?.toDurationSecondsOrNull(),
                    ),
                )
            }
        }
    }
}

private fun Element.readText(tagName: String): String? =
    getElementsByTagName(tagName)
        .item(0)
        ?.textContent
        ?.trim()
        ?.takeIf(String::isNotEmpty)

private fun Element.readAttribute(tagName: String, attributeName: String): String? {
    val node = getElementsByTagName(tagName).item(0) as? Element ?: return null
    return node.getAttribute(attributeName)
        .trim()
        .takeIf(String::isNotEmpty)
}

private fun String.isGenericFeedCategory(): Boolean =
    equals("Premium", ignoreCase = true) ||
        equals("Latest Premium", ignoreCase = true) ||
        equals("Video", ignoreCase = true) ||
        equals("Videos", ignoreCase = true) ||
        equals("Uncategorized", ignoreCase = true)

private fun String.inferSeriesTitle(): String? {
    val separators = listOf(": ", " - ", " — ", " #")
    separators.forEach { separator ->
        val prefix = substringBefore(separator, missingDelimiterValue = "")
            .trim()
            .toHeuristicShowTitleOrNull()
            ?.takeIf { it.length <= 40 }
        if (prefix != null) {
            return prefix
        }
    }
    val episodeIndex = indexOf(" episode ", ignoreCase = true)
    if (episodeIndex > 0) {
        return substring(0, episodeIndex).trim().toHeuristicShowTitleOrNull()
    }
    return toHeuristicShowTitleOrNull()
}

private fun String.extractFirstImageUrl(): String? =
    IMAGE_URL_REGEX.find(this)?.groupValues?.getOrNull(1)

private val IMAGE_URL_REGEX = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

private fun String.toEpochMillisOrNull(): Long? =
    runCatching {
        ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private fun String.toDurationSecondsOrNull(): Long? {
    val trimmed = trim()
    if (trimmed.isBlank()) {
        return null
    }
    if (":" !in trimmed) {
        return trimmed.toLongOrNull()
    }
    val parts = trimmed.split(":").mapNotNull(String::toLongOrNull)
    if (parts.isEmpty()) {
        return null
    }
    return parts.fold(0L) { total, part -> total * 60 + part }
}
