package com.looktube.app

import com.looktube.data.InMemoryLookTubeRepository
import com.looktube.model.AuthMode
import com.looktube.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LookTubeAppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun bootstrapsSampleFeedAndTracksAuthMode() = runTest {
        val repository = InMemoryLookTubeRepository()

        val viewModel = LookTubeAppViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.videos.value.isNotEmpty())

        viewModel.selectAuthMode(AuthMode.CredentialedFeed)
        assertEquals(AuthMode.CredentialedFeed, viewModel.accountSession.value.authMode)
    }
}
