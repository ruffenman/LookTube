package com.looktube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
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
