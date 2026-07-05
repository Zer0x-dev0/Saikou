package ani.saikou.media.anime.mpv.ui.components.gestures

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import ani.saikou.R

enum class SeekDirection { LEFT, RIGHT, NONE }

class SemiCircleShape(private val isRightSide: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val radius = size.height * 0.5f

            if (isRightSide) {
                val centerX = size.width
                val centerY = size.height / 2f

                moveTo(size.width, 0f)
                lineTo(size.width, size.height)
                arcTo(
                    rect = Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            } else {
                val centerX = 0f

                moveTo(0f, size.height)
                lineTo(0f, 0f)
                arcTo(
                    rect = Rect(centerX - radius, size.height / 2f - radius, centerX + radius, size.height / 2f + radius),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun SeekEffectOverlay(
    direction: SeekDirection,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
    seekSeconds: Int = 10,
    isPreviewMode: Boolean = false
) {
    var triggerOverlay by remember { mutableStateOf(isPreviewMode) }
    var atEndByTrigger by remember { mutableStateOf(isPreviewMode) }

    val skipImage = AnimatedImageVector.animatedVectorResource(R.drawable.anim_skip)
    val rewindImage = AnimatedImageVector.animatedVectorResource(R.drawable.anim_rewind)


    if (!isPreviewMode) {
        LaunchedEffect(direction, seekSeconds) {
            when (direction) {
                SeekDirection.LEFT, SeekDirection.RIGHT -> {
                    atEndByTrigger = false
                    triggerOverlay = true
                    delay(10.milliseconds)
                    atEndByTrigger = true

                }
                SeekDirection.NONE -> {
                    triggerOverlay = false
                    atEndByTrigger = false
                    onAnimationFinished()
                }
            }
        }
    }

    val containerAlpha by animateFloatAsState(
        targetValue = if (triggerOverlay && direction != SeekDirection.NONE) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "container_fade"
    )

    Box(
        modifier = modifier.fillMaxSize().alpha(containerAlpha),
        contentAlignment = if (direction == SeekDirection.RIGHT) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (direction != SeekDirection.NONE) {
            val isRight = direction == SeekDirection.RIGHT
            val vectorPainter = rememberAnimatedVectorPainter(
                animatedImageVector = if (isRight) skipImage else rewindImage,
                atEnd = atEndByTrigger
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.32f)
                    .clip(SemiCircleShape(isRightSide = isRight))
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 10.dp)
                ) {
                    Image(
                        painter = vectorPainter,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White)
                    )

                    Text(
                        text = if (isRight) "+${seekSeconds}s" else "-${seekSeconds}s",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
        }
    }
}

class SeekPreview : PreviewParameterProvider<SeekDirection> {
    override val values: Sequence<SeekDirection> = sequenceOf(
        SeekDirection.LEFT,
        SeekDirection.RIGHT
    )
}

@Preview(
    name = "Dark Unified Preview",
    showBackground = true,
    widthDp = 720,
    heightDp = 380,
    backgroundColor = 0xFF141414
)
@Composable
fun SeekEffectOverlayCustomPreview(
    @PreviewParameter(SeekPreview::class) direction: SeekDirection
) {
    SeekEffectOverlay(
        direction = direction,
        onAnimationFinished = {},
        seekSeconds = 10,
        isPreviewMode = true
    )
}