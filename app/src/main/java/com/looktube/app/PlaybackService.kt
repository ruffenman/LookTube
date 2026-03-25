package com.looktube.app
import android.app.PendingIntent
import android.content.Intent

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.looktube.model.PlaybackProgress
@UnstableApi

class PlaybackService : MediaSessionService() {
    private lateinit var player: Player
    private lateinit var localPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var castPlayer: CastPlayer? = null
    private var lastKnownMediaItem: MediaItem? = null
    private var lastKnownPositionMs: Long = 0L
    private var lastKnownPlayWhenReady: Boolean = false
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            persistProgress()
            if (player.isPlaying) {
                progressHandler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            capturePlaybackSnapshot(player)
            if (isPlaying) {
                progressHandler.removeCallbacks(progressRunnable)
                progressHandler.post(progressRunnable)
            } else {
                progressHandler.removeCallbacks(progressRunnable)
                persistProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            capturePlaybackSnapshot(player)
            persistProgress()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            capturePlaybackSnapshot(player)
            persistProgress()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            capturePlaybackSnapshot(player)
        }
    }

    private val castSessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            transferPlaybackToRemoteSession()
        }

        override fun onCastSessionUnavailable() {
            restoreLocalPlaybackFromSnapshot()
        }
    }

    override fun onCreate() {
        super.onCreate()
        localPlayer = ExoPlayer.Builder(this)
            .build()
            .also(::configureLocalPlayerForPlayback)
        player = runCatching {
            CastPlayer.Builder(this)
                .setLocalPlayer(localPlayer)
                .build()
                .also {
                    castPlayer = it
                    it.setSessionAvailabilityListener(castSessionAvailabilityListener)
                }
        }.getOrElse { localPlayer }.apply {
            addListener(playerListener)
        }
        capturePlaybackSnapshot(player)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        persistProgress()
        player.removeListener(playerListener)
        mediaSession.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        localPlayer.release()
        super.onDestroy()
    }

    private fun createSessionActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun persistProgress() {
        val currentMediaItem = player.currentMediaItem ?: return
        val mediaId = currentMediaItem.mediaId.takeIf(String::isNotBlank) ?: return
        val durationMs = player.duration.takeIf { it > 0 } ?: return
        (application as? LookTubeApplication)
            ?.appContainer
            ?.playbackBookmarkStore
            ?.write(
                PlaybackProgress(
                    videoId = mediaId,
                    positionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1_000L),
                    durationSeconds = durationMs / 1_000L,
                ),
            )
    }

    private fun capturePlaybackSnapshot(player: Player) {
        player.currentMediaItem?.let { mediaItem ->
            lastKnownMediaItem = mediaItem
        }
        lastKnownPositionMs = player.currentPosition.coerceAtLeast(0L)
        lastKnownPlayWhenReady = player.playWhenReady
    }

    private fun transferPlaybackToRemoteSession() {
        val remotePlayer = castPlayer ?: return
        capturePlaybackSnapshot(localPlayer)
        val mediaItem = lastKnownMediaItem ?: localPlayer.currentMediaItem ?: return
        remotePlayer.setMediaItem(mediaItem, lastKnownPositionMs)
        remotePlayer.prepare()
        remotePlayer.playWhenReady = lastKnownPlayWhenReady
    }

    private fun restoreLocalPlaybackFromSnapshot() {
        val mediaItem = lastKnownMediaItem ?: return
        localPlayer.setMediaItem(mediaItem, lastKnownPositionMs)
        localPlayer.prepare()
        localPlayer.playWhenReady = lastKnownPlayWhenReady
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}

internal val PLAYBACK_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

internal const val PLAYBACK_HANDLES_AUDIO_FOCUS = true
internal const val PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY = true

internal fun configureLocalPlayerForPlayback(player: LocalPlaybackConfigurable) {
    player.setAudioAttributes(PLAYBACK_AUDIO_ATTRIBUTES, PLAYBACK_HANDLES_AUDIO_FOCUS)
    player.setHandleAudioBecomingNoisy(PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY)
}

internal interface LocalPlaybackConfigurable {
    fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean)
    fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean)
}

private fun configureLocalPlayerForPlayback(player: ExoPlayer) {
    configureLocalPlayerForPlayback(
        object : LocalPlaybackConfigurable {
            override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
                player.setAudioAttributes(audioAttributes, handleAudioFocus)
            }

            override fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean) {
                player.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
            }
        },
    )
}
