package com.myapplication.shared.ui.analytics.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ChartBucket
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart

@Composable
internal fun PressureDistributionChart(buckets: List<ChartBucket>, modifier: Modifier = Modifier) {
    val values = buckets.map { it.value.toFloat() }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(modelProducer, values) {
        modelProducer.runTransaction {
            columnSeries {
                series(values)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(rememberColumnCartesianLayer()),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(120.dp),
    )
}
