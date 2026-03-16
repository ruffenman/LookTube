package com.looktube.network

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRssVideoFeedServiceTest {
    private val parser = RssVideoFeedParser()

    @Test
    fun loadVideosAddsExpectedHeadersForCredentialedRequests() {
        val connection = FakeHttpURLConnection(
            responseCodeValue = 200,
            inputBody = validFeedBody,
        )
        val service = HttpRssVideoFeedService(
            parser = parser,
            connectionFactory = UrlConnectionFactory { connection },
        )

        val videos = service.loadVideos(
            VideoFeedRequest(
                feedUrl = "https://example.com/premium.xml",
                username = "jorge",
                password = "secret",
            ),
        )

        assertEquals(1, videos.size)
        assertEquals("GET", connection.requestMethod)
        assertEquals(10_000, connection.connectTimeout)
        assertEquals(10_000, connection.readTimeout)
        assertTrue(connection.instanceFollowRedirects)
        assertEquals("application/rss+xml, application/xml, text/xml", connection.recordedRequestProperties["Accept"])
        assertEquals("LookTube/0.2 feed sync", connection.recordedRequestProperties["User-Agent"])
        assertEquals("Basic am9yZ2U6c2VjcmV0", connection.recordedRequestProperties["Authorization"])
        assertTrue(connection.disconnectCalled)
    }

    @Test
    fun loadVideosSkipsAuthorizationHeaderWhenCredentialsAreBlank() {
        val connection = FakeHttpURLConnection(
            responseCodeValue = 200,
            inputBody = validFeedBody,
        )
        val service = HttpRssVideoFeedService(
            parser = parser,
            connectionFactory = UrlConnectionFactory { connection },
        )

        service.loadVideos(
            VideoFeedRequest(
                feedUrl = "https://example.com/premium.xml",
                username = "",
                password = "",
            ),
        )

        assertFalse(connection.recordedRequestProperties.containsKey("Authorization"))
        assertTrue(connection.disconnectCalled)
    }

    @Test
    fun loadVideosSurfacesHttpErrorsWithPreviewAndDisconnects() {
        val connection = FakeHttpURLConnection(
            responseCodeValue = 401,
            errorBody = "Unauthorized premium feed",
        )
        val service = HttpRssVideoFeedService(
            parser = parser,
            connectionFactory = UrlConnectionFactory { connection },
        )

        val exception = runCatching {
            service.loadVideos(
                VideoFeedRequest(
                    feedUrl = "https://example.com/premium.xml",
                    username = "",
                    password = "",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is FeedSyncException)
        assertTrue(exception?.message.orEmpty().contains("HTTP 401"))
        assertTrue(exception?.message.orEmpty().contains("Unauthorized premium feed"))
        assertTrue(connection.disconnectCalled)
    }

    @Test
    fun loadVideosWrapsUnexpectedParserFailures() {
        val connection = FakeHttpURLConnection(
            responseCodeValue = 200,
            inputBody = "<rss",
        )
        val service = HttpRssVideoFeedService(
            parser = parser,
            connectionFactory = UrlConnectionFactory { connection },
        )

        val exception = runCatching {
            service.loadVideos(
                VideoFeedRequest(
                    feedUrl = "https://example.com/premium.xml",
                    username = "",
                    password = "",
                ),
            )
        }.exceptionOrNull()

        assertNotNull(exception)
        assertTrue(exception is FeedSyncException)
        assertTrue(connection.disconnectCalled)
    }

    private companion object {
        private val validFeedBody = """
            <rss version="2.0">
                <channel>
                    <item>
                        <guid>premium-1</guid>
                        <title>Premium Item</title>
                        <description>Fixture feed.</description>
                        <category>Premium</category>
                        <enclosure url="https://video.example.com/premium-1.mp4" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()
    }
}

private class FakeHttpURLConnection(
    private val responseCodeValue: Int,
    inputBody: String = "",
    errorBody: String = "",
) : HttpURLConnection(URL("https://example.com/feed.xml")) {
    private val inputStreamValue = ByteArrayInputStream(inputBody.toByteArray())
    private val errorStreamValue = ByteArrayInputStream(errorBody.toByteArray())

    val recordedRequestProperties = linkedMapOf<String, String>()
    var disconnectCalled = false
        private set

    override fun disconnect() {
        disconnectCalled = true
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit

    override fun setRequestProperty(key: String, value: String) {
        recordedRequestProperties[key] = value
    }

    override fun getRequestProperty(key: String): String? = recordedRequestProperties[key]

    override fun getInputStream(): InputStream = inputStreamValue

    override fun getErrorStream(): InputStream = errorStreamValue

    override fun getResponseCode(): Int = responseCodeValue
}
