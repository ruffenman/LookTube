package com.looktube.network

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class HttpRssVideoFeedService(
    private val parser: RssVideoFeedParser,
    private val connectionFactory: UrlConnectionFactory = DefaultUrlConnectionFactory(),
) : VideoFeedService {
    override fun loadVideos(request: VideoFeedRequest): List<com.looktube.model.VideoSummary> {
        val connection = connectionFactory.open(request.feedUrl)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            connection.setRequestProperty("User-Agent", "LookTube/0.2 feed sync")
            if (request.username.isNotBlank() && request.password.isNotBlank()) {
                val encodedCredentials = Base64.getEncoder().encodeToString(
                    "${request.username}:${request.password}".toByteArray(),
                )
                connection.setRequestProperty("Authorization", "Basic $encodedCredentials")
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                val errorPreview = connection.errorStream?.bufferedReader()?.use { it.readText().take(250) }.orEmpty()
                throw FeedSyncException(
                    "Feed request failed with HTTP $statusCode.${if (errorPreview.isBlank()) "" else " $errorPreview"}".trim(),
                )
            }

            val body = stream.bufferedReader().use { it.readText() }
            parser.parse(body)
        } catch (exception: FeedSyncException) {
            throw exception
        } catch (exception: Exception) {
            throw FeedSyncException(exception.message ?: "Unable to load the configured feed.", exception)
        } finally {
            connection.disconnect()
        }
    }
}

fun interface UrlConnectionFactory {
    fun open(url: String): HttpURLConnection
}

class DefaultUrlConnectionFactory : UrlConnectionFactory {
    override fun open(url: String): HttpURLConnection =
        URL(url).openConnection() as HttpURLConnection
}

class FeedSyncException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
