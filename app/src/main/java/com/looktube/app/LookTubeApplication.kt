package com.looktube.app

import android.app.Application
import com.looktube.data.ConfigurableLookTubeRepository
import com.looktube.data.FeedConfigurationStore
import com.looktube.data.LocalCaptionEngineRegistry
import com.looktube.data.LookTubeRepository
import com.looktube.data.SyncedLibraryStore
import com.looktube.data.VideoCaptionStore
import com.looktube.database.PlaybackBookmarkStore
import com.looktube.database.VideoEngagementStore
import androidx.media3.common.util.UnstableApi
import com.looktube.network.HttpRssVideoFeedService
import com.looktube.network.VideoFeedService
import com.looktube.network.RssVideoFeedParser
@UnstableApi

class LookTubeApplication : Application() {
    val appContainer: AppContainer by lazy {
        val feedConfigurationStore = SharedPreferencesFeedConfigurationStore(this)
        val syncedLibraryStore = SharedPreferencesSyncedLibraryStore(this)
        val playbackBookmarkStore = SharedPreferencesPlaybackBookmarkStore(this)
        val videoEngagementStore = SharedPreferencesVideoEngagementStore(this)
        val videoCaptionStore = FileVideoCaptionStore(this)
        val localCaptionEngineRegistry = createLocalCaptionEngineRegistry(this)
        val localCaptionCastHttpServer = LocalCaptionCastHttpServer(videoCaptionStore.rootDirectory)
        val videoFeedService = HttpRssVideoFeedService(
            parser = RssVideoFeedParser(),
        )
        val librarySyncNotifier = LibrarySyncNotifier(this)
        AppContainer(
            feedConfigurationStore = feedConfigurationStore,
            syncedLibraryStore = syncedLibraryStore,
            playbackBookmarkStore = playbackBookmarkStore,
            videoEngagementStore = videoEngagementStore,
            localCaptionEngineRegistry = localCaptionEngineRegistry,
            videoCaptionStore = videoCaptionStore,
            localCaptionCastHttpServer = localCaptionCastHttpServer,
            videoFeedService = videoFeedService,
            librarySyncNotifier = librarySyncNotifier,
            repository = ConfigurableLookTubeRepository(
                feedConfigurationStore = feedConfigurationStore,
                syncedLibraryStore = syncedLibraryStore,
                playbackBookmarkStore = playbackBookmarkStore,
                videoEngagementStore = videoEngagementStore,
                videoFeedService = videoFeedService,
                libraryRefreshScheduler = WorkManagerLibraryRefreshScheduler(this),
                localCaptionEngineRegistry = localCaptionEngineRegistry,
                videoCaptionStore = videoCaptionStore,
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        appContainer.librarySyncNotifier.ensureNotificationChannel()
    }
}

@UnstableApi
data class AppContainer(
    val feedConfigurationStore: FeedConfigurationStore,
    val syncedLibraryStore: SyncedLibraryStore,
    val playbackBookmarkStore: PlaybackBookmarkStore,
    val videoEngagementStore: VideoEngagementStore,
    val localCaptionEngineRegistry: LocalCaptionEngineRegistry,
    val videoCaptionStore: VideoCaptionStore,
    val localCaptionCastHttpServer: LocalCaptionCastUrlProvider,
    val videoFeedService: VideoFeedService,
    val librarySyncNotifier: LibrarySyncNotifier,
    val repository: LookTubeRepository,
)
