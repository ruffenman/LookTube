package com.looktube.app

import android.app.Application
import com.looktube.data.ConfigurableLookTubeRepository
import com.looktube.data.LookTubeRepository
import com.looktube.network.HttpRssVideoFeedService
import com.looktube.network.RssVideoFeedParser

class LookTubeApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(
            repository = ConfigurableLookTubeRepository(
                feedConfigurationStore = SharedPreferencesFeedConfigurationStore(this),
                videoFeedService = HttpRssVideoFeedService(
                    parser = RssVideoFeedParser(),
                ),
            ),
        )
    }
}

data class AppContainer(
    val repository: LookTubeRepository,
)
