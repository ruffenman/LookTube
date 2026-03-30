package com.looktube.app
import android.content.Intent

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
@UnstableApi

class MainActivity : AppCompatActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as LookTubeApplication).appContainer.repository
        launchIntent = intent

        setContent {
            val viewModel: LookTubeAppViewModel = viewModel(
                factory = LookTubeAppViewModel.factory(repository),
            )
            LookTubeApp(
                viewModel = viewModel,
                launchIntent = launchIntent,
                showLaunchIntroOnStart = savedInstanceState == null,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntent = intent
    }

}
