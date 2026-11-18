package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
internal fun BloomingFlower(progress: () -> Float, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurface
    val petals = listOf(Color(0xFFF4BED3), Color(0xFFF7CDCC), Color(0xFFDAD2F6))
    val faceInk = Color(0xFF574351)
    Canvas(modifier.size(44.dp)) {
        val open = progress().coerceIn(0f, 1f)
        val unit = size.minDimension / 44f
        val hub = Offset(0f, 4f * (1f - open))
        withTransform({
            translate(center.x, center.y)
            scale(unit, unit, Offset.Zero)
        }) {
            val width = 6f + 8f * open
            val length = 13f + 8f * open
            repeat(6) { index ->
                // The folded petals point upwards, then fan out around the face.
                val angle = (index - 2.5f) * (10f + 50f * open)
                rotate(angle, hub) {
                    val corner = hub - Offset(width / 2, length)
                    val petalSize = Size(width, length)
                    drawOval(petals[index % petals.size], corner, petalSize)
                    drawOval(outline, corner, petalSize, style = Stroke(1f))
                }
            }
            val radius = 2.8f + 4.5f * open
            drawCircle(Color(0xFFEEC873), radius, hub)
            drawCircle(faceInk, radius, hub, style = Stroke(1f))
            drawCircle(faceInk, radius * .12f, hub + Offset(-radius * .39f, -radius * .19f))
            drawCircle(faceInk, radius * .12f, hub + Offset(radius * .39f, -radius * .19f))
            drawArc(faceInk, 20f, 140f, false, hub + Offset(-radius * .34f, -radius * .07f),
                Size(radius * .68f, radius * .52f), style = Stroke(radius * .15f, cap = StrokeCap.Round))
        }
    }
}
