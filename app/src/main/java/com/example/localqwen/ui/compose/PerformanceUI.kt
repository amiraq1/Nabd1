package com.example.localqwen.ui.compose

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun PerformanceChart(
    dataPoints: List<Float>,
    label: String,
    lineColor: Int = AndroidColor.RED,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                setDrawGridBackground(false)
                setScaleEnabled(false)
                setPinchZoom(false)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = AndroidColor.GRAY
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String = ""
                    }
                }
                
                axisLeft.apply {
                    textColor = AndroidColor.GRAY
                    setDrawGridLines(true)
                    gridColor = AndroidColor.LTGRAY
                }
                
                axisRight.isEnabled = false
                legend.isEnabled = true
                legend.textColor = AndroidColor.GRAY
            }
        },
        update = { chart ->
            val entries = dataPoints.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }
            
            val dataSet = LineDataSet(entries, label).apply {
                color = lineColor
                setDrawCircles(true)
                setCircleColor(lineColor)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = lineColor
                fillAlpha = 30
            }
            
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
