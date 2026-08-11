package com.myapplication.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

enum class IconName { Calendar, Today, Scheduled, Tray, CheckCircle, Trash, Search, Plus, Close, ChevronBack, ChevronRight, ChevronDown, Flag }

@Composable
fun RemIcon(
    name: IconName,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Canvas(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val s = size.minDimension
        val u = s / 24f
        val st = 1.8f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = st, cap = StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
            drawCircle(tint, radius = r * u, center = Offset(cx * u, cy * u), style = if (filled) Fill else Stroke(width = st))
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
            drawRoundRect(
                tint,
                topLeft = Offset(x * u, y * u),
                size = Size(w * u, h * u),
                cornerRadius = CornerRadius(r * u, r * u),
                style = Stroke(width = st),
            )
        fun poly(vararg pts: Float, filled: Boolean = false) {
            val p = Path()
            var i = 0
            while (i + 1 < pts.size) {
                val x = pts[i] * u
                val y = pts[i + 1] * u
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                i += 2
            }
            drawPath(p, tint, style = if (filled) Fill else Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (name) {
            IconName.Calendar -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 4.1f)
                line(16f, 3f, 16f, 4.1f)
            }
            IconName.Today -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 4.1f)
                line(16f, 3f, 16f, 4.1f)
                circle(12f, 12.5f, 2.2f, filled = true)
            }
            IconName.Scheduled -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 4.1f)
                line(16f, 3f, 16f, 4.1f)
                line(7.5f, 12f, 16.5f, 12f)
                line(7.5f, 15.5f, 13f, 15.5f)
            }
            IconName.Tray -> {
                box(4f, 7f, 16f, 12f, r = 2.5f)
                line(4f, 10.5f, 20f, 10.5f)
                line(9.5f, 13.8f, 14.5f, 13.8f)
            }
            IconName.CheckCircle -> {
                circle(12f, 12f, 8f)
                poly(8.5f, 12.5f, 11.2f, 15.2f, 16f, 9.5f)
            }
            IconName.Trash -> {
                line(6f, 8.5f, 18f, 8.5f)
                box(10f, 5f, 4f, 3.5f, r = 1f)
                poly(8.2f, 8.5f, 8.6f, 19.5f, 15.4f, 19.5f, 15.8f, 8.5f)
                line(10.6f, 12f, 10.9f, 16.5f)
                line(13.4f, 12f, 13.1f, 16.5f)
            }
            IconName.Search -> {
                circle(10.5f, 10.5f, 5.5f)
                line(14.5f, 14.5f, 20f, 20f)
            }
            IconName.Plus -> {
                line(12f, 6f, 12f, 18f)
                line(6f, 12f, 18f, 12f)
            }
            IconName.Close -> {
                line(7f, 7f, 17f, 17f)
                line(17f, 7f, 7f, 17f)
            }
            IconName.ChevronBack -> poly(14f, 6f, 9f, 12f, 14f, 18f)
            IconName.ChevronRight -> poly(10f, 6f, 15f, 12f, 10f, 18f)
            IconName.ChevronDown -> poly(6f, 10f, 12f, 15f, 18f, 10f)
            IconName.Flag -> {
                line(8f, 4f, 8f, 20f)
                poly(8f, 5.5f, 18f, 8.5f, 8f, 11.5f, filled = true)
            }
        }
    }
}
