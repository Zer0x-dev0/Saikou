package ani.saikou.media.anime.mpv

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.connections.anilist.Anilist
import ani.saikou.connections.discord.WebSocketRPC
import ani.saikou.connections.updateProgress
import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.media.MediaDetailsViewModel
import ani.saikou.media.anime.Episode
import ani.saikou.media.anime.mpv.PlayerRepository.SkipInterval
import ani.saikou.parsers.AnimeSources
import ani.saikou.parsers.HAnimeSources
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoExtractor
import ani.saikou.saveData
import ani.saikou.settings.PlayerSettings
import ani.saikou.snackString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class PlayerViewModel(application: Application) : AndroidViewModel(application) {


    private var _playerRef: WeakReference<MpvVideoPlayer>? = null
    val player: MpvVideoPlayer? get() = _playerRef?.get()

    val isPlayerAttached: Boolean
        get() {
            val p = player ?: return false
            return p.isInitialized && p.surfaceReady
        }

    private val repository = PlayerRepository()
    private val stateJobs = mutableListOf<Job>()
    private var timestampCollectionJob: Job? = null


    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val videoTracks: StateFlow<List<VideoTrack>> = _videoTracks.asStateFlow()

    private val loadingAudioTrack =
        AudioTrack(id = 0, name = "Loading Audio...", language = "unknown")
    private val loadingVideoTrack = VideoTrack(id = 0, name = "Loading Video", resolution = null)

    private val _currentAudioTrack = MutableStateFlow(loadingAudioTrack)
    val currentAudioTrack: StateFlow<AudioTrack> = _currentAudioTrack.asStateFlow()

    private val defaultSubtitleTrack = SubtitleTrack(id = -1, name = "None/Off", language = "")
    private val _currentSubtitleTrack = MutableStateFlow(defaultSubtitleTrack)
    val currentSubtitleTrack: StateFlow<SubtitleTrack> = _currentSubtitleTrack.asStateFlow()

    private val _currentVideoTrack = MutableStateFlow(loadingVideoTrack)
    val currentVideoTrack: StateFlow<VideoTrack> = _currentVideoTrack.asStateFlow()

    private val _videoScaleMode = MutableStateFlow(VideoScaleMode.FIT)
    val videoScaleMode: StateFlow<VideoScaleMode> = _videoScaleMode.asStateFlow()

    private val _currentDecoder = MutableStateFlow(Decoder.Auto)
    val currentDecoder: StateFlow<Decoder> = _currentDecoder.asStateFlow()

    private val _audioChannel = MutableStateFlow(AudioChannels.Auto)
    val audioChannel: StateFlow<AudioChannels> = _audioChannel.asStateFlow()

    private val _bufferCacheDuration = MutableStateFlow(0L)
    val bufferCacheDuration: StateFlow<Long> = _bufferCacheDuration.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerEpisodeUiState())
    val uiState: StateFlow<PlayerEpisodeUiState> = _uiState.asStateFlow()

    private val _skipStamps = MutableStateFlow<List<SkipInterval>>(emptyList())
    val skipStamps: StateFlow<List<SkipInterval>> = _skipStamps.asStateFlow()

    var isTimeStampsLoaded = false; private set
    var currentEpisodeIndex = 0; private set

    private var media: Media? = null
    private var currentEpisode: Episode? = null
    private var episodeArr: List<String> = emptyList()
    private var episodes: Map<String, Episode> = emptyMap()

    private var extractor: VideoExtractor? = null
    private var video: Video? = null

    private var subtitleOverride: Subtitle? = null
    private var loadedMediaKey: String? = null
    var settings = PlayerSettings(); private set

    private var pendingStartPositionMs: Long = 0L

    private val _isDialogShowing = MutableStateFlow(false)
    val isDialogShowing: StateFlow<Boolean> = _isDialogShowing.asStateFlow()

    private val discordRPC: WebSocketRPC by lazy { WebSocketRPC(getApplication()) }

    private val _attachedPlayerView = MutableStateFlow<MpvVideoPlayer?>(null)
    val attachedPlayerView: StateFlow<MpvVideoPlayer?> = _attachedPlayerView.asStateFlow()

    private var _playbackServiceRef: WeakReference<PlaybackService>? = null
    private val playbackService: PlaybackService? get() = _playbackServiceRef?.get()

    private var boundMediaDetailsModel: WeakReference<MediaDetailsViewModel>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as PlaybackService.LocalBinder).service
            _playbackServiceRef = WeakReference(service)
            service.setSessionActive(true)

            val mediaDetailsModel = boundMediaDetailsModel?.get()
            if (mediaDetailsModel == null) {
                Log.w("mpv", "Service requested mediaDetailsModel is null")
                return
            }
            setPlayerInstance(service.player)
            service.onNext = {
                Log.d("mpv", "Service requested next episode")
            }
            service.onPrev = {
                Log.d("mpv", "Service requested previous episode")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _playbackServiceRef?.clear()
            _playbackServiceRef = null
        }
    }


    fun bindService(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        boundMediaDetailsModel = WeakReference(mediaDetailsModel)

        val intent = Intent(activity, PlaybackService::class.java)
        activity.startService(intent)
        activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }


    fun unbindService(context: Context) {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {

        }
        boundMediaDetailsModel?.clear()
        boundMediaDetailsModel = null
    }

    private fun buildRPCConfig(): WebSocketRPC.RPCConfig {
        val currentMedia = media
        val ep = currentEpisode

        if (currentMedia == null || ep == null) {
            return WebSocketRPC.RPCConfig(
                title = "Saikou",
                episode = "?",
                episodeTitle = "loading peak",
                totalEpisodes = "searching",
                coverUrl = null,
                shareLink ="https://anilist.co/anime/116674"
            )
        }

        return WebSocketRPC.RPCConfig(
            title = currentMedia.userPreferredName ?: currentMedia.nameRomaji ?: currentMedia.name
            ?: "Unknown Anime",
            episode = ep.number,
            episodeTitle = ep.title?.takeIf { it.isNotBlank() && it != "null" },
            totalEpisodes = currentMedia.anime?.totalEpisodes?.toString(),
            coverUrl = currentMedia.banner ?: currentMedia.cover,
            shareLink = currentMedia.shareLink ?: "https://anilist.co/anime/${currentMedia.id}",
            episodeThumbnail = ep.thumb?.url
        )
    }

    fun setDialogShowing(showing: Boolean) {
        _isDialogShowing.value = showing
    }

    fun setPlayerInstance(playerInstance: MpvVideoPlayer) {
        clearStateJobs()
        _playerRef = WeakReference(playerInstance)
        _attachedPlayerView.value = playerInstance

        discordRPC.connect()

        stateJobs += viewModelScope.launch {
            playerInstance.playbackState.collect { state ->
                _playbackState.value = state
                if (state == PlaybackState.BUFFERING || state == PlaybackState.IDLE || state == PlaybackState.ENDED) {
                    return@collect
                }
                val playingNow = state == PlaybackState.PLAYING
                discordRPC.onPlaybackChanged(playingNow, playerInstance.currentPosition.value)
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.isPlaying.collect { isPlaying ->
                _isPlaying.value = isPlaying
                playbackService?.publishState(
                    if (isPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                    else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
                )
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentPosition.collect {
                _currentPosition.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.duration.collect {
                _duration.value = it
            }
        }
        stateJobs += viewModelScope.launch { playerInstance.volume.collect { _volume.value = it } }
        stateJobs += viewModelScope.launch {
            playerInstance.playbackSpeed.collect {
                _playbackSpeed.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.audioTracks.collect {
                _audioTracks.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentAudioTrack.collect {
                _currentAudioTrack.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.subtitleTracks.collect {
                _subtitleTracks.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentSubtitleTrack.collect {
                _currentSubtitleTrack.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.videoTracks.collect {
                _videoTracks.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentVideoTrack.collect {
                _currentVideoTrack.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.videoScaleMode.collect {
                _videoScaleMode.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.currentDecoder.collect {
                _currentDecoder.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.audioChannel.collect {
                _audioChannel.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.bufferCacheDuration.collect {
                _bufferCacheDuration.value = it
            }
        }
        stateJobs += viewModelScope.launch {
            playerInstance.duration.collect { dur ->
                _duration.value = dur
                if (dur > 0L) {
                    var resolvedPosition = currentPosition.value
                    if (pendingStartPositionMs > 0L) {
                        val safeTarget = pendingStartPositionMs
                        pendingStartPositionMs = 0L
                        val remainingMs = dur - safeTarget

                        if (remainingMs >= 60_000L) {
                            resolvedPosition = safeTarget
                        } else {
                            seekTo(0L)
                            resolvedPosition = 0L
                        }
                    }

                    discordRPC.onDurationReady(buildRPCConfig(), dur, resolvedPosition)

                    if (!isTimeStampsLoaded && settings.timeStampsEnabled) {
                        val epNum = currentEpisode?.number?.trim()?.toIntOrNull() ?: return@collect
                        isTimeStampsLoaded = true
                        val currentMedia = media ?: return@collect
                        loadSkipTimes(currentMedia, epNum, dur)
                    }
                }
            }
        }
    }

    fun onSurfaceReady(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        val targetEpisode =
            mediaDetailsModel.getEpisode().value ?: episodeArr.getOrNull(currentEpisodeIndex)
                ?.let { episodes[it] }
        if (targetEpisode != null) {
            loadResolvedEpisode(targetEpisode, activity, mediaDetailsModel)
        }
    }

    fun initializeManager(
        extractedMedia: Media,
        initialSubtitle: Subtitle? = null,
        mediaDetailsModel: MediaDetailsViewModel
    ) {
        media = extractedMedia
        subtitleOverride = initialSubtitle
        settings = loadData("player_settings") ?: PlayerSettings()
        mediaDetailsModel.watchSources = if (extractedMedia.isAdult) HAnimeSources else AnimeSources

        episodes = extractedMedia.anime?.episodes ?: emptyMap()
        episodeArr = episodes.keys.toList()
        currentEpisodeIndex =
            episodeArr.indexOf(extractedMedia.anime?.selectedEpisode).coerceAtLeast(0)

        mediaDetailsModel.setMedia(extractedMedia)
        publishUi()
    }

    fun setInitialEpisode(mediaDetailsModel: MediaDetailsViewModel) {
        episodeArr.getOrNull(currentEpisodeIndex)?.let { episodes[it] }?.let {
            mediaDetailsModel.setEpisode(it, "mpv-init")
        }
    }

    fun handleNextEpisodeClick(
        activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel
    ) {
        if (currentEpisodeIndex + 1 < episodeArr.size) {
            updateAnimeProgress()
            openEpisodeSelector(currentEpisodeIndex + 1, activity, mediaDetailsModel)
        } else {
            snackString("This is the last Episode!")
        }
    }

    fun previousEpisode(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        if (currentEpisodeIndex > 0) {
            openEpisodeSelector(currentEpisodeIndex - 1, activity, mediaDetailsModel)
        } else {
            snackString("This is the first Episode!")
        }
    }

    fun handleSourceClick(activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel) {
        val currentMedia = media ?: return
        val selected = currentMedia.selected ?: return
        val episodeNumber = episodeArr.getOrNull(currentEpisodeIndex) ?: return

        saveCurrentPosition()
        selected.server = null
        mediaDetailsModel.saveSelected(currentMedia.id, selected, activity)
        mediaDetailsModel.onEpisodeClick(
            currentMedia, episodeNumber, activity.supportFragmentManager, launch = false
        )
    }

    fun loadResolvedEpisode(
        ep: Episode, activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel
    ) {
        val currentMedia = media ?: return
        val targetIndex = episodeArr.indexOf(ep.number)
        if (targetIndex == -1) return

        val preferredServer = mediaDetailsModel.loadSelected(currentMedia).server
        val resolvedExtractor = ep.extractors?.find { it.server.name == ep.selectedExtractor }
            ?: ep.extractors?.find { it.server.name == preferredServer } ?: return

        val resolvedVideo =
            ep.selectedVideo.let { resolvedExtractor.videos.getOrNull(it) } ?: return

        val newMediaKey =
            "${ep.number}|${resolvedExtractor.server.name}|${ep.selectedVideo}|${resolvedVideo.file.url}|${ep.selectedSubtitle}"
        if (newMediaKey == loadedMediaKey) {
            publishUi()
            return
        }

        currentEpisodeIndex = targetIndex
        currentEpisode = ep
        currentMedia.anime?.selectedEpisode = ep.number
        mediaDetailsModel.setMedia(currentMedia)

        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _currentAudioTrack.value = loadingAudioTrack
        _currentSubtitleTrack.value = defaultSubtitleTrack
        _currentVideoTrack.value = loadingVideoTrack

        _skipStamps.value = emptyList()
        isTimeStampsLoaded = false
        _currentPosition.value = 0L
        _duration.value = 0L

        viewModelScope.launch(Dispatchers.IO) {
            extractor?.onVideoStopped(video)
            extractor = resolvedExtractor
            video = resolvedVideo

            resolvedExtractor.onVideoPlayed(resolvedVideo)
            saveContinueState(currentMedia, ep)

            val startPosition = loadData<Long>("${currentMedia.id}_${ep.number}", activity) ?: 0L
            val headers = resolvedVideo.file.headers
            val externalAudio = resolvedExtractor.audioTracks.map {
                ExternalAudio(
                    it.url, language = it.language, headers = headers
                )
            }
            val externalSubs = resolvedExtractor.subtitles.map { sub ->
                ExternalSubtitle(
                    url = sub.file.url,
                    headers = sub.headers ?: sub.file.headers ?: emptyMap(),
                    language = sub.language ?: "und"
                )
            }

            pendingStartPositionMs = startPosition
            val loadSucceeded = loadMedia(
                resolvedVideo.file.url, headers, startPosition, externalAudio, externalSubs
            )

            if (loadSucceeded) {
                loadedMediaKey = newMediaKey
            } else {
                pendingStartPositionMs = 0L
            }

            launch(Dispatchers.Main) {
                publishUi()
                if (loadSucceeded) {
                    play()
                    discordRPC.updateEpisode(buildRPCConfig(), isCurrentlyPlaying = true)
                    playbackService?.updateMetadata(
                        title = currentMedia.userPreferredName ?: currentMedia.nameRomaji
                        ?: currentMedia.name ?: "Unknown",
                        subtitle = "Episode ${ep.number}",
                        durationMs = _duration.value
                    )
                }
            }
        }
    }

    fun loadSkipTimes(media: Media, episodeNumber: Int, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (settings.useAlternativeTimestampProvider) {
                repository.fetchSkipTimes(media.id, episodeNumber, durationMs)
            } else {
                media.idMAL?.let { malId ->
                    repository.fetchAniSkipTimes(
                        malId, episodeNumber, durationMs / 1000
                    )
                }
            }

            if (!result.isNullOrEmpty()) {
                _skipStamps.value = result
            } else {
                isTimeStampsLoaded = false
            }
        }
    }

    fun saveCurrentPosition() {
        val currentMedia = media ?: return
        val episodeNumber = currentEpisode?.number ?: currentMedia.anime?.selectedEpisode ?: return
        saveData("${currentMedia.id}_${episodeNumber}", currentPosition.value, getApplication())
    }

    fun release(mediaDetailsModel: MediaDetailsViewModel) {
        val currentMedia = media
        val fallbackEpisode = currentEpisode?.number ?: currentMedia?.anime?.selectedEpisode
        if (currentMedia != null && fallbackEpisode != null) {
            currentMedia.anime?.selectedEpisode = fallbackEpisode
            mediaDetailsModel.setMedia(currentMedia)
        }
        viewModelScope.launch(Dispatchers.IO) { extractor?.onVideoStopped(video) }
    }

    private fun openEpisodeSelector(
        index: Int, activity: AppCompatActivity, mediaDetailsModel: MediaDetailsViewModel
    ) {
        val currentMedia = media ?: return
        if (index !in episodeArr.indices) return

        saveCurrentPosition()
        val targetEpisodeNumber = episodeArr[index]

        mediaDetailsModel.epChanged.postValue(false)
        mediaDetailsModel.onEpisodeClick(
            currentMedia, targetEpisodeNumber, activity.supportFragmentManager, launch = false
        )
        pause()
    }

    private fun saveContinueState(currentMedia: Media, ep: Episode) {
        saveData("${currentMedia.id}_current_ep", ep.number, getApplication())
        val continueSet =
            loadData<MutableSet<Int>>("continue_ANIME", getApplication()) ?: mutableSetOf()
        continueSet.remove(currentMedia.id)
        continueSet.add(currentMedia.id)
        saveData("continue_ANIME", continueSet, getApplication())
    }

    private fun publishUi() {
        val current =
            currentEpisode ?: episodeArr.getOrNull(currentEpisodeIndex)?.let { episodes[it] }
        val builtEpisodeTitle = current?.let { ep ->
            val prefix = "Episode ${ep.number}"
            val fillerText = if (ep.filler) " [Filler]" else ""
            val titleText =
                if (!ep.title.isNullOrBlank() && ep.title != "null") " : ${ep.title}" else ""
            "$prefix$fillerText$titleText"
        }.orEmpty()

        _uiState.value = PlayerEpisodeUiState(
            mainTitle = media?.let { it.userPreferredName ?: it.nameRomaji ?: it.name }
                ?: "Unknown",
            episodeTitle = builtEpisodeTitle,
            episodeTitles = episodeArr.mapNotNull { episodes[it]?.number },
            currentEpisodeIndex = currentEpisodeIndex,
            hasNextEpisode = currentEpisodeIndex + 1 < episodeArr.size,
            hasPreviousEpisode = currentEpisodeIndex > 0
        )
    }

    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        audioTracks: List<ExternalAudio> = emptyList(),
        subtitles: List<ExternalSubtitle> = emptyList()
    ): Boolean {
        val playerInstance = player ?: return false
        playerInstance.loadMedia(videoUrl, headers, startPositionMs, audioTracks, subtitles)
        return true
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        saveCurrentPosition(); player?.pause()
    }

    fun stop() {
        saveCurrentPosition(); player?.stop()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun selectSubtitleTrack(trackId: Int) {
        player?.selectSubtitleTrack(trackId)
    }

    fun selectAudioTrack(trackId: Int) {
        player?.selectAudioTrack(trackId)
    }

    fun selectVideoTrack(trackId: Int) {
        player?.selectVideoTrack(trackId)
    }

    fun setDecoder(decoder: Decoder) {
        player?.setDecoder(decoder)
    }

    fun setAudioChannel(channel: AudioChannels) {
        player?.setAudioChannel(channel)
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        player?.setVideoScaleMode(mode); _videoScaleMode.value = mode
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    fun setExternalSubtitles(subs: List<ExternalSubtitle>) {
        player?.setExternalSubtitles(subs)
    }

    fun setVolume(level: Int) {
        playbackService?.setSystemVolume(level)
        player?.setVolume(level)
        _volume.value = level
    }

    fun detachPlayerState() {
        clearStateJobs()
        timestampCollectionJob?.cancel()
        _playerRef?.clear()
        _playerRef = null
        _attachedPlayerView.value = null
    }

    fun exitPlayback() {
        saveCurrentPosition()
        updateAnimeProgress()
        playbackService?.teardown()
        _playbackServiceRef?.clear()
        _playbackServiceRef = null
    }

    fun releasePlayer(mediaDetailsModel: MediaDetailsViewModel) {
        clearStateJobs()
        timestampCollectionJob?.cancel()
        release(mediaDetailsModel)
        discordRPC.close()

        _playbackState.value = PlaybackState.ENDED
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _videoTracks.value = emptyList()
        _currentAudioTrack.value = loadingAudioTrack
        _currentVideoTrack.value = loadingVideoTrack
        _currentSubtitleTrack.value = defaultSubtitleTrack
        _skipStamps.value = emptyList()
        isTimeStampsLoaded = false
        pendingStartPositionMs = 0L
        _playerRef?.clear()
        _playerRef = null
        _attachedPlayerView.value = null
    }

    private fun clearStateJobs() {
        stateJobs.forEach { it.cancel() }
        stateJobs.clear()
    }

    private fun updateAnimeProgress() {
        val dur = duration.value
        if (dur <= 0L) return

        val ratio = currentPosition.value.toDouble() / dur.toDouble()
        if (ratio <= settings.watchPercentage || Anilist.userid == null) return

        val currentMedia = media ?: return
        val epNumber = (currentEpisode ?: episodeArr.getOrNull(currentEpisodeIndex)
            ?.let { episodes[it] })?.number ?: return

        if (loadData<Boolean>("${media?.id}_save_progress") != false && if (media?.isAdult == true) settings.updateForH else true) {
            updateProgress(currentMedia, epNumber)
        }
    }

    fun destroyViewModel(mediaDetailsModel: MediaDetailsViewModel) {
        release(mediaDetailsModel)
        detachPlayerState()
    }
}