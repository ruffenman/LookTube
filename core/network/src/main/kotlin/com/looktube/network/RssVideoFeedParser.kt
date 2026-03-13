package com.looktube.network

import com.looktube.model.VideoSummary
import java.io.StringReader
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

        return buildList {
            for (index in 0 until nodes.length) {
                val item = nodes.item(index) as? Element ?: continue
                add(
                    VideoSummary(
                        id = item.readText("guid") ?: "item-$index",
                        title = item.readText("title") ?: "Untitled",
                        description = item.readText("description") ?: "",
                        isPremium = item.readText("category")?.contains("Premium", ignoreCase = true) == true,
                        feedCategory = item.readText("category") ?: "Uncategorized",
                        playbackUrl = item.readAttribute("media:content", "url")
                            ?: item.readAttribute("enclosure", "url")
                            ?: item.readText("link"),
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
