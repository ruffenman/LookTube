package com.looktube.network

import com.looktube.model.VideoSummary

fun interface VideoFeedService {
    fun loadVideos(request: VideoFeedRequest): List<VideoSummary>
}
