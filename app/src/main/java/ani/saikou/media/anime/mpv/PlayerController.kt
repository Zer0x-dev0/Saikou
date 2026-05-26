package ani.saikou.media.anime.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MPVPlayerImpl(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs), Player {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val TAG = "mpv"

    // StateFlows
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.PlaybackState.IDLE)
    override val playbackState: StateFlow<Player.PlaybackState> = _playbackState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    override val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    override val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    override val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()

    private val _volume = MutableStateFlow(100)
    override val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _audioChannel = MutableStateFlow(Player.AudioChannel.AUTO)
    override val audioChannel: StateFlow<Player.AudioChannel> = _audioChannel.asStateFlow()

    private val _videoScaleMode = MutableStateFlow(Player.VideoScaleMode.FIT)
    override val videoScaleMode: StateFlow<Player.VideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(Player.Decoder.AUTO)
    override val currentDecoder: StateFlow<Player.Decoder> = _currentDecoder.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _availableDecoders = MutableStateFlow(Player.Decoder.entries)
    override val availableDecoders: StateFlow<List<Player.Decoder>> =
        _availableDecoders.asStateFlow()

    // Lifecycle tracking
    private var isInitialized = false
    private var surfaceReady = false

    // Pending media data
    private var pendingMediaState: PendingMediaState? = null

    data class PendingMediaState(
        val videoUrl: String,
        val headers: Map<String, String>,
        val startPositionMs: Long,
        val audioTracks: List<ExternalAudio>,
        val subtitles: List<ExternalSubtitle>
    )

    override fun initOptions() {
        Log.d(TAG, "initOptions()")
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("msg-level", "all=v")
        mpv.setOptionString("ytdl", "no")


        mpv.setOptionString("hwdec", "mediacodec-copy")

        val cacheMegs =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) 64 else 32
        mpv.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        mpv.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("volume-max", "200")
        mpv.setOptionString("volume", "100")
        mpv.setOptionString("speed", "1.0")
    }

    override fun postInitOptions() {
        Log.d(TAG, "postInitOptions()")
    }

    override fun observeProperties() {
        Log.d(TAG, "observeProperties()")
        mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("volume", MPV.mpvFormat.MPV_FORMAT_INT64)
        mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NODE)
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)

        setupObservers()
    }

    private fun setupObservers() {
        mpv.addObserver(object : MPV.EventObserver {
            override fun eventProperty(property: String) = handlePropertyChange(property)
            override fun eventProperty(property: String, value: Long) =
                handlePropertyChange(property)

            override fun eventProperty(property: String, value: Boolean) =
                handlePropertyChange(property)

            override fun eventProperty(property: String, value: String) =
                handlePropertyChange(property)

            override fun eventProperty(property: String, value: Double) =
                handlePropertyChange(property)

            override fun eventProperty(property: String, value: MPVNode) =
                handlePropertyChange(property)

            override fun event(eventId: Int, data: MPVNode) {
                Log.d(TAG, "MPV Event: $eventId")
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "EVENT_FILE_LOADED")
                        _playbackState.value = Player.PlaybackState.READY
                        refreshTracks()

                        if (pendingMediaState?.startPositionMs ?: 0 > 0) {
                            seekTo(pendingMediaState!!.startPositionMs)
                        }
                    }

                    MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                        Log.d(TAG, "EVENT_END_FILE")
                        _playbackState.value = Player.PlaybackState.ENDED
                        _isPlaying.value = false
                    }

                    MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                        Log.d(TAG, "EVENT_PLAYBACK_RESTART")
                        _playbackState.value = Player.PlaybackState.READY
                        _isPlaying.value = true
                    }

                    MPV.mpvEvent.MPV_EVENT_SEEK -> {
                        Log.d(TAG, "EVENT_SEEK")
                        _playbackState.value = Player.PlaybackState.BUFFERING
                    }

                    MPV.mpvEvent.MPV_EVENT_SHUTDOWN -> {
                        Log.d(TAG, "EVENT_SHUTDOWN")
                        _playbackState.value = Player.PlaybackState.IDLE
                        _isPlaying.value = false
                    }
                }
            }
        })
    }

    private fun handlePropertyChange(property: String) {
        when (property) {
            "time-pos" -> {
                val posSeconds = mpv.getPropertyDouble("time-pos") ?: 0.0
                _currentPosition.value = (posSeconds * 1000).toLong()
            }

            "duration" -> {
                val durSeconds = mpv.getPropertyDouble("duration") ?: 0.0
                _duration.value = (durSeconds * 1000).toLong()
            }

            "pause" -> {
                val isPaused = mpv.getPropertyBoolean("pause") ?: true
                _isPlaying.value = !isPaused
            }

            "volume" -> {
                _volume.value = mpv.getPropertyInt("volume") ?: 100
            }

            "speed" -> {
                _playbackSpeed.value = (mpv.getPropertyDouble("speed") ?: 1.0).toFloat()
            }

            "track-list" -> {
                refreshTracks()
            }

            "eof-reached" -> {
                val eof = mpv.getPropertyBoolean("eof-reached") ?: false
                if (eof) {
                    _playbackState.value = Player.PlaybackState.ENDED
                }
            }
        }
    }

    private fun refreshTracks() {
        val trackListNode = mpv.getPropertyNode("track-list") ?: return
        val (audio, subtitle, video) = TrackParser.parseTrackList(trackListNode)

        _audioTracks.value = audio
        _subtitleTracks.value = subtitle
        _videoTracks.value = video

        if (audio.isNotEmpty()) {
            val currentAid = mpv.getPropertyInt("aid") ?: 0
            if (currentAid == 0) {
                // Anyways it skips this cause the bitrate is low (just a patch). Ill have to force it to select default(when master playlist provides) , choose a higher bitrate or use option from after getting audiotracks(needs adjusting).
                val selected = audio.firstOrNull {
                    it.language?.lowercase() == "ja"
                } ?: audio.first()

                Log.d(TAG, "Auto-selecting: ${selected.name ?: selected.id}")
                mpv.setPropertyInt("aid", selected.id)
            }
        }
    }

    override fun init(surfaceHolder: Any) {
        if (isInitialized) {
            Log.w(TAG, "Player already initialized")
            return
        }

        Log.d(TAG, "init() called")
        val configDir = File(context.filesDir, "mpv_config").absolutePath
        val cacheDir = context.cacheDir.absolutePath
        File(configDir).mkdirs()

        initialize(configDir, cacheDir)
        isInitialized = true
        Log.d(TAG, "Player initialized successfully")
    }

    override fun release() {
        if (!isInitialized) return

        Log.d(TAG, "release() called")
        _isPlaying.value = false
        _playbackState.value = Player.PlaybackState.IDLE
        _currentPosition.value = 0L
        _duration.value = 0L

        destroy()
        isInitialized = false
        surfaceReady = false
    }

    override fun play() {
        if (!isInitialized) {
            Log.w(TAG, "play() called but player not initialized")
            return
        }
        Log.d(TAG, "play() called")
        mpv.command("set", "pause", "no")
    }

    override fun pause() {
        if (!isInitialized) return
        Log.d(TAG, "pause() called")
        mpv.command("set", "pause", "yes")
        _isPlaying.value = false
        _playbackState.value = Player.PlaybackState.READY
    }

    override fun seekTo(positionMs: Long) {
        if (!isInitialized) return
        if (positionMs < 0) return
        val seconds = positionMs / 1000.0
        Log.d(TAG, "seekTo($positionMs ms = $seconds seconds)")
        mpv.command("seek", seconds.toString(), "absolute")
    }

    override fun stop() {
        if (!isInitialized) return
        Log.d(TAG, "stop() called")
        mpv.command("stop")
        _isPlaying.value = false
        _playbackState.value = Player.PlaybackState.IDLE
    }

    override fun loadMedia(
        videoUrl: String,
        headers: Map<String, String>,
        startPositionMs: Long,
        audioTracks: List<ExternalAudio>,
        subtitles: List<ExternalSubtitle>
    ) {
        Log.d(
            TAG,
            "loadMedia() called - initialized=$isInitialized, surfaceReady=$surfaceReady, url=$videoUrl"
        )

        val mediaState =
            PendingMediaState(videoUrl, headers, startPositionMs, audioTracks, subtitles)
        pendingMediaState = mediaState


        if (!isInitialized || !surfaceReady) {
            Log.d(TAG, "Deferring media load until surface is ready")
            return
        }
/// iguess its ready to give picture
        loadMediaInternal(mediaState)
    }

    private fun loadMediaInternal(mediaState: PendingMediaState) {
        Log.d(TAG, "loadMediaInternal() - loading: ${mediaState.videoUrl}")
        _playbackState.value = Player.PlaybackState.BUFFERING

        // Set headers if needed
        /// might need to verify tls using the bundled certs or maybe it does it automatically ///
        if (mediaState.headers.isNotEmpty() &&
            (mediaState.videoUrl.startsWith("http") || mediaState.videoUrl.startsWith("https"))
        ) {
            val headerStr =
                mediaState.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
            mpv.setPropertyString("http-header-fields", headerStr)
            Log.d(TAG, "HTTP headers set: ${mediaState.headers.keys}")
        }

        // Load the file
        Log.d(TAG, "Executing: loadfile ${mediaState.videoUrl}")
        mpv.command("loadfile", mediaState.videoUrl, "replace")

        // Add external audio tracks
        mediaState.audioTracks.forEach { audio ->
            val cmd = mutableListOf("audio-add", audio.url, "select")
            if (!audio.label.isNullOrBlank()) cmd.add(audio.label)
            if (!audio.language.isNullOrBlank()) cmd.add(audio.language)
            Log.d(TAG, "Adding audio track: ${audio.url}")
            mpv.command(*cmd.toTypedArray())
        }

        // Add external subtitles
        mediaState.subtitles.forEach { sub ->
            val cmd = mutableListOf("sub-add", sub.url, "select")
            if (!sub.label.isNullOrBlank()) cmd.add(sub.label)
            if (!sub.language.isNullOrBlank()) cmd.add(sub.language)
            Log.d(TAG, "Adding subtitle: ${sub.url}")
            mpv.command(*cmd.toTypedArray())
        }
    }

    override fun selectAudioTrack(trackId: Int) {
        if (!isInitialized) return
        if (trackId == -1) mpv.setPropertyString("aid", "no") else mpv.setPropertyInt(
            "aid",
            trackId
        )
    }

    override fun setExternalAudioTrack(url: String, headers: Map<String, String>) {
        if (!isInitialized) return
        mpv.command("audio-add", url, "select")
    }

    override fun selectSubtitleTrack(trackId: Int) {
        if (!isInitialized) return
        if (trackId == -1) mpv.setPropertyString("sid", "no") else mpv.setPropertyInt(
            "sid",
            trackId
        )
    }

    override fun setExternalSubtitles(subtitles: List<ExternalSubtitle>) {
        if (!isInitialized) return
        subtitles.forEach { sub -> mpv.command("sub-add", sub.url) }
    }

    override fun selectVideoTrack(trackId: Int) {
        if (!isInitialized) return
        if (trackId == -1) mpv.setPropertyString("vid", "no") else mpv.setPropertyInt(
            "vid",
            trackId
        )
    }

    override fun setVolume(level: Int) {
        if (!isInitialized) return
        val clamped = level.coerceIn(0, 200)
        mpv.setPropertyInt("volume", clamped)
        _volume.value = clamped
    }

    override fun increaseVolume(step: Int) = setVolume(_volume.value + step)
    override fun decreaseVolume(step: Int) = setVolume(_volume.value - step)

    override fun setAudioChannel(channel: Player.AudioChannel) {
        if (!isInitialized) return
        mpv.setPropertyString("audio-channels", channel.value)
        _audioChannel.value = channel
    }

    override fun setVideoScaleMode(mode: Player.VideoScaleMode) {
        if (!isInitialized) return
        val value = when (mode) {
            Player.VideoScaleMode.FIT -> "-1"
            Player.VideoScaleMode.STRETCH -> "0"
            Player.VideoScaleMode.CROP -> "-1"
            Player.VideoScaleMode.ZOOM -> "1.1"
            Player.VideoScaleMode.ORIGINAL -> "0"
        }
        mpv.setPropertyString("video-aspect-override", value)
        _videoScaleMode.value = mode
    }

    override fun setDecoder(decoder: Player.Decoder) {
        if (!isInitialized) return
        mpv.setPropertyString("hwdec", decoder.value)
        _currentDecoder.value = decoder
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (!isInitialized) return
        val clamped = speed.coerceIn(0.25f, 4.0f)
        mpv.setPropertyDouble("speed", clamped.toDouble())
        _playbackSpeed.value = clamped
    }

    override fun command(vararg args: String) {
        if (!isInitialized) return
        mpv.command(*args)
    }

    // Needs improvement though prepsurface and make sure its ready then command play
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated() called")
        super.surfaceCreated(holder)
        surfaceReady = true
        Log.d(TAG, "Surface is now ready for playback")

        // Load pending media if it exists
        val pending = pendingMediaState
        if (pending != null && isInitialized) {
            Log.d(TAG, "Loading pending media: ${pending.videoUrl}")
            loadMediaInternal(pending)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed() called")
        surfaceReady = false
        super.surfaceDestroyed(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged() - format=$format, w=$width, h=$height")
        super.surfaceChanged(holder, format, width, height)
    }
}
// investigate these
//mpv_get_property(time-pos) format 5 was unavailable
//2026-05-26 15:23:54.677 18499-22918 mpv                     ani.saikou.beta                      V  mpv_get_property(duration) format 5 was unavailable
//2026-05-26 15:23:54.678 18499-22918 mpv                     ani.saikou.beta                      V  mpv_get_property(eof-reached) format 3 was unavailable