package com.looktube.app

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.TimeUnit

class WorkManagerLibraryRefreshSchedulerTest {
    @Test
    fun periodicRefreshPolicyKeepsTheExistingCadence() {
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, WorkManagerLibraryRefreshScheduler.PERIODIC_WORK_POLICY)
    }

    @Test
    fun periodicRefreshPolicyUpgradesLegacyRegistrationsOnce() {
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, periodicWorkPolicy(storedVersion = 0))
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, periodicWorkPolicy(storedVersion = 1))
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, periodicWorkPolicy(storedVersion = 2))
    }

    @Test
    fun periodicRefreshWorkUsesConnectedNetworkWithoutBatteryGate() {
        val request = buildPeriodicLibraryRefreshWorkRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.MINUTES.toMillis(15), request.workSpec.intervalDuration)
        assertEquals(TimeUnit.MINUTES.toMillis(5), request.workSpec.flexDuration)
    }

    @Test
    fun catchUpRefreshWorkUsesTheSameReachabilityConstraint() {
        val request = buildCatchUpLibraryRefreshWorkRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertFalse(request.workSpec.constraints.requiresBatteryNotLow())
    }
}
