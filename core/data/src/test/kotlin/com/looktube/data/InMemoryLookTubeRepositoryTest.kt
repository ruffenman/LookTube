package com.looktube.data

import com.looktube.model.SyncPhase
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
        assertEquals(null, repository.selectedVideoId.value)
    }

    @Test
    fun signInMarksRepositoryAsCredentialedFeed() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.signInToPremiumFeed()

        assertEquals(com.looktube.model.AuthMode.CredentialedFeed, repository.accountSession.value.authMode)
    }

    @Test
    fun refreshLeavesSeededContentActive() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.bootstrap()
        repository.refreshLibrary()

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertTrue(repository.videos.value.isNotEmpty())
    }
}
