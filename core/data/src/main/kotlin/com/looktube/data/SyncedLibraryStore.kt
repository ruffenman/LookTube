package com.looktube.data

import com.looktube.model.PersistedLibrarySnapshot
import kotlinx.coroutines.flow.StateFlow

interface SyncedLibraryStore {
    val persistedSnapshot: StateFlow<PersistedLibrarySnapshot?>

    suspend fun save(snapshot: PersistedLibrarySnapshot)

    suspend fun clear()
}
