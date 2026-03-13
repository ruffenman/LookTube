package com.looktube.model

data class VideoSummary(
    val id: String,
    val title: String,
    val description: String,
    val isPremium: Boolean,
    val feedCategory: String,
    val playbackUrl: String?,
)
