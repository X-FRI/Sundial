package com.myapplication.shared.ui.analytics.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ChartPoint
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart

@Composable
internal fun EnergyOutputChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val series = preparePointSeries(points)
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(modelProducer, series.values) {
        modelProducer.runTransaction {
            columnSeries {
                series(series.values)
            }
        }
    }
    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        valueFormatter = rememberLabelValueFormatter(series.labels),
                    ),
            ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(160.dp),
    )
}
