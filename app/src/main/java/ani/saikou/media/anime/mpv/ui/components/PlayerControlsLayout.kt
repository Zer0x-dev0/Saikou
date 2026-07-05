package ani.saikou.media.anime.mpv.ui.components


import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.media.anime.mpv.PlaybackState
import ani.saikou.media.anime.mpv.PlayerEpisodeUiState
import ani.saikou.media.anime.mpv.PlayerScreenActions
import ani.saikou.media.anime.mpv.PlayerViewModel
import ani.saikou.media.anime.mpv.VideoScaleMode
import ani.saikou.media.anime.mpv.ui.components.gestures.PlayerGestures
import ani.saikou.media.anime.mpv.ui.components.gestures.SeekDirection
import ani.saikou.media.anime.mpv.ui.components.gestures.SeekEffectOverlay
import ani.saikou.media.anime.mpv.ui.components.controls.BottomPlayerControls
import ani.saikou.media.anime.mpv.ui.components.controls.CenterControls
import ani.saikou.media.anime.mpv.ui.components.sheets.DecoderSettingsSheet
import ani.saikou.media.anime.mpv.ui.components.controls.TopControls
import ani.saikou.media.anime.mpv.ui.components.controls.VerticalSlider
import ani.saikou.media.anime.mpv.ui.components.sheets.AudioTracksSheet
import ani.saikou.media.anime.mpv.ui.components.sheets.SubtitlesTracksSheet
import ani.saikou.media.anime.mpv.ui.components.sheets.VideoTracksSheet
import ani.saikou.others.AniSkip.getType
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerControlsLayout(
    viewModel: PlayerViewModel,
    episodeUi: PlayerEpisodeUiState,
    isControlsLocked: Boolean,
    onLockChanged: (Boolean) -> Unit,
    actions: PlayerScreenActions,
    activity: AppCompatActivity?
) {


    val playbackState by viewModel.playbackState.collectAsState()
    val durationMs by viewModel.duration.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val videoTracks by viewModel.videoTracks.collectAsState()
    val currentVideoTrack by viewModel.currentVideoTrack.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val currentAudioTrack by viewModel.currentAudioTrack.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val currentSubtitleTrack by viewModel.currentSubtitleTrack.collectAsState()
    val currentDecoder by viewModel.currentDecoder.collectAsState()
    val currentAudioChannel by viewModel.audioChannel.collectAsState()
    val bufferCacheDuration by viewModel.bufferCacheDuration.collectAsState()
    val currentScaleMode by viewModel.videoScaleMode.collectAsState()
    val aniSkipStamps by viewModel.skipStamps.collectAsState()
    val currentEnginePos by viewModel.currentPosition.collectAsState()

    var isControlsVisible by remember { mutableStateOf(true) }
    var controlsHideResetToken by remember { mutableLongStateOf(0L) }
    var showAudioTracksSheet by remember { mutableStateOf(false) }
    var showVideoTracksSheet by remember { mutableStateOf(false) }
    var showSubtitleTracksSheet by remember { mutableStateOf(false) }
    var openSheetState by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }


    var activeSeekDirection by remember { mutableStateOf(SeekDirection.NONE) }
    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }
    var seekDebounceJobToken by remember { mutableLongStateOf(0L) }
    var manualSkipOffsetMs by remember { mutableLongStateOf(0L) }
    var is2xActive by remember { mutableStateOf(false) }
    var sliderDismissToken by remember { mutableLongStateOf(0L) }
    var isFirstVolumeCollect by remember { mutableStateOf(true) }


    val isBuffering = playbackState == PlaybackState.BUFFERING
    val activelyPlaying = playbackState == PlaybackState.PLAYING
    val areYouReadyToRumble = remember(playbackState) {
        playbackState == PlaybackState.BUFFERING || playbackState == PlaybackState.IDLE
    }

    var brightnessSliderValue by remember {
        val initialBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
        val validBrightness = if (initialBrightness < 0f) 0.5f else initialBrightness
        mutableFloatStateOf(validBrightness * 10f)
    }

    val triggerCompoundingSeek: (SeekDirection) -> Unit = remember(activeSeekDirection) {
        { direction ->
            if (direction != SeekDirection.NONE) {
                if (activeSeekDirection != direction) {
                    accumulatedSeekSeconds = viewModel.settings.seekTime
                    activeSeekDirection = direction
                } else {
                    accumulatedSeekSeconds += viewModel.settings.seekTime
                }
                isControlsVisible = false
                seekDebounceJobToken = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(currentEnginePos) {
        manualSkipOffsetMs = 0L
    }

    LaunchedEffect(seekDebounceJobToken) {
        if (seekDebounceJobToken > 0L) {
            delay(650.milliseconds)

            val totalSeekMs = accumulatedSeekSeconds * 1000L
            val currentPos = viewModel.currentPosition.value

            if (activeSeekDirection == SeekDirection.RIGHT) {
                viewModel.seekTo((currentPos + totalSeekMs).coerceAtMost(durationMs))
            } else if (activeSeekDirection == SeekDirection.LEFT) {
                viewModel.seekTo((currentPos - totalSeekMs).coerceAtLeast(0))
            }

            accumulatedSeekSeconds = 0
            activeSeekDirection = SeekDirection.NONE
            seekDebounceJobToken = 0L
        }
    }


    val resetControlsVisibilityTimer: () -> Unit = {
        isControlsVisible = true
        controlsHideResetToken = System.currentTimeMillis()
    }
    LaunchedEffect(
        controlsHideResetToken,
        activelyPlaying,
        isControlsVisible,
        isControlsLocked,
        isBuffering
    ) {
        if (isBuffering) {
            isControlsVisible = true
            return@LaunchedEffect
        }
        if (isControlsVisible && activelyPlaying && !isControlsLocked) {
            delay(5000.milliseconds)
            isControlsVisible = false
        }
    }

    LaunchedEffect(volume) {
        if (isFirstVolumeCollect) {
            isFirstVolumeCollect = false
            return@LaunchedEffect
        }

        showVolumeSlider = true

        sliderDismissToken = System.currentTimeMillis()
    }
    LaunchedEffect(sliderDismissToken) {
        if (sliderDismissToken > 0L) {
            delay(1300.milliseconds)
            showVolumeSlider = false
            showBrightnessSlider = false
            sliderDismissToken = 0L
        }
    }

    val displayPositionMs by remember(
        currentEnginePos,
        activeSeekDirection,
        accumulatedSeekSeconds,
        manualSkipOffsetMs,
        durationMs
    ) {
        derivedStateOf {
            if (activeSeekDirection != SeekDirection.NONE) {
                val totalSeekMs = accumulatedSeekSeconds * 1000L
                if (activeSeekDirection == SeekDirection.RIGHT) {
                    (currentEnginePos + totalSeekMs).coerceAtMost(durationMs)
                } else {
                    (currentEnginePos - totalSeekMs).coerceAtLeast(0L)
                }
            } else if (manualSkipOffsetMs != 0L) {
                (currentEnginePos + manualSkipOffsetMs).coerceIn(0L, durationMs)
            } else {
                currentEnginePos
            }
        }
    }

    // Skip timestamps
    val activeAniSkipStamp by remember {
        derivedStateOf {
            val currentPosMs = currentEnginePos
            aniSkipStamps.find { stamp ->
                currentPosMs >= stamp.startTimeMs && currentPosMs < stamp.endTimeMs
            }
        }
    }
    //  AUTO-SKIP
    LaunchedEffect(activeAniSkipStamp) {
        val stamp = activeAniSkipStamp ?: return@LaunchedEffect
        val isSkippableType = stamp.type == "Opening" || stamp.type == "Ending"

        if (viewModel.settings.autoSkipOPED && isSkippableType) {
            val currentMs = viewModel.currentPosition.value
            val targetMs = stamp.endTimeMs

            manualSkipOffsetMs = targetMs - currentMs
            viewModel.seekTo(targetMs)
        }
    }

    val scrimColor by animateColorAsState(
        targetValue = if (isControlsVisible && !isControlsLocked && !is2xActive) Color.Black.copy(
            alpha = 0.4f
        ) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "ControlsScrimAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (scrimColor != Color.Transparent) {
                    drawRect(scrimColor)
                }
            }
            .clickable(onClick = { isControlsVisible = !isControlsVisible })
    ) {
        SeekEffectOverlay(
            direction = activeSeekDirection,
            onAnimationFinished = {},
            seekSeconds = if (accumulatedSeekSeconds > 0) accumulatedSeekSeconds else 10
        )

        PlayerGestures(
            isControlsLocked = isControlsLocked,
            onSingleTap = { isControlsVisible = !isControlsVisible },
            isGesturesEnabled = viewModel.settings.gestures,
            onDoubleTapLeft = { triggerCompoundingSeek(SeekDirection.LEFT) },
            onDoubleTapRight = { triggerCompoundingSeek(SeekDirection.RIGHT) },
            onBrightnessGestureStart = {
                showBrightnessSlider = true
                sliderDismissToken = System.currentTimeMillis()
            },
            onBrightnessChanged = { deltaFraction ->
                showBrightnessSlider = true
                sliderDismissToken = System.currentTimeMillis()
                activity?.window?.let { window ->
                    val attributes = window.attributes
                    val currentBrightness =
                        if (attributes.screenBrightness < 0) 0.5f else attributes.screenBrightness
                    val finalBrightness = (currentBrightness + deltaFraction).coerceIn(0.01f, 1.0f)

                    attributes.screenBrightness = finalBrightness
                    window.attributes = attributes
                    brightnessSliderValue = finalBrightness * 10f
                }
            },
            onVolumeGestureStart = {
                showVolumeSlider = true
                sliderDismissToken = System.currentTimeMillis()
            },
            onVolumeChanged = { deltaFraction ->
                showVolumeSlider = true
                sliderDismissToken = System.currentTimeMillis()
                val delta = deltaFraction * 10f
                val currentSliderEquivalent = volume.toFloat() / 10f
                val newSliderValue = (currentSliderEquivalent + delta).coerceIn(0f, 10f)
                viewModel.setVolume((newSliderValue * 10f).toInt())
            },
            onSpeedChanged = { targetSpeed ->
                viewModel.setPlaybackSpeed(targetSpeed)
                is2xActive = targetSpeed > 1.0f
                if (is2xActive) isControlsVisible = false
            }
        )

        //  Speed indicator for gestures
        AnimatedVisibility(
            visible = is2xActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(60.dp)
                )
                Text(
                    text = "2X",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }


        AnimatedVisibility(
            visible = isControlsVisible && !isControlsLocked && !is2xActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {

            TopControls(
                mainTitle = episodeUi.mainTitle,
                episodeName = episodeUi.episodeTitle,
                onBackPressed = actions.onClose,
                onSourcesClicked = actions.onSourceClick,
                videoScaleModeText = currentScaleMode.name,
                onSubtitleTracksButtonClicked = { showSubtitleTracksSheet = true },
                subtitleTracks = subtitleTracks,
                videoQualityTracks = videoTracks,
                showVideoInfo = viewModel.settings.videoInfo,
                onVideoTrackButtonClicked = { showVideoTracksSheet = true },
                onMoreSettingsClicked = { openSheetState = true },
                audioTracks = audioTracks,
                onAudioTrackButtonClicked = { showAudioTracksSheet = true }
            )
        }


        AnimatedVisibility(
            visible = isControlsVisible && !isControlsLocked && !is2xActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterControls(
                isPlaying = activelyPlaying,
                isLoading = areYouReadyToRumble,
                onPlayPauseToggle = {
                    isControlsVisible = true
                    if (activelyPlaying) viewModel.pause() else viewModel.play()
                },
                onNextEpisode = actions.onNextEpisode,
                onPreviousEpisode = actions.onPreviousEpisode,
                hasNext = episodeUi.hasNextEpisode,
                hasPrevious = episodeUi.hasPreviousEpisode
            )
        }


        AnimatedVisibility(
            visible = !is2xActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val currentStamp = activeAniSkipStamp
            val hasActiveStamp = currentStamp != null

            val skipLabelDuration = remember(currentStamp, currentEnginePos) {
                if (currentStamp != null) {
                    ((currentStamp.endTimeMs - (currentEnginePos))).toInt().coerceAtLeast(0)
                } else {
                    null
                }
            }

            val segmentDisplayLabel = remember(currentStamp) {
                currentStamp?.type?.getType() ?: ""
            }

            BottomPlayerControls(
                isLocked = isControlsLocked,
                isControlsVisible = isControlsVisible,
                onUserInteraction = resetControlsVisibilityTimer,
                onLockToggled = { locked ->
                    onLockChanged(locked)
                    isControlsVisible = true
                },
                segmentName = segmentDisplayLabel,
                skipSegmentDuration = skipLabelDuration,
                onSkipSegmentClicked = {
                    isControlsVisible = true
                    val currentMs = viewModel.currentPosition.value

                    val skipDurationMs = viewModel.settings.skipTime.toLong() * 1000L

                    val targetMs = if (hasActiveStamp) {
                        (currentStamp!!.endTimeMs)
                    } else {
                        (currentMs + skipDurationMs).coerceAtMost(durationMs)
                    }

                    manualSkipOffsetMs = targetMs - currentMs
                    viewModel.seekTo(targetMs)
                },
                onAspectRatioClicked = {
                    isControlsVisible = true
                    val modes = VideoScaleMode.entries
                    val nextModeIndex = (modes.indexOf(currentScaleMode) + 1) % modes.size
                    val nextMode = modes[nextModeIndex]
                    viewModel.setVideoScaleMode(nextMode)
                },
                positionMs = displayPositionMs,
                durationMs = durationMs,
                readAheadMs = displayPositionMs + bufferCacheDuration,
                onSeekFinished = { targetMs ->
                    isControlsVisible = true
                    viewModel.seekTo(targetMs)
                },
                skipStamps = aniSkipStamps,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .padding(bottom = 16.dp)
            )
        }


        AnimatedVisibility(
            visible = showVolumeSlider,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp, bottom = 20.dp)
        ) {
            val currentVolumeFraction = remember(volume) { volume.toFloat() / 10f }
            VerticalSlider(
                value = currentVolumeFraction,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                onValueChange = { newValue ->
                    sliderDismissToken = System.currentTimeMillis()
                    viewModel.setVolume((newValue * 10f).toInt())
                }
            )
        }

        AnimatedVisibility(
            visible = showBrightnessSlider,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 56.dp, bottom = 20.dp)
        ) {
            VerticalSlider(
                value = brightnessSliderValue,
                icon = Icons.Rounded.LightMode,
                onValueChange = { newValue ->
                    sliderDismissToken = System.currentTimeMillis()
                    brightnessSliderValue = newValue
                    activity?.window?.let { window ->
                        val attrs = window.attributes
                        attrs.screenBrightness = (newValue / 10f).coerceIn(0.01f, 1.0f)
                        window.attributes = attrs
                    }
                }
            )
        }

        if (areYouReadyToRumble) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                propagateMinConstraints = false
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(88.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 4.dp
                )
            }
        }

        // SHEETS etc
        if (showAudioTracksSheet) {
            AudioTracksSheet(
                title = "Select Audio Track",
                trackList = audioTracks,
                currentTrack = currentAudioTrack,
                onTrackSelected = { track ->
                    viewModel.selectAudioTrack(track.id)
                    showAudioTracksSheet = false
                },
                onDismissRequest = { showAudioTracksSheet = false }
            )
        }
        if (showSubtitleTracksSheet) {
            SubtitlesTracksSheet(
                title = "Select Subtitle",
                trackList = subtitleTracks,
                currentTrack = currentSubtitleTrack,
                onTrackSelected = { track ->
                    viewModel.selectSubtitleTrack(track.id)
                    showSubtitleTracksSheet = false
                },
                onDismissRequest = { showSubtitleTracksSheet = false }
            )
        }
        if (showVideoTracksSheet) {
            VideoTracksSheet(
                title = "Select Video Track",
                trackList = videoTracks,
                currentTrack = currentVideoTrack,
                onTrackSelected = { track ->
                    viewModel.selectVideoTrack(track.id)
                    showVideoTracksSheet = false
                },
                onDismissRequest = { showVideoTracksSheet = false }
            )
        }
        if (openSheetState) {
            DecoderSettingsSheet(
                selectedDecoder = currentDecoder,
                onDecoderSelected = { decoder -> viewModel.setDecoder(decoder) },
                selectedAudioChannel = currentAudioChannel,
                onAudioChannelSelected = { channel -> viewModel.setAudioChannel(channel) },
                onDismissRequest = { openSheetState = false }
            )
        }
    }
}