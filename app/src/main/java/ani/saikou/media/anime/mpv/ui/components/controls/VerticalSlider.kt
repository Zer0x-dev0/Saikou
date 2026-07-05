package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    var totalHeightPx by remember { mutableFloatStateOf(1f) }

    val sliderWidth = 32

    val animatedFraction by animateFloatAsState(
        targetValue = (value / 10f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 400f
        ),
        label = "SliderTrackFillAnimation"
    )

    val trackContainerColor = MaterialTheme.colorScheme.primaryContainer
    val trackFillColor = MaterialTheme.colorScheme.primary
    val configuration = LocalConfiguration.current

    val screenHeightDp = configuration.screenHeightDp.dp
    val sliderHeight = (screenHeightDp * 0.45f).coerceAtMost(172.dp)

    Column(
        modifier = modifier.width(sliderWidth.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(sliderWidth.dp)
                .height(sliderHeight)
                .clip(RoundedCornerShape(20.dp))
                .onGloballyPositioned { coordinates ->
                    totalHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (totalHeightPx - offset.y) / totalHeightPx
                        val newValue = (fraction * 10f).coerceIn(0f, 10f)
                        onValueChange(newValue)
                    }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    startDragImmediately = true,
                    state = rememberDraggableState { deltaY ->
                        val changeInFraction = -deltaY / totalHeightPx
                        val newValue = (value + changeInFraction * 10f).coerceIn(0f, 10f)
                        onValueChange(newValue)
                    }
                )
                .drawBehind {
                    drawRect(color = trackContainerColor)

                    val fillHeight = size.height * animatedFraction
                    drawRect(
                        color = trackFillColor,
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight)
                    )
                }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = Color.White
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, backgroundColor = 0xFF000000)
@Composable
fun VerticalSliderPreview() {
    var volumeValue by remember { mutableFloatStateOf(6f) }

    Box(
        modifier = Modifier
            .height(300.dp)
            .width(100.dp),
        contentAlignment = Alignment.Center
    ) {
        VerticalSlider(
            value = volumeValue,
            onValueChange = { volumeValue = it },
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
        )
    }
}