package com.looktube.app

import android.app.Application
import com.looktube.data.ConfigurableLookTubeRepository
import com.looktube.data.LookTubeRepository
import com.looktube.database.PlaybackBookmarkStore
import com.looktube.network.HttpRssVideoFeedService
import com.looktube.network.RssVideoFeedParser

class LookTubeApplication : Application() {
    val appContainer: AppContainer by lazy {
        val feedConfigurationStore = SharedPreferencesFeedConfigurationStore(this)
        val syncedLibraryStore = SharedPreferencesSyncedLibraryStore(this)
        val playbackBookmarkStore = SharedPreferencesPlaybackBookmarkStore(this)
        AppContainer(
            playbackBookmarkStore = playbackBookmarkStore,
            repository = ConfigurableLookTubeRepository(
                feedConfigurationStore = feedConfigurationStore,
                syncedLibraryStore = syncedLibraryStore,
                playbackBookmarkStore = playbackBookmarkStore,
                videoFeedService = HttpRssVideoFeedService(
                    parser = RssVideoFeedParser(),
                ),
            ),
        )
    }
}

data class AppContainer(
    val playbackBookmarkStore: PlaybackBookmarkStore,
    val repository: LookTubeRepository,
)
