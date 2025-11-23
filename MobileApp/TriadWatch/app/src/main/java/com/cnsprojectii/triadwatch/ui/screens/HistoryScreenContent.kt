package com.cnsprojectii.triadwatch.ui.screens

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.components.SensorLineChart
// --- IMPORTANT: CORRECTED IMPORT ---
import com.cnsprojectii.triadwatch.ui.viewmodels.SensorHistoryViewModel

@Composable
fun HistoryScreenContent(
    modifier: Modifier = Modifier,
    // --- IMPORTANT: USE THE CORRECT VIEWMODEL ---
    historyViewModel: SensorHistoryViewModel = viewModel()
) {
    // Collect the history data streams from the correct ViewModel
    val temperatureData by historyViewModel.temperatureHistory.collectAsState()
    val humidityData by historyViewModel.humidityHistory.collectAsState()
    val distanceData by historyViewModel.distanceHistory.collectAsState()
    val ldrData by historyViewModel.ldrHistory.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Live Sensor Data",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        ChartSection(
            title = "Temperature (°C)",
            dataPoints = temperatureData,
            color = Color.RED
        )

        ChartSection(
            title = "Humidity (%)",
            dataPoints = humidityData,
            color = Color.BLUE
        )

        ChartSection(
            title = "Distance (cm)",
            dataPoints = distanceData,
            color = Color.GREEN
        )

        ChartSection(
            title = "Light Intensity (LDR)",
            dataPoints = ldrData,
            color = Color.parseColor("#FFA500") // Orange
        )
    }
}

@Composable
private fun ChartSection(title: String, dataPoints: List<Pair<Float, Float>>, color: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        SensorLineChart(
            dataPoints = dataPoints,
            colorInt = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
