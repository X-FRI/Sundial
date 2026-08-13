package com.myapplication.shared.ui.analytics.charts

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ChartTone
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.LineComponent
import com.patrykandpatrick.vico.multiplatform.common.data.ExtraStore
import kotlin.math.roundToInt

@Composable
internal fun rememberLabelValueFormatter(labels: List<String>): CartesianValueFormatter =
    remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.roundToInt()).orEmpty()
        }
    }

@Composable
internal fun rememberToneColumnProvider(tones: List<ChartTone>): ColumnCartesianLayer.ColumnProvider {
    val colors = rememberAnalyticsChartColors()
    return remember(tones, colors) {
        ToneColumnProvider(
            tones.map { tone ->
                LineComponent(
                    fill = Fill(tone.chartColor(colors)),
                    thickness = 14.dp,
                    shape = RoundedCornerShape(4.dp),
                )
            },
        )
    }
}

private class ToneColumnProvider(
    private val columns: List<LineComponent>,
) : ColumnCartesianLayer.ColumnProvider {
    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = columns.columnAt(entry.x.roundToInt())

    override fun getWidestSeriesColumn(seriesIndex: Int, extraStore: ExtraStore): LineComponent =
        columns.columnAt(seriesIndex)

    private fun List<LineComponent>.columnAt(index: Int): LineComponent =
        getOrElse(index) { last() }
}

private fun ChartTone.chartColor(colors: AnalyticsChartColors): Color =
    when (this) {
        ChartTone.Primary -> colors.primary
        ChartTone.Info -> colors.secondary
        ChartTone.Warning -> colors.warning
        ChartTone.Danger -> colors.danger
        ChartTone.Neutral -> colors.label
    }
