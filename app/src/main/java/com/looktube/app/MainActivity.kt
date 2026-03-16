package com.looktube.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewmodel.compose.viewModel
@UnstableApi

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as LookTubeApplication).appContainer.repository

        setContent {
            val viewModel: LookTubeAppViewModel = viewModel(
                factory = LookTubeAppViewModel.factory(repository),
            )
            LookTubeApp(viewModel = viewModel)
        }
    }
}
