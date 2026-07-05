package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ani.saikou.media.anime.mpv.PlayerRepository.SkipInterval
import ani.saikou.media.anime.mpv.PlayerViewModel


@Composable
fun BottomPlayerControls(
    isLocked: Boolean,
    isControlsVisible: Boolean,
    onLockToggled: (Boolean) -> Unit,
    onUserInteraction: () -> Unit,
    segmentName: String,
    skipSegmentDuration: Int?,
    onSkipSegmentClicked: () -> Unit,
    onAspectRatioClicked: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    readAheadMs: Long,
    onSeekFinished: (Long) -> Unit, skipStamps: List<SkipInterval>? = null,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val elementTint = Color.White
    val feedbackColor = MaterialTheme.colorScheme.primary

    val isSegmentAvailable = skipSegmentDuration != null
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
    ) {
        AnimatedVisibility(
            visible = isControlsVisible || isSegmentAvailable,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        BottomLeftControls(
                            isLocked = isLocked,
                            onLockToggled = onLockToggled,
                            currentSpeed = viewModel.playbackSpeed.value,
                            onSpeedChanged = {
                                onUserInteraction()
                                val current = viewModel.playbackSpeed.value
                                val nextIndex =
                                    (speedOptions.indexOf(current) + 1) % speedOptions.size
                                viewModel.setPlaybackSpeed(speedOptions[nextIndex])
                            },
                            elementTint = elementTint,
                            feedbackColor = feedbackColor,

                            modifier = Modifier
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    BottomRightControls(
                        isLocked = isLocked,
                        isControlsVisible = isControlsVisible,
                        skipDurationSecondsSettings = viewModel.settings.skipTime,
                        onSkipSegmentClicked = onSkipSegmentClicked,
                        onAspectRatioClicked = onAspectRatioClicked,
                        elementTint = elementTint,
                        feedbackColor = feedbackColor,
                        segment = segmentName,
                        isSegmentAvailable = isSegmentAvailable
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                readAheadMs = readAheadMs,
                onSeekFinished = onSeekFinished,
                skipStamps = skipStamps,
                modifier = Modifier
                    .fillMaxWidth()

            )
        }
    }
}