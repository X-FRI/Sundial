package com.myapplication.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemEmptyState(
    title: String,
    subtitle: String = "",
    icon: IconName? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val colors = LocalRemColors.current
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Canvas(Modifier.size(96.dp)) {
                val s = size.minDimension
                val st = 2.dp.toPx()
                val cardColor = colors.textLow.copy(alpha = 0.35f)
                withTransform({
                    rotate(-6f, pivot = Offset(s / 2, s / 2))
                }) {
                    drawRoundRect(
                        color = cardColor,
                        topLeft = Offset(s * 0.18f, s * 0.22f),
                        size = Size(s * 0.64f, s * 0.56f),
                        cornerRadius = CornerRadius(s * 0.02f),
                        style = Stroke(width = st),
                    )
                    drawRoundRect(
                        color = colors.textLow.copy(alpha = 0.5f),
                        topLeft = Offset(s * 0.30f, s * 0.34f),
                        size = Size(s * 0.40f, s * 0.08f),
                        cornerRadius = CornerRadius(s * 0.04f),
                    )
                    drawRoundRect(
                        color = colors.textLow.copy(alpha = 0.5f),
                        topLeft = Offset(s * 0.30f, s * 0.48f),
                        size = Size(s * 0.28f, s * 0.08f),
                        cornerRadius = CornerRadius(s * 0.04f),
                    )
                    val p = Path().apply {
                        moveTo(s * 0.34f, s * 0.74f)
                        lineTo(s * 0.42f, s * 0.82f)
                        lineTo(s * 0.60f, s * 0.62f)
                    }
                    drawPath(p, colors.brand, style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        androidx.compose.foundation.text.BasicText(title, style = RemType.title18.copy(color = colors.textHigh))
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText(subtitle, style = RemType.text12.copy(color = colors.textLow))
        }
    }
}
