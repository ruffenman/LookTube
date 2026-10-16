package com.looktube.app
import android.app.PendingIntent
import android.content.Intent

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.looktube.model.PlaybackProgress
import com.looktube.model.isCompletedByProgress
import com.looktube.heuristics.recommendNextVideo
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
@UnstableApi

class PlaybackService : MediaSessionService() {
    private lateinit var player: Player
    private lateinit var localPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var castPlayer: CastPlayer? = null
    private var isPlaybackRouteRemote = false
    private var localCaptionCastUrlProvider: LocalCaptionCastUrlProvider = NoOpLocalCaptionCastUrlProvider
    private var lastKnownLocalSnapshot: PlaybackSnapshot? = null
    private var lastKnownRemoteSnapshot: PlaybackSnapshot? = null
    private val autoplayGate = AutoplayCompletionGate()
    private val completedInSession = mutableSetOf<String>()
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            capturePlaybackSnapshot(player)
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
            val container = (application as? LookTubeApplication)?.appContainer ?: return
            val castFinishedId = finishedCastMediaId()
            val endedId = castFinishedId ?: player.currentMediaItem?.mediaId
            if (autoplayGate.update(
                    currentMediaId = endedId,
                    isPlaying = player.isPlaying,
                    ended = player.playbackState == Player.STATE_ENDED || castFinishedId != null,
                    playWhenReady = player.playWhenReady,
                    enabled = container.feedConfigurationStore.persistedConfiguration.value.autoplayEnabled,
                    failedOrIdle = player.playerError != null ||
                        (player.playbackState == Player.STATE_IDLE && castFinishedId == null),
                )) {
                // Leave the current Media3 event dispatch before replacing its item.
                progressHandler.post {
                    val sameCastCompletion = castFinishedId != null && finishedCastMediaId() == endedId
                    val sameLocalCompletion = !isPlaybackRouteRemote &&
                        player.currentMediaItem?.mediaId == endedId && player.playbackState == Player.STATE_ENDED
                    if ((sameLocalCompletion || sameCastCompletion) && player.playWhenReady &&
                        player.playerError == null &&
                        container.repository.selectedVideoId.value == endedId &&
                        container.feedConfigurationStore.persistedConfiguration.value.autoplayEnabled
                    ) {
                        if (sameCastCompletion && endedId != null) persistCastCompletion(endedId)
                        else persistProgress()
                        endedId?.let(completedInSession::add)
                        val repository = container.repository
                        val next = endedId?.let {
                            recommendNextVideo(it, repository.videos.value,
                                container.playbackBookmarkStore.progressSnapshots.value,
                                container.videoEngagementStore.engagementRecords.value, completedInSession)
                        } ?: return@post
                        val progress = container.playbackBookmarkStore.read(next.video.id)
                            ?.takeUnless { it.isCompletedByProgress() }
                        player.setMediaItem(next.video.toPlaybackMediaItem(
                            next.video.playbackUrl ?: return@post,
                            repository.videoCaptions.value[next.video.id]),
                            (progress?.positionSeconds ?: 0L).coerceAtLeast(0L) * 1_000L)
                        player.prepare()
                        player.play()
                        container.videoEngagementStore.recordPlayback(next.video.id)
                        repository.inspectVideo(next.video.id)
                    }
                }
            }
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            val playbackRouteRemote = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            if (playbackRouteRemote == isPlaybackRouteRemote) {
                return
            }
            isPlaybackRouteRemote = playbackRouteRemote
            if (playbackRouteRemote) {
                transferPlaybackToRemoteSession()
            } else {
                restoreLocalPlaybackFromSnapshot()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        localCaptionCastUrlProvider = (application as? LookTubeApplication)
            ?.appContainer
            ?.localCaptionCastHttpServer
            ?: NoOpLocalCaptionCastUrlProvider
        localCaptionCastUrlProvider.start()
        localPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(DOUBLE_TAP_SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(DOUBLE_TAP_SEEK_INCREMENT_MS)
            .build()
            .also(::configureLocalPlayerForPlayback)
        player = runCatching {
            val remotePlayer = RemoteCastPlayer.Builder(this)
                .setMediaItemConverter(
                    CaptionAwareMediaItemConverter(localCaptionCastUrlProvider),
                )
                .setSeekBackIncrementMs(DOUBLE_TAP_SEEK_INCREMENT_MS)
                .setSeekForwardIncrementMs(DOUBLE_TAP_SEEK_INCREMENT_MS)
                .build()
            CastPlayer.Builder(this)
                .setLocalPlayer(localPlayer)
                .setRemotePlayer(remotePlayer)
                .build()
                .also {
                    castPlayer = it
                    isPlaybackRouteRemote =
                        it.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
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
        progressHandler.removeCallbacksAndMessages(null)
        persistProgress()
        player.removeListener(playerListener)
        mediaSession.release()
        castPlayer?.release()
        localCaptionCastUrlProvider.stop()
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
        // The receiver may clear its position/timeline when it becomes idle.
        if (isPlaybackRouteRemote && player.playbackState == Player.STATE_IDLE) return
        val currentMediaItem = player.currentMediaItem ?: return
        val mediaId = currentMediaItem.mediaId.takeIf(String::isNotBlank) ?: return
        val durationMs = player.duration.takeIf { it > 0 } ?: return
        val progress = PlaybackProgress(
            videoId = mediaId,
            positionSeconds = (player.currentPosition.coerceAtLeast(0L) / 1_000L),
            durationSeconds = durationMs / 1_000L,
        )
        (application as? LookTubeApplication)
            ?.appContainer
            ?.let { container ->
                container.playbackBookmarkStore.write(progress)
                container.videoEngagementStore.recordPlaybackProgress(progress)
            }
    }

    private fun finishedCastMediaId(): String? = runCatching {
        if (!isPlaybackRouteRemote) return@runCatching null
        val session = CastContext.getSharedInstance(this).sessionManager.currentCastSession
            ?.takeIf { it.isConnected } ?: return@runCatching null
        val status = session.remoteMediaClient?.mediaStatus ?: return@runCatching null
        val snapshot = lastKnownRemoteSnapshot ?: return@runCatching null
        snapshot.mediaId.takeIf {
            isNaturalCastCompletion(
                isRemote = isPlaybackRouteRemote,
                isIdle = status.playerState == MediaStatus.PLAYER_STATE_IDLE,
                finished = status.idleReason == MediaStatus.IDLE_REASON_FINISHED,
                sameStream = status.mediaInfo?.contentId == snapshot.mediaItem.localConfiguration?.uri?.toString(),
            )
        }
    }.getOrNull()

    private fun persistCastCompletion(videoId: String) {
        val container = (application as? LookTubeApplication)?.appContainer ?: return
        val duration = container.playbackBookmarkStore.read(videoId)?.durationSeconds
            ?.takeIf { it > 0 }
            ?: container.repository.videos.value.firstOrNull { it.id == videoId }?.durationSeconds
            ?.takeIf { it > 0 }
            ?: return
        val progress = PlaybackProgress(videoId, duration, duration)
        container.playbackBookmarkStore.write(progress)
        container.videoEngagementStore.recordPlaybackProgress(progress)
    }

    private fun capturePlaybackSnapshot(player: Player) {
        val snapshot = player.toPlaybackSnapshotOrNull() ?: return
        if (isPlaybackRouteRemote) {
            lastKnownRemoteSnapshot = snapshot
        } else {
            lastKnownLocalSnapshot = snapshot
        }
    }

    private fun transferPlaybackToRemoteSession() {
        val remotePlayer = castPlayer ?: return
        val snapshot = lastKnownLocalSnapshot ?: localPlayer.toPlaybackSnapshotOrNull() ?: return
        if (
            !shouldTransferPlaybackToRemoteSession(
                remoteCurrentMediaId = remotePlayer.currentMediaItem?.mediaId,
                remotePlaybackState = remotePlayer.playbackState,
                remotePositionMs = remotePlayer.currentPosition,
                snapshot = snapshot,
            )
        ) {
            return
        }
        remotePlayer.setMediaItem(snapshot.mediaItem, snapshot.positionMs)
        remotePlayer.prepare()
        remotePlayer.playWhenReady = snapshot.playWhenReady
        lastKnownRemoteSnapshot = snapshot
    }

    private fun restoreLocalPlaybackFromSnapshot() {
        val snapshot = lastKnownRemoteSnapshot ?: lastKnownLocalSnapshot ?: return
        if (
            shouldRestoreLocalPlaybackFromSnapshot(
                localCurrentMediaId = localPlayer.currentMediaItem?.mediaId,
                localPlaybackState = localPlayer.playbackState,
                localPositionMs = localPlayer.currentPosition,
                snapshot = snapshot,
            )
        ) {
            localPlayer.setMediaItem(snapshot.mediaItem, snapshot.positionMs)
            localPlayer.prepare()
        }
        localPlayer.playWhenReady = snapshot.playWhenReady
        lastKnownLocalSnapshot = snapshot
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}

internal data class PlaybackSnapshot(
    val mediaItem: MediaItem,
    val positionMs: Long,
    val playWhenReady: Boolean,
) {
    val mediaId: String
        get() = mediaItem.mediaId
}

internal val PLAYBACK_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

internal const val PLAYBACK_HANDLES_AUDIO_FOCUS = true
internal const val PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY = true
internal const val PLAYBACK_WAKE_MODE = C.WAKE_MODE_NETWORK
internal const val DOUBLE_TAP_SEEK_INCREMENT_MS = 10_000L
internal object NoOpLocalCaptionCastUrlProvider : LocalCaptionCastUrlProvider {
    override fun start() = Unit

    override fun stop() = Unit

    override fun buildRemoteCaptionUrl(localUri: android.net.Uri): String? = null
}

internal fun shouldTransferPlaybackToRemoteSession(
    remoteCurrentMediaId: String?,
    remotePlaybackState: Int,
    remotePositionMs: Long,
    snapshot: PlaybackSnapshot?,
): Boolean {
    val validSnapshot = snapshot ?: return false
    return when {
        remoteCurrentMediaId == validSnapshot.mediaId &&
            remotePlaybackState in ACTIVE_PLAYBACK_STATES -> false
        remoteCurrentMediaId.isNullOrBlank() &&
            remotePlaybackState in ACTIVE_PLAYBACK_STATES &&
            remotePositionMs > 0L -> false
        else -> true
    }
}

internal fun shouldRestoreLocalPlaybackFromSnapshot(
    localCurrentMediaId: String?,
    localPlaybackState: Int,
    localPositionMs: Long,
    snapshot: PlaybackSnapshot?,
): Boolean {
    val validSnapshot = snapshot ?: return false
    return !(
        localCurrentMediaId == validSnapshot.mediaId &&
            localPlaybackState in ACTIVE_PLAYBACK_STATES &&
            localPositionMs > 0L
        )
}

internal fun configureLocalPlayerForPlayback(player: LocalPlaybackConfigurable) {
    player.setAudioAttributes(PLAYBACK_AUDIO_ATTRIBUTES, PLAYBACK_HANDLES_AUDIO_FOCUS)
    player.setHandleAudioBecomingNoisy(PLAYBACK_HANDLES_AUDIO_BECOMING_NOISY)
    player.setWakeMode(PLAYBACK_WAKE_MODE)
}

internal interface LocalPlaybackConfigurable {
    fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean)
    fun setHandleAudioBecomingNoisy(handleAudioBecomingNoisy: Boolean)
    fun setWakeMode(@C.WakeMode wakeMode: Int)
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

            override fun setWakeMode(wakeMode: Int) {
                player.setWakeMode(wakeMode)
            }
        },
    )
}

private fun Player.toPlaybackSnapshotOrNull(): PlaybackSnapshot? =
    currentMediaItem?.let { mediaItem ->
        PlaybackSnapshot(
            mediaItem = mediaItem,
            positionMs = currentPosition.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
        )
    }

private val ACTIVE_PLAYBACK_STATES = setOf(
    Player.STATE_READY,
    Player.STATE_BUFFERING,
)
