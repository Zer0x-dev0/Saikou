package ani.saikou.media.anime.mpv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var _player: Player? = null
    val player: Player? get() = _player


    private val _fallbackPlaybackState = MutableStateFlow(Player.PlaybackState.IDLE)
    private val _fallbackIsPlaying = MutableStateFlow(false)
    private val _fallbackCurrentPosition = MutableStateFlow(0L)
    private val _fallbackDuration = MutableStateFlow(0L)
    private val _fallbackVolume = MutableStateFlow(100)
    private val _fallbackPlaybackSpeed = MutableStateFlow(1.0f)
    private val _fallbackAudioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _fallbackSubtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _fallbackVideoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    private val _fallbackVideoScaleMode = MutableStateFlow(Player.VideoScaleMode.FIT)


    val playbackState: StateFlow<Player.PlaybackState>
        get() = _player?.playbackState ?: _fallbackPlaybackState.asStateFlow()

    val isPlaying: StateFlow<Boolean>
        get() = _player?.isPlaying ?: _fallbackIsPlaying.asStateFlow()

    val currentPosition: StateFlow<Long>
        get() = _player?.currentPosition ?: _fallbackCurrentPosition.asStateFlow()

    val duration: StateFlow<Long>
        get() = _player?.duration ?: _fallbackDuration.asStateFlow()

    val volume: StateFlow<Int>
        get() = _player?.volume ?: _fallbackVolume.asStateFlow()

    val playbackSpeed: StateFlow<Float>
        get() = _player?.playbackSpeed ?: _fallbackPlaybackSpeed.asStateFlow()

    val audioTracks: StateFlow<List<AudioTrack>>
        get() = _player?.audioTracks ?: _fallbackAudioTracks.asStateFlow()

    val subtitleTracks: StateFlow<List<SubtitleTrack>>
        get() = _player?.subtitleTracks ?: _fallbackSubtitleTracks.asStateFlow()

    val videoTracks: StateFlow<List<VideoTrack>>
        get() = _player?.videoTracks ?: _fallbackVideoTracks.asStateFlow()

    val videoScaleMode: StateFlow<Player.VideoScaleMode>
        get() = _player?.videoScaleMode ?: _fallbackVideoScaleMode.asStateFlow()


    fun setPlayerInstance(playerInstance: Player) {
        _player = playerInstance
    }


//    fun initPlayer(surfaceHolder: Any) {
//        _player?.init(surfaceHolder)
//    }

    // Controls

    fun releasePlayer() {
        _player?.release()
        _player = null
    }

    fun play() {
        _player?.play()
    }

    fun pause() {
        _player?.pause()
    }

    fun stop() {
        _player?.stop()
    }

    fun seekTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }

    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        audioTracks: List<ExternalAudio> = emptyList(),
        subtitles: List<ExternalSubtitle> = emptyList()
    ) {
        _player?.loadMedia(videoUrl, headers, startPositionMs, audioTracks, subtitles)
    }

    fun selectAudioTrack(trackId: Int) {
        _player?.selectAudioTrack(trackId)
    }

    fun selectSubtitleTrack(trackId: Int) {
        _player?.selectSubtitleTrack(trackId)
    }

    fun selectVideoTrack(trackId: Int) {
        _player?.selectVideoTrack(trackId)
    }

    fun setVolume(level: Int) {
        _player?.setVolume(level)
    }

    fun increaseVolume() {
        _player?.increaseVolume()
    }

    fun decreaseVolume() {
        _player?.decreaseVolume()
    }

    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
    }

    fun nextEpisode() {}
    fun previousEpisode() {}

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}