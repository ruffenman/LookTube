package com.looktube.data

import com.looktube.model.AuthMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLookTubeRepositoryTest {
    @Test
    fun bootstrapSeedsSamplePremiumVideos() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.bootstrap()

        assertTrue(repository.videos.value.any { it.isPremium })
        assertTrue(repository.selectedVideoId.value != null)
    }

    @Test
    fun selectingAuthModePersistsChoice() {
        val repository = InMemoryLookTubeRepository()

        repository.selectAuthMode(AuthMode.SessionCookie)

        assertEquals(AuthMode.SessionCookie, repository.accountSession.value.authMode)
    }
}
