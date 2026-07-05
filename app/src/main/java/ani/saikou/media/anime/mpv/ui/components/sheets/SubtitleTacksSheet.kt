package ani.saikou.media.anime.mpv.ui.components.sheets

import androidx.compose.runtime.Composable
import ani.saikou.media.anime.mpv.SubtitleTrack

@Composable

fun SubtitlesTracksSheet(
    title: String = "Select Subtitle",
    trackList: List<SubtitleTrack>,
    currentTrack: SubtitleTrack,
    onTrackSelected: (SubtitleTrack) -> Unit,
    onDismissRequest: () -> Unit
) {
    GenericTracksSheet(
        title = title,
        trackList = trackList,
        currentTrack = currentTrack,
        trackToText = { track ->
            track.name.ifEmpty { "Unknown Subtitle" }
        },
        onTrackSelected = onTrackSelected,
        onDismissRequest = onDismissRequest
    )
}