package com.cnsprojectii.triadwatch.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.components.LEDDisplayContent
import com.cnsprojectii.triadwatch.ui.state.TempHumidityUiState
import com.cnsprojectii.triadwatch.ui.viewmodels.SensorViewModel
import com.cnsprojectii.triadwatch.utils.formatTimestampForDisplay
import com.cnsprojectii.triadwatch.viewmodels.LEDControlViewModel
import com.cnsprojectii.triadwatch.viewmodels.LEDType

@Composable
fun HomeScreenContent(userEmail: String?) {
    val sensorViewModel: SensorViewModel = viewModel()
    // Corrected: If tempHumidityState is State<T>, no .collectAsState() needed
    val tempHumidityState by sensorViewModel.tempHumidityState

    val ledControlViewModel: LEDControlViewModel = viewModel()
    // Correct: uiState is StateFlow<T>, so .collectAsState() is needed
    val allLEDsUiState by ledControlViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp), // Adjust as needed, consider Scaffold for proper padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        val displayEmail = userEmail ?: "Not logged in"
        Text(
            text = "Welcome, $displayEmail!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (tempHumidityState.lastUpdateTimestamp > 0) {
            Text(
                text = "Last Sensor Update: ${formatTimestampForDisplay(tempHumidityState.lastUpdateTimestamp)}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Waiting for sensor data...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box( // Decorative bar
            modifier = Modifier
                .fillMaxWidth(0.2f)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.LightGray)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Grid for sensor boxes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    TemperatureHumidityContent(uiState = tempHumidityState)
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    DistanceContent()
                }
            }

            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    ResistanceContent()
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    MotionContent()
                }
            }

            // --- Third row: THIS IS WHERE THE TWO DISTINCT LED BOXES ARE CREATED ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Box 1: Blue LED ---
                val blueLEDState = allLEDsUiState.blueLEDState
                LargeRoundedBox(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (blueLEDState.error != null && !blueLEDState.isLoading) {
                            Log.d("HomeScreen", "Blue LED error retry tapped.")
                            ledControlViewModel.retryListeners()
                        } else if (!blueLEDState.isLoading) {
                            Log.d("HomeScreen", "Blue LED tapped. Current state: ${blueLEDState.isLEDOn}, toggling.")
                            ledControlViewModel.setLEDState(LEDType.BLUE, !blueLEDState.isLEDOn)
                        }
                    }
                ) {
                    // Content inside the Blue LED's LargeRoundedBox
                    if (blueLEDState.error != null && !blueLEDState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Blue LED Error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "(Tap to retry)",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        LEDDisplayContent( // Using the generic display content
                            ledName = "Blue LED",
                            ledUiState = blueLEDState
                        )
                    }
                }

                // --- Box 2: White LED ---
                val whiteLEDState = allLEDsUiState.whiteLEDState
                LargeRoundedBox(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (whiteLEDState.error != null && !whiteLEDState.isLoading) {
                            Log.d("HomeScreen", "White LED error retry tapped.")
                            ledControlViewModel.retryListeners()
                        } else if (!whiteLEDState.isLoading) {
                            Log.d("HomeScreen", "White LED tapped. Current state: ${whiteLEDState.isLEDOn}, toggling.")
                            ledControlViewModel.setLEDState(LEDType.WHITE, !whiteLEDState.isLEDOn)
                        }
                    }
                ) {
                    // Content inside the White LED's LargeRoundedBox
                    if (whiteLEDState.error != null && !whiteLEDState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "White LED Error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "(Tap to retry)",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        LEDDisplayContent( // Using the generic display content
                            ledName = "White LED",
                            ledUiState = whiteLEDState
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        val integrityMessage = when {
            tempHumidityState.statusMessage != null -> tempHumidityState.statusMessage
            tempHumidityState.isTemperatureVerified && tempHumidityState.isHumidityVerified -> "Sensor Data Integrity: Verified"
            tempHumidityState.temperature == "Loading..." || tempHumidityState.humidity == "Loading..." -> "Sensor Data Integrity: Checking..."
            else -> "Sensor Data Integrity: Issues Detected"
        }
        Text(
            text = integrityMessage ?: "Sensor Data Integrity: Status Unavailable",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (tempHumidityState.isTemperatureVerified && tempHumidityState.isHumidityVerified && tempHumidityState.statusMessage == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}


@Composable
fun LargeRoundedBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, // Optional click handler
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            //.aspectRatio(1.5f) // square
            .height(150.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f)) // transparent dark gray color
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(16.dp), // padding for content in the box
        contentAlignment = Alignment.Center // Center content
    ) {
        content()
    }
}

@Composable
fun TemperatureHumidityContent(uiState: TempHumidityUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Temperature", style = MaterialTheme.typography.titleMedium)
        Text(
            text = uiState.temperature,
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isTemperatureVerified &&
                !uiState.temperature.contains("Error", ignoreCase = true) &&
                !uiState.temperature.contains("Failed", ignoreCase = true) &&
                !uiState.temperature.contains("N/A")
            )
                Color.Red // Or MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Muted color
        )
        if (uiState.temperature == "Verify Failed" || uiState.temperature == "Decrypt Error") {
            Text(
                uiState.temperature,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Humidity", style = MaterialTheme.typography.titleMedium)
        Text(
            text = uiState.humidity,
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isHumidityVerified &&
                !uiState.humidity.contains("Error", ignoreCase = true) &&
                !uiState.humidity.contains("Failed", ignoreCase = true) &&
                !uiState.humidity.contains("N/A")
            )
                Color.Blue // Or MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (uiState.humidity == "Verify Failed" || uiState.humidity == "Decrypt Error") {
            Text(
                uiState.humidity,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Display overall status message if any, specific to this sensor box
        uiState.statusMessage?.let {
            if (it.contains("Temp", ignoreCase = true) || it.contains(
                    "Hum",
                    ignoreCase = true
                )
            ) { // Filter for relevant messages
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DistanceContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Distance", style = MaterialTheme.typography.titleMedium)
        Text("0.0 cm", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ResistanceContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resistance", style = MaterialTheme.typography.titleMedium)
        Text("0 Ohms", style = MaterialTheme.typography.bodyLarge, color = Color.Cyan)
    }
}

@Composable
fun MotionContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Motion", style = MaterialTheme.typography.titleMedium)
        Text("Not Detected", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
    }
}

@Composable
fun ESPLedContent(
    modifier: Modifier = Modifier,
    currentLEDIsOn: Boolean = false, // OFF,
    isLoading: Boolean,
    onLEDStateChange: (Boolean) -> Unit
) {
    // val currentLEDIsOn = initialLEDState

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ESP LED", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (currentLEDIsOn) "On" else "Off",
            style = MaterialTheme.typography.bodyLarge,
            color = if (currentLEDIsOn) Color.Green else Color.Red
        )
    }
}

@Composable
fun ArduinoLedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Arduino LED", style = MaterialTheme.typography.titleMedium)
        Text("Off", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
    }
}