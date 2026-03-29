package com.looktube.app

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.looktube.model.DefaultLocalCaptionModel
@UnstableApi

internal class CaptionAwareMediaItemConverter(
    private val captionCastUrlProvider: LocalCaptionCastUrlProvider,
    private val delegate: MediaItemConverter = DefaultMediaItemConverter(),
) : MediaItemConverter {
    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val defaultQueueItem = delegate.toMediaQueueItem(mediaItem)
        val originalMediaInfo = defaultQueueItem.media ?: return defaultQueueItem
        val existingTracks = originalMediaInfo.mediaTracks.orEmpty()
        var nextTrackId = (existingTracks.maxOfOrNull(MediaTrack::getId) ?: 0L) + 1L
        val subtitleTracks = mediaItem.localConfiguration?.subtitleConfigurations
            .orEmpty()
            .mapNotNull { subtitleConfiguration ->
                subtitleConfiguration.toCastMediaTrack(
                    trackId = nextTrackId++,
                    captionCastUrlProvider = captionCastUrlProvider,
                )
            }
        if (subtitleTracks.isEmpty()) {
            return defaultQueueItem
        }
        val mediaInfoBuilder = MediaInfo.Builder(
            originalMediaInfo.contentId,
            originalMediaInfo.contentType,
        )
            .setStreamType(originalMediaInfo.streamType)
            .setStreamDuration(originalMediaInfo.streamDuration)
            .setMetadata(originalMediaInfo.metadata)
            .setMediaTracks(existingTracks + subtitleTracks)
        originalMediaInfo.contentUrl
            ?.takeIf(String::isNotBlank)
            ?.let(mediaInfoBuilder::setContentUrl)
        originalMediaInfo.entity
            ?.takeIf(String::isNotBlank)
            ?.let(mediaInfoBuilder::setEntity)
        originalMediaInfo.customData
            ?.let(mediaInfoBuilder::setCustomData)
        originalMediaInfo.textTrackStyle
            ?.let(mediaInfoBuilder::setTextTrackStyle)
        val updatedMediaInfo = mediaInfoBuilder.build()
        return MediaQueueItem.Builder(updatedMediaInfo)
            .setAutoplay(defaultQueueItem.autoplay)
            .setPlaybackDuration(defaultQueueItem.playbackDuration)
            .setPreloadTime(defaultQueueItem.preloadTime)
            .setStartTime(defaultQueueItem.startTime)
            .apply {
                defaultQueueItem.customData?.let(::setCustomData)
                defaultQueueItem.activeTrackIds
                    ?.takeIf { trackIds -> trackIds.isNotEmpty() }
                    ?.let(::setActiveTrackIds)
            }
            .build()
    }
}

private fun MediaItem.SubtitleConfiguration.toCastMediaTrack(
    trackId: Long,
    captionCastUrlProvider: LocalCaptionCastUrlProvider,
): MediaTrack? {
    val subtitleUri = uri
    val contentId = when (subtitleUri.scheme?.lowercase()) {
        "http", "https" -> subtitleUri.toString()
        "file" -> captionCastUrlProvider.buildRemoteCaptionUrl(subtitleUri)
        else -> null
    } ?: return null
    return MediaTrack.Builder(trackId, MediaTrack.TYPE_TEXT)
        .setContentId(contentId)
        .setContentType(mimeType ?: MimeTypes.TEXT_VTT)
        .setLanguage(language ?: DefaultLocalCaptionModel.languageTag)
        .setName(label ?: "English (generated)")
        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
        .build()
}
