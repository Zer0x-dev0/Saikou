package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.media.anime.mpv.AudioTrack
import ani.saikou.media.anime.mpv.SubtitleTrack
import ani.saikou.media.anime.mpv.VideoTrack
import kotlinx.coroutines.delay

@Composable
fun TopControls(
    mainTitle: String,
    episodeName: String,
    onBackPressed: () -> Unit,

    videoScaleModeText: String,

    subtitleTracks: List<SubtitleTrack>,
    onSubtitleTracksButtonClicked: () -> Unit,

    audioTracks: List<AudioTrack>,
    onAudioTrackButtonClicked: () -> Unit,

    videoQualityTracks: List<VideoTrack>,
    onVideoTrackButtonClicked: () -> Unit,
    showVideoInfo: Boolean,

    onMoreSettingsClicked: () -> Unit,
    onSourcesClicked: () -> Unit,

    modifier: Modifier = Modifier
) {
    val feedbackColor = MaterialTheme.colorScheme.primary

    var isStatusVisible by remember { mutableStateOf(false) }
    var previousStateText by remember { mutableStateOf("") }

    LaunchedEffect(videoScaleModeText) {
        if (videoScaleModeText.isNotEmpty() && videoScaleModeText != previousStateText) {

            if (previousStateText.isNotEmpty()) {
                isStatusVisible = true
            }

            previousStateText = videoScaleModeText

            delay(5000)
            isStatusVisible = false
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // LEFT SIDE Navigation and Metadata
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 24.dp
                            ),
                            onClick = onBackPressed
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Close Player",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = episodeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mainTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            //  RIGHT SIDE  Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                //  Sources Selector
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 22.dp
                            ),
                            onClick = onSourcesClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = "Streaming Sources",
                        tint = Color.White
                    )
                }

                // Subtitles
                if (subtitleTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(
                                    bounded = true,
                                    color = feedbackColor,
                                    radius = 22.dp
                                ),
                                onClick = onSubtitleTracksButtonClicked
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = Color.White
                        )
                    }
                }

                // Audio
                if (audioTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(
                                    bounded = true,
                                    color = feedbackColor,
                                    radius = 22.dp
                                ),
                                onClick = onAudioTrackButtonClicked
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "Audio tracks",
                            tint = Color.White
                        )
                    }
                }

                // Video Quality
                if (videoQualityTracks.size > 1 && showVideoInfo) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(
                                    bounded = true,
                                    color = feedbackColor,
                                    radius = 22.dp
                                ),
                                onClick = onVideoTrackButtonClicked
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = "Quality options",
                            tint = Color.White
                        )
                    }
                }

                // Decoder settings
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = feedbackColor,
                                radius = 22.dp
                            ),
                            onClick = onMoreSettingsClicked
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "More configurations",
                        tint = Color.White
                    )
                }
            }
        }

        // Toast indicator(Might want to put peers,seeders leechers when torrents is supported)
        AnimatedVisibility(
            visible = isStatusVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 64.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = videoScaleModeText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}


@Preview(
    showSystemUi = true,
    showBackground = true,
    device = "spec:width=800dp,height=360dp,dpi=480"
)
@Composable
fun TopBarPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val defaultPreviewTrack = AudioTrack(id = 1, name = "Japanese (Default)", language = "ja")
        val defaultPreviewTrack2 = VideoTrack(id = 1, name = "Dunno")
        val defaultPreviewTrack3 = SubtitleTrack(id = 1, name = "wassup", language = null)
        TopControls(
            mainTitle = "Frieren: Beyond Journey's End",
            episodeName = "Episode 11 - Winter in the Northern Lands",
            videoScaleModeText = "Cropped",
            onBackPressed = {},
            subtitleTracks = listOf(defaultPreviewTrack3),
            onSubtitleTracksButtonClicked = {},
            audioTracks = listOf(defaultPreviewTrack),
            onAudioTrackButtonClicked = {},
            videoQualityTracks = listOf(defaultPreviewTrack2),
            showVideoInfo = true,
            onVideoTrackButtonClicked = {},
            onMoreSettingsClicked = {},
            onSourcesClicked = {},
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}