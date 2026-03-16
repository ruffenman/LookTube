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
    fun signInUpdatesRepositoryNotesForFeedFirstSync() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.signInToPremiumFeed()
        assertEquals(
            "Spike feed-first Premium access first with copied feed URLs only.",
            repository.accountSession.value.notes,
        )
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
