package ani.saikou.media.anime.mpv.ui.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    iconProvider: (Float) -> ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    var totalHeightPx by remember { mutableFloatStateOf(1f) }

    val sliderWidth = 42

    val animatedFraction by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
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
    val sliderHeight = (screenHeightDp * 0.55f).coerceAtMost(180.dp)

    Box(
        modifier = modifier
            .width(sliderWidth.dp)
            .height(sliderHeight)
            .clip(RoundedCornerShape(24.dp))
            .onGloballyPositioned { coordinates ->
                totalHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (totalHeightPx - offset.y) / totalHeightPx
                    val newValue = fraction.coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                startDragImmediately = true,
                state = rememberDraggableState { deltaY ->
                    val changeInFraction = -deltaY / totalHeightPx
                    val newValue = (value + changeInFraction).coerceIn(0f, 1f)
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
    ) {
        Icon(
            imageVector = iconProvider(value),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(24.dp),
            tint = tint
        )
    }
}

