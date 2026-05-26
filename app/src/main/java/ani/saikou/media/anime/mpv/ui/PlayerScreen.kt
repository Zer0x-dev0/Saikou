package ani.saikou.media.anime.mpv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ani.saikou.media.anime.mpv.MPVPlayerImpl
import ani.saikou.media.anime.mpv.Player
import ani.saikou.media.anime.mpv.PlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    videoUrl: String,
    headers: Map<String, String> =emptyMap()
) {

    var playerCreated by remember { mutableStateOf(false) }


    val playbackState by if (playerCreated) {
        viewModel.playbackState.collectAsState()
    } else {
        remember { mutableStateOf(Player.PlaybackState.IDLE) }
    }

    val isPlaying by if (playerCreated) {
        viewModel.isPlaying.collectAsState()
    } else {
        remember { mutableStateOf(false) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { context ->
                MPVPlayerImpl(context).also { playerInstance ->

                    viewModel.setPlayerInstance(playerInstance)
                    playerCreated = true

                    playerInstance.init(playerInstance.holder)


                    viewModel.loadMedia(videoUrl = videoUrl, headers = headers)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (playbackState == Player.PlaybackState.BUFFERING ||
            playbackState == Player.PlaybackState.IDLE) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        /// some states
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "State: ${playbackState.name}",
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = if (isPlaying) "Playing" else "Paused", /// so its just playing while buffering(player state mismatch revisit this)
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}