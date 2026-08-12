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

/**
 * 全部图标名称的枚举；与 [RemIcon] 的 when 分支一一对应。
 *
 * 新增图标流程：在此加枚举值 → 在 RemIcon 的 when 中补绘制分支 →
 * 调用方通过 contentDescription 提供无障碍文案。
 */
enum class IconName { Calendar, Today, Scheduled, Tray, CheckCircle, Trash, Search, Plus, Close, ChevronBack, ChevronRight, ChevronDown, Flag, DotsThree, Cloud, Server, Device, Key, Settings, Sync, Clock, Inbox, Layers, Send }

/**
 * 矢量手绘图标组件（Canvas 绘制，不依赖图片资源）。
 *
 * 坐标系约定（所有图标必须遵守）：
 * - 设计网格固定为 24×24 虚拟单位，绘制时以 `u = size / 24f` 缩放，
 *   保证任意尺寸下图标比例一致；
 * - 描边线宽 = 1.8 个网格单位（st = 1.8f * u），全部线帽/连接处为圆角；
 * - 坐标均为"网格坐标"（如 box(3.5f, 5f, 17f, 15f) = 左上角 (3.5,5)、
 *   宽 17、高 15），修改图标时保持 24 网格内布局并留出描边边距。
 *
 * 辅助绘制函数（局部函数，闭包捕获 tint/u/st）：
 * - [line]：两点连线；[circle]：圆（可选实心）；[box]：圆角矩形描边；
 * - [poly]：折线/多边形（可选实心）。
 */
@Composable
fun RemIcon(
    name: IconName,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Canvas(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val s = size.minDimension
        // 网格换算：1 网格单位 = 画布边长 / 24
        val u = s / 24f
        // 标准描边宽度：1.8 网格单位
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
        // 各图标绘制：均为 24 网格内的几何组合，参数为网格坐标
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
            IconName.DotsThree -> {
                circle(5.5f, 12f, 1.2f, filled = true)
                circle(12f, 12f, 1.2f, filled = true)
                circle(18.5f, 12f, 1.2f, filled = true)
            }
            IconName.Cloud -> {
                line(6.2f, 17.4f, 17.8f, 17.4f)
                circle(8.2f, 15.6f, 3.1f)
                circle(12.4f, 14f, 3.9f)
                circle(16f, 16f, 2.3f)
            }
            IconName.Server -> {
                box(4.5f, 5f, 15f, 14f, r = 2.5f)
                line(4.5f, 10.6f, 19.5f, 10.6f)
                circle(7.8f, 7.9f, 0.9f, filled = true)
                circle(7.8f, 13.3f, 0.9f, filled = true)
            }
            IconName.Device -> {
                box(7.5f, 4f, 9f, 16f, r = 2.5f)
                line(11f, 17.2f, 13f, 17.2f)
            }
            IconName.Key -> {
                circle(9.8f, 14.5f, 3.1f)
                line(12.2f, 12.1f, 17.8f, 6.5f)
                line(17.8f, 6.5f, 17.8f, 13.5f)
                line(14.8f, 10.4f, 20.8f, 10.4f)
            }
            IconName.Settings -> {
                line(4f, 7f, 20f, 7f)
                circle(9f, 7f, 2.4f, filled = true)
                line(4f, 12.5f, 20f, 12.5f)
                circle(15f, 12.5f, 2.4f, filled = true)
                line(4f, 18f, 20f, 18f)
                circle(6.5f, 18f, 2.4f, filled = true)
            }
            IconName.Sync -> {
                circle(12f, 12f, 7.5f)
                poly(12f, 3.5f, 8.4f, 7f, 15.6f, 7f, filled = true)
                poly(12f, 20.5f, 8.4f, 17f, 15.6f, 17f, filled = true)
            }
            IconName.Clock -> {
                circle(12f, 12f, 8f)
                line(12f, 7.5f, 12f, 12f)
                line(12f, 12f, 15.5f, 14.2f)
            }
            IconName.Inbox -> {
                box(4f, 6f, 16f, 12f, r = 2.5f)
                line(4f, 12f, 8.5f, 12f)
                line(15.5f, 12f, 20f, 12f)
                line(8.5f, 12f, 10f, 15f)
                line(14f, 15f, 15.5f, 12f)
                line(10f, 15f, 14f, 15f)
            }
            IconName.Layers -> {
                poly(12f, 4.5f, 20f, 9f, 12f, 13.5f, 4f, 9f, 12f, 4.5f)
                poly(5.5f, 13f, 12f, 16.7f, 18.5f, 13f)
                poly(5.5f, 16.5f, 12f, 20.2f, 18.5f, 16.5f)
            }
            IconName.Send -> {
                poly(4f, 12f, 20f, 5f, 15f, 20f, 12f, 14f, 4f, 12f)
            }
        }
    }
}
