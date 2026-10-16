package com.looktube.app

import org.junit.Assert.*
import org.junit.Test

class AutoplayCompletionGateTest {
    @Test fun consumesCompletionOnlyOnceAfterActualPlayback() {
        val gate = AutoplayCompletionGate()
        assertFalse(gate.update("one", true, false, true, true, false))
        assertTrue(gate.update("one", false, true, true, true, false))
        assertFalse(gate.update("one", false, true, true, true, false))
    }

    @Test fun previewAndPausedEndDoNotAdvance() {
        val gate = AutoplayCompletionGate()
        assertFalse(gate.update("preview", false, true, true, true, false))
        gate.update("played", true, false, true, true, false)
        assertFalse(gate.update("played", false, true, false, true, false))
    }

    @Test fun turningOffBeforeCompletionStopsContinuationAndEnablingAfterwardsDoesNotRestart() {
        val gate = AutoplayCompletionGate()
        gate.update("one", true, false, true, true, false)
        assertFalse(gate.update("one", false, true, true, false, false))
        assertFalse(gate.update("one", false, true, true, true, false))
    }

    @Test fun errorOrStopClearsPermissionToContinue() {
        val gate = AutoplayCompletionGate()
        gate.update("one", true, false, true, true, false)
        gate.update("one", false, false, true, true, true)
        assertFalse(gate.update("one", false, true, true, true, false))
    }

    @Test fun changingItemsDiscardsStaleCompletionButNewPlaybackCanContinue() {
        val gate = AutoplayCompletionGate()
        gate.update("one", true, false, true, true, false)
        assertFalse(gate.update("two", false, true, true, true, false))
        gate.update("two", true, false, true, true, false)
        assertTrue(gate.update("two", false, true, true, true, false))
    }

    @Test fun castCompletionRequiresFinishedMatchingStreamAndConnectedRemoteRoute() {
        assertTrue(isNaturalCastCompletion(true, true, true, true))
        assertFalse(isNaturalCastCompletion(false, true, true, true))
        assertFalse(isNaturalCastCompletion(true, false, true, true))
        assertFalse(isNaturalCastCompletion(true, true, false, true))
        assertFalse(isNaturalCastCompletion(true, true, true, false))
    }
}
