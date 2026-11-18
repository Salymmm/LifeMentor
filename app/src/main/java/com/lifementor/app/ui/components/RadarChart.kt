package com.lifementor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Five-dimension growth radar: values are normalized 0–1, drawn with raw Canvas, no third-party deps */
@Composable
fun RadarChart(
    labels: List<String>,
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sides = labels.size
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f - 30.dp.toPx()
        val gridColor = color.copy(alpha = 0.22f)

        fun vertex(i: Int, radius: Float): Offset {
            val angle = -Math.PI / 2 + 2 * Math.PI * i / sides
            return Offset(cx + (radius * Math.cos(angle)).toFloat(), cy + (radius * Math.sin(angle)).toFloat())
        }

        // concentric grid
        for (ring in 1..3) {
            val p = Path()
            for (i in 0 until sides) {
                val v = vertex(i, r * ring / 3f)
                if (i == 0) p.moveTo(v.x, v.y) else p.lineTo(v.x, v.y)
            }
            p.close()
            drawPath(p, gridColor, style = Stroke(width = 1.dp.toPx()))
        }
        // axis lines
        for (i in 0 until sides) {
            drawLine(gridColor, Offset(cx, cy), vertex(i, r), 1.dp.toPx())
        }
        // data polygon
        val data = Path()
        for (i in 0 until sides) {
            val v = vertex(i, r * values[i].coerceIn(0.06f, 1f))
            if (i == 0) data.moveTo(v.x, v.y) else data.lineTo(v.x, v.y)
        }
        data.close()
        drawPath(data, color.copy(alpha = 0.28f))
        drawPath(data, color, style = Stroke(width = 2.5.dp.toPx()))
        // vertices
        for (i in 0 until sides) {
            drawCircle(color, 4.dp.toPx(), vertex(i, r * values[i].coerceIn(0.06f, 1f)))
        }
        // labels
        val paint = android.graphics.Paint().apply {
            setColor(
                android.graphics.Color.argb(
                    255,
                    (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
                )
            )
            textSize = 12.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (i in 0 until sides) {
            val v = vertex(i, r + 18.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(labels[i], v.x, v.y + 4.dp.toPx(), paint)
        }
    }
}
