package com.looktube.app
import android.content.Intent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.looktube.data.LookTubeRepository
import com.looktube.model.FeedConfiguration
import com.looktube.model.LibrarySyncState
import com.looktube.model.PlaybackProgress
import com.looktube.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LookTubeAppViewModel(
    private val repository: LookTubeRepository,
) : ViewModel() {
    val accountSession = repository.accountSession
    val feedConfiguration = repository.feedConfiguration
    val librarySyncState = repository.librarySyncState
    val videos = repository.videos
    val playbackProgress = repository.playbackProgress
    private val requestedPageState = MutableStateFlow<Int?>(null)
    val requestedPage: StateFlow<Int?> = requestedPageState.asStateFlow()

    val selectedVideo: StateFlow<VideoSummary?> = combine(
        repository.videos,
        repository.selectedVideoId,
    ) { videos, selectedVideoId ->
        videos.firstOrNull { it.id == selectedVideoId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val selectedProgress: StateFlow<PlaybackProgress?> = combine(
        repository.playbackProgress,
        repository.selectedVideoId,
    ) { progressMap, selectedVideoId ->
        selectedVideoId?.let(progressMap::get)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            repository.bootstrap()
        }
    }

    fun updateFeedUrl(feedUrl: String) {
        viewModelScope.launch {
            repository.updateFeedUrl(feedUrl)
        }
    }
    fun signInToPremiumFeed() {
        viewModelScope.launch {
            repository.signInToPremiumFeed()
        }
    }
    fun clearSyncedData() {
        viewModelScope.launch {
            repository.clearSyncedData()
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            repository.refreshLibrary()
        }
    }

    fun handleLaunchIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        launchIntent.getStringExtra(LookTubeLaunchContract.EXTRA_OPEN_VIDEO_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { videoId ->
                repository.selectVideo(videoId)
                requestedPageState.value = LookTubeLaunchContract.PLAYER_PAGE_INDEX
                return
            }
        launchIntent.getIntExtra(LookTubeLaunchContract.EXTRA_TARGET_PAGE, -1)
            .takeIf { pageIndex -> pageIndex >= 0 }
            ?.let { pageIndex ->
                requestedPageState.value = pageIndex
            }
    }

    fun consumeRequestedPage(pageIndex: Int) {
        if (requestedPageState.value == pageIndex) {
            requestedPageState.value = null
        }
    }

    fun selectVideo(videoId: String) {
        repository.selectVideo(videoId)
    }

    companion object {
        fun factory(repository: LookTubeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LookTubeAppViewModel(repository) as T
                }
            }
    }
}
