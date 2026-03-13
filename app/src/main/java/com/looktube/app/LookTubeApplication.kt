package com.looktube.app

import android.app.Application
import com.looktube.data.InMemoryLookTubeRepository
import com.looktube.data.LookTubeRepository

class LookTubeApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(
            repository = InMemoryLookTubeRepository(),
        )
    }
}

data class AppContainer(
    val repository: LookTubeRepository,
)
