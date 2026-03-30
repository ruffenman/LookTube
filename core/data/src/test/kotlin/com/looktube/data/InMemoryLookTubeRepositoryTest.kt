package com.looktube.data

import com.looktube.model.SyncPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryLookTubeRepositoryTest {
    @Test
    fun bootstrapStartsWithEmptyLibrary() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.bootstrap()

        assertTrue(repository.videos.value.isEmpty())
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
    fun refreshReportsNoSyncedLibraryData() = runTest {
        val repository = InMemoryLookTubeRepository()

        repository.bootstrap()
        repository.refreshLibrary()

        assertEquals(SyncPhase.Success, repository.librarySyncState.value.phase)
        assertTrue(repository.videos.value.isEmpty())
    }
}
