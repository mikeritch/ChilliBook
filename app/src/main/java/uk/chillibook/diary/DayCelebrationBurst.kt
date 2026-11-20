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

private data class BurstWave(
    val pieces: List<BurstPiece>,
    val start: Float,
    val end: Float,
    val isPetal: Boolean,
    val scale: Float,
)

// Distribute each wave evenly around the flower, with fixed offsets for its stagger and reach.
private fun wave(
    count: Int,
    start: Float,
    end: Float,
    isPetal: Boolean,
    offset: Int,
    scale: Float = 1f,
): BurstWave {
    val pieces = List(count) { index ->
        fun variation(step: Int) = ((index * step + offset) % 101) / 101f
        val angle = (index + variation(37) * .65) * 2 * Math.PI / count
        val reach = .27f + variation(53) * .41f
        BurstPiece(
            Offset(cos(angle).toFloat(), sin(angle).toFloat()) * reach,
            variation(47) * .12f,
            5f + variation(71) * 7f,
            (variation(59) - .5f) * 380f,
            burstColors[(index + offset) % burstColors.size]
        )
    }
    return BurstWave(pieces, start, end, isPetal, scale)
}

private fun flowerWaves(stars: Int, petals: Int) = listOf(
    wave(stars, 0f, .65f, isPetal = false, offset = 19),
    wave(petals, .19f, 1f, isPetal = true, offset = 34),
)

private val firstDayWaves = flowerWaves(28, 50)
private val threeDayWaves = flowerWaves(30, 58)
private val sevenDayWaves = flowerWaves(34, 66)
private val fourteenDayWaves = flowerWaves(36, 74) +
    wave(16, .39f, .9f, isPetal = false, offset = 79, scale = .72f)

private fun ramp(value: Float, start: Float, end: Float) =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

@Composable
internal fun DayCelebrationBurst(progress: () -> Float, streak: Int, modifier: Modifier) {
    val (waves, bloomSize) = when {
        streak >= 14 -> fourteenDayWaves to 164.dp
        streak >= 7 -> sevenDayWaves to 152.dp
        streak >= 3 -> threeDayWaves to 140.dp
        else -> firstDayWaves to 128.dp
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val time = progress()
            if (time <= 0f || time >= 1f) return@Canvas
            for (wave in waves) {
                if (time <= wave.start || time >= wave.end) continue
                for (piece in wave.pieces) {
                    val phase = ramp(time, wave.start + piece.delay, wave.end)
                    if (phase <= 0f || phase >= 1f) continue
                    val travel = 1f - (1f - phase) * (1f - phase)
                    val fall = if (wave.isPetal) phase * phase * size.height * .14f else 0f
                    val flutter = if (wave.isPetal) sin(phase * 7f + piece.spin) * phase * 14.dp.toPx() else 0f
                    val x = center.x + piece.direction.x * size.width * travel + flutter
                    val y = center.y + piece.direction.y * size.height * travel + fall
                    val radius = piece.radius.dp.toPx() * wave.scale * (.55f + .45f * travel)
                    val alpha = ramp(phase, 0f, .12f) * (1f - ramp(phase, .66f, 1f))
                    withTransform({
                        translate(x, y)
                        rotate(piece.spin * phase, Offset.Zero)
                        scale(radius, radius, Offset.Zero)
                    }) {
                        drawPath(if (wave.isPetal) petalShape else starShape, piece.color, alpha)
                    }
                }
            }
        }
        BloomingFlower(
            progress = { ramp(progress(), .02f, .29f) },
            modifier = Modifier.size(bloomSize).graphicsLayer {
                val time = progress()
                val growth = ramp(time, 0f, .25f)
                scaleX = .42f + .58f * (1f - (1f - growth) * (1f - growth))
                scaleY = scaleX
                alpha = ramp(time, 0f, .08f) * (1f - ramp(time, .43f, .7f))
            }
        )
    }
}
