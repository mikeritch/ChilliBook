package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val burstColors = listOf(
    Color(0xFFEAAF49), Color(0xFFE18BAA), Color(0xFF84BCA4),
    Color(0xFFA997D2), Color(0xFFF0A382), Color(0xFF76B6CC)
)

private val starShape = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(.13f, -.15f, .8f, 0f)
    quadraticTo(.13f, .15f, 0f, 1f)
    quadraticTo(-.13f, .15f, -.8f, 0f)
    quadraticTo(-.13f, -.15f, 0f, -1f)
    close()
}

private val petalShape = Path().apply {
    moveTo(0f, 1f)
    cubicTo(-.9f, .3f, -.7f, -1f, 0f, -1f)
    cubicTo(.85f, -.95f, .64f, .48f, 0f, 1f)
    close()
}

private data class BurstPiece(
    val direction: Offset,
    val delay: Float,
    val radius: Float,
    val spin: Float,
    val color: Color
)

// Stagger two evenly spaced waves and vary their reach without moving pieces between frames.
private fun pieces(count: Int, seed: Int): List<BurstPiece> {
    val random = Random(seed)
    return List(count) { index ->
        val angle = (index + random.nextFloat() * .65) * 2 * Math.PI / count
        val reach = .27f + random.nextFloat() * .41f
        BurstPiece(
            Offset(cos(angle).toFloat(), sin(angle).toFloat()) * reach,
            random.nextFloat() * .12f,
            5f + random.nextFloat() * 7f,
            (random.nextFloat() - .5f) * 380f,
            burstColors[index % burstColors.size]
        )
    }
}

private val stars = pieces(28, 19)
private val petals = pieces(50, 34)

private fun ramp(value: Float, start: Float, end: Float) =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

@Composable
internal fun DayCelebrationBurst(progress: () -> Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val time = progress()
            if (time <= 0f || time >= 1f) return@Canvas
            fun drawWave(pieces: List<BurstPiece>, isPetal: Boolean) {
                val start = if (isPetal) .19f else 0f
                val end = if (isPetal) 1f else .65f
                for (piece in pieces) {
                    val phase = ramp(time, start + piece.delay, end)
                    if (phase <= 0f || phase >= 1f) continue
                    val travel = 1f - (1f - phase) * (1f - phase)
                    val fall = if (isPetal) phase * phase * size.height * .14f else 0f
                    val flutter = if (isPetal) sin(phase * 7f + piece.spin) * phase * 14.dp.toPx() else 0f
                    val x = center.x + piece.direction.x * size.width * travel + flutter
                    val y = center.y + piece.direction.y * size.height * travel + fall
                    val radius = piece.radius.dp.toPx() * (.55f + .45f * travel)
                    val alpha = ramp(phase, 0f, .12f) * (1f - ramp(phase, .66f, 1f))
                    withTransform({
                        translate(x, y)
                        rotate(piece.spin * phase, Offset.Zero)
                        scale(radius, radius, Offset.Zero)
                    }) {
                        drawPath(if (isPetal) petalShape else starShape, piece.color, alpha)
                    }
                }
            }
            drawWave(stars, false)
            drawWave(petals, true)
        }
        BloomingFlower(
            progress = { ramp(progress(), .02f, .29f) },
            modifier = Modifier.size(128.dp).graphicsLayer {
                val time = progress()
                val growth = ramp(time, 0f, .25f)
                scaleX = .42f + .58f * (1f - (1f - growth) * (1f - growth))
                scaleY = scaleX
                alpha = ramp(time, 0f, .08f) * (1f - ramp(time, .43f, .7f))
            }
        )
    }
}
