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

/**
 * 空状态占位视图：居中显示标题 + 可选副标题 + 可选装饰图标。
 *
 * 设计要点：
 * - [icon] 为 null 时跳过 Canvas 图形，仅显示文字（省去不必要的绘制）；
 * - 装饰图形不是现成图标，而是用 Canvas 手绘一张"斜放的待办卡片"：
 *   卡片外框 + 两条正文线 + 右上角品牌色对勾，暗示"任务已完成、列表为空"；
 * - 所有坐标按画布尺寸 s 的比例硬编码，随 [Modifier.size(96.dp)] 等比缩放。
 */
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
                // 卡片线稿用弱化文字色，整体绕中心旋转 -6° 呈现"待办堆叠"的轻松感
                val cardColor = colors.textLow.copy(alpha = 0.35f)
                withTransform({
                    rotate(-6f, pivot = Offset(s / 2, s / 2))
                }) {
                    // 卡片外框（描边）
                    drawRoundRect(
                        color = cardColor,
                        topLeft = Offset(s * 0.18f, s * 0.22f),
                        size = Size(s * 0.64f, s * 0.56f),
                        cornerRadius = CornerRadius(s * 0.02f),
                        style = Stroke(width = st),
                    )
                    // 两条"正文行"（实心圆角条）
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
                    // 对勾：品牌色描边折线，圆角端帽
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
