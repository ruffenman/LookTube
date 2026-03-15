package com.looktube.app

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.looktube.model.PlaybackProgress

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
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
            if (isPlaying) {
                progressHandler.removeCallbacks(progressRunnable)
                progressHandler.post(progressRunnable)
            } else {
                progressHandler.removeCallbacks(progressRunnable)
                persistProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            persistProgress()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            persistProgress()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        persistProgress()
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

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

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
