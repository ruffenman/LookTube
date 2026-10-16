package com.looktube.app

/** A completion must belong to an item that actually played, and can be consumed only once. */
internal class AutoplayCompletionGate {
    private var mediaId: String? = null
    private var armed = false

    fun update(
        currentMediaId: String?,
        isPlaying: Boolean,
        ended: Boolean,
        playWhenReady: Boolean,
        enabled: Boolean,
        failedOrIdle: Boolean,
    ): Boolean {
        if (mediaId != currentMediaId) {
            mediaId = currentMediaId
            armed = false
        }
        if (failedOrIdle) armed = false
        if (isPlaying && !failedOrIdle && currentMediaId != null) armed = true
        if (!ended) return false
        val advance = armed && playWhenReady && enabled && !failedOrIdle
        armed = false
        return advance
    }
}

/** Cast idle also represents cancellation, errors and disconnects; only a matching finished item qualifies. */
internal fun isNaturalCastCompletion(isRemote: Boolean, isIdle: Boolean, finished: Boolean, sameStream: Boolean): Boolean =
    isRemote && isIdle && finished && sameStream
