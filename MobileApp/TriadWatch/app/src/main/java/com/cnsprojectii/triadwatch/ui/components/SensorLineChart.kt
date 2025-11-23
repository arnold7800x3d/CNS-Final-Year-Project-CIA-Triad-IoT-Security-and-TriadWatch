package com.cnsprojectii.triadwatch.ui.components

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A reusable Composable that displays a line chart which updates when its data changes.
 *
 * @param dataPoints List of data points, where each is a Pair of (Timestamp in seconds, Sensor Value).
 * @param colorInt The Android integer representation of the line color (e.g., Color.RED).
 */
@Composable
fun SensorLineChart(
    dataPoints: List<Pair<Float, Float>>,
    colorInt: Int,
    modifier: Modifier = Modifier
) {
    // Formatter to display timestamps on the X-axis in a readable "HH:mm:ss" format.
    val xAxisFormatter = object : ValueFormatter() {
        private val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        override fun getFormattedValue(value: Float): String {
            return format.format(Date(TimeUnit.SECONDS.toMillis(value.toLong())))
        }
    }

    AndroidView(
        // The `factory` block runs only once to initialize the chart's appearance and settings.
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                axisRight.isEnabled = false

                // X-Axis (Time) Styling
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.DKGRAY
                    valueFormatter = xAxisFormatter
                    labelRotationAngle = -45f
                }

                // Y-Axis (Value) Styling
                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = Color.DKGRAY
                }
            }
        },
        // The `update` block runs every time the `dataPoints` list changes.
        update = { chart ->
            if (dataPoints.isNotEmpty()) {
                val entries = dataPoints.map { Entry(it.first, it.second) }

                val dataSet = LineDataSet(entries, "Sensor Data").apply {
                    color = colorInt
                    setDrawCircles(false)
                    setDrawValues(false)
                    lineWidth = 2f
                    mode = LineDataSet.Mode.LINEAR
                }

                chart.data = LineData(dataSet)
                chart.notifyDataSetChanged() // Tell the chart data has changed
                chart.invalidate()           // Force the chart to redraw
            } else {
                // If there's no data, clear the chart to show it's empty.
                chart.clear()
                chart.invalidate()
            }
        },
        modifier = modifier
    )
}
