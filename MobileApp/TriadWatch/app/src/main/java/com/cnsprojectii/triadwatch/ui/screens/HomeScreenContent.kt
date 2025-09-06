package com.cnsprojectii.triadwatch.ui.screens

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.components.LEDDisplayContent
import com.cnsprojectii.triadwatch.ui.state.SensorReadingsUiState
import com.cnsprojectii.triadwatch.ui.viewmodels.LEDType
import com.cnsprojectii.triadwatch.ui.viewmodels.SensorViewModel
import com.cnsprojectii.triadwatch.utils.formatTimestampForDisplay
import com.cnsprojectii.triadwatch.viewmodels.LEDControlViewModel

@Composable
fun HomeScreenContent(userEmail: String?) {
    val sensorViewModel: SensorViewModel = viewModel()
    // Use the renamed state variable from SensorViewModel
    val sensorReadingsState by sensorViewModel.sensorReadingsState // UPDATED HERE

    val ledControlViewModel: LEDControlViewModel = viewModel()
    val allLEDsUiState by ledControlViewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
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
        // Use the renamed state variable
        if (sensorReadingsState.lastUpdateTimestamp > 0) { // UPDATED HERE
            Text(
                text = "Last Sensor Update: ${formatTimestampForDisplay(sensorReadingsState.lastUpdateTimestamp)}", // UPDATED HERE
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
        Box(
            modifier = Modifier
                .fillMaxWidth(0.2f)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.LightGray)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    // Pass the renamed state variable
                    TemperatureHumidityContent(uiState = sensorReadingsState) // UPDATED HERE
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    // Pass the renamed state variable
                    DistanceContent(uiState = sensorReadingsState) // UPDATED HERE
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    // Pass the renamed state variable
                    ResistanceContent(uiState = sensorReadingsState) // UPDATED HERE
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    MotionContent(uiState = sensorReadingsState)
                }
            }


            // LED Control Row - Updated onClick logic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val blueLEDState = allLEDsUiState.blueLEDState
                LargeRoundedBox(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!allLEDsUiState.isMqttConnected) {
                            Toast.makeText(context, "MQTT Disconnected. Retrying...", Toast.LENGTH_SHORT).show()
                            ledControlViewModel.retryConnection()
                            // Optionally, you could also immediately try to send the command again after retryConnection
                            // ledControlViewModel.setLEDState(LEDType.BLUE, !blueLEDState.isLEDOn)
                        } else if (blueLEDState.error != null && !blueLEDState.isLoading) {
                            Log.d("HomeScreen", "Blue LED error displayed. Retrying command.")
                            Toast.makeText(context, "Retrying Blue LED command...", Toast.LENGTH_SHORT).show()
                            // Clear the error by re-attempting the command.
                            ledControlViewModel.setLEDState(LEDType.BLUE, !blueLEDState.isLEDOn)
                        } else if (!blueLEDState.isLoading) {
                            Log.d("HomeScreen", "Blue LED tapped. Current state: ${blueLEDState.isLEDOn}, toggling.")
                            ledControlViewModel.setLEDState(LEDType.BLUE, !blueLEDState.isLEDOn)
                        } else {
                            Log.d("HomeScreen", "Blue LED is currently loading/processing.")
                            Toast.makeText(context, "Blue LED processing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    // Display for Blue LED
                    if (blueLEDState.error != null && !blueLEDState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = blueLEDState.error.take(40), // Show the actual error, truncated
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
                        // Assuming LEDDisplayContent is structured to show loading and on/off state
                        LEDDisplayContent(
                            ledName = "Blue LED",
                            ledUiState = blueLEDState // Pass the LEDState
                        )
                    }
                }

                val whiteLEDState = allLEDsUiState.whiteLEDState
                LargeRoundedBox(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!allLEDsUiState.isMqttConnected) {
                            Toast.makeText(context, "MQTT Disconnected. Retrying...", Toast.LENGTH_SHORT).show()
                            ledControlViewModel.retryConnection()
                        } else if (whiteLEDState.error != null && !whiteLEDState.isLoading) {
                            Log.d("HomeScreen", "White LED error displayed. Retrying command.")
                            Toast.makeText(context, "Retrying White LED command...", Toast.LENGTH_SHORT).show()
                            ledControlViewModel.setLEDState(LEDType.WHITE, !whiteLEDState.isLEDOn)
                        } else if (!whiteLEDState.isLoading) {
                            Log.d("HomeScreen", "White LED tapped. Current state: ${whiteLEDState.isLEDOn}, toggling.")
                            ledControlViewModel.setLEDState(LEDType.WHITE, !whiteLEDState.isLEDOn)
                        } else {
                            Log.d("HomeScreen", "White LED is currently loading/processing.")
                            Toast.makeText(context, "White LED processing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    // Display for White LED
                    if (whiteLEDState.error != null && !whiteLEDState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = whiteLEDState.error.take(40), // Show the actual error, truncated
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
                        LEDDisplayContent(
                            ledName = "White LED",
                            ledUiState = whiteLEDState // Pass the LEDState
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // Use the renamed state variable and include checks for new sensor values
        val integrityMessage = when {
            sensorReadingsState.statusMessage != null -> sensorReadingsState.statusMessage
            sensorReadingsState.isTemperatureVerified &&
                    sensorReadingsState.isHumidityVerified &&
                    sensorReadingsState.isLdrResistanceVerified && // ADDED CHECK
                    sensorReadingsState.isDistanceVerified &&
                    sensorReadingsState.isMotionVerified -> "Sensor Data Integrity: Verified" // ADDED CHECK
            sensorReadingsState.temperature == "Loading..." ||
                    sensorReadingsState.humidity == "Loading..." ||
                    sensorReadingsState.ldrResistance == "Loading..." || // ADDED CHECK
                    sensorReadingsState.distance == "Loading..." ||
                    sensorReadingsState.motion == "Loading..." -> "Sensor Data Integrity: Checking..." // ADDED CHECK
            else -> "Sensor Data Integrity: Issues Detected"
        }
        Text(
            text = integrityMessage ?: "Sensor Data Integrity: Status Unavailable",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (sensorReadingsState.isTemperatureVerified &&
                sensorReadingsState.isHumidityVerified &&
                sensorReadingsState.isLdrResistanceVerified && // ADDED CHECK
                sensorReadingsState.isDistanceVerified && // ADDED CHECK
                sensorReadingsState.isMotionVerified &&
                sensorReadingsState.statusMessage == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}


@Composable
fun LargeRoundedBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(150.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun TemperatureHumidityContent(uiState: SensorReadingsUiState) {
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
                Color.Red
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                Color.Blue
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

        uiState.statusMessage?.let {
            if (it.contains("Temp", ignoreCase = true) || it.contains("Hum", ignoreCase = true) ||
                it.contains("LDR", ignoreCase = true) || it.contains("Dist", ignoreCase = true) // Check for new integrity messages
            ) {
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
fun DistanceContent(uiState: SensorReadingsUiState) { // UPDATED to accept uiState
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Distance", style = MaterialTheme.typography.titleMedium)
        Text(
            text = uiState.distance, // UPDATED
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isDistanceVerified &&
                !uiState.distance.contains("Error", ignoreCase = true) &&
                !uiState.distance.contains("Failed", ignoreCase = true) &&
                !uiState.distance.contains("N/A")
            )
                Color.Black // Example color
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (uiState.distance == "Verify Failed" || uiState.distance == "Decrypt Error") {
            Text(
                uiState.distance,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ResistanceContent(uiState: SensorReadingsUiState) { // UPDATED to accept uiState
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resistance", style = MaterialTheme.typography.titleMedium) // Changed "Resistance" to "LDR" for consistency with topic
        Text(
            text = uiState.ldrResistance, // UPDATED
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isLdrResistanceVerified &&
                !uiState.ldrResistance.contains("Error", ignoreCase = true) &&
                !uiState.ldrResistance.contains("Failed", ignoreCase = true) &&
                !uiState.ldrResistance.contains("N/A")
            )
                Color.Cyan
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (uiState.ldrResistance == "Verify Failed" || uiState.ldrResistance == "Decrypt Error") {
            Text(
                uiState.ldrResistance,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun MotionContent(uiState: SensorReadingsUiState) { // Accept SensorReadingsUiState
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Added for better vertical centering
    ) {
        Text("Motion", style = MaterialTheme.typography.titleMedium)

        val motionText = uiState.motion
        val isVerified = uiState.isMotionVerified
        val isLoading = motionText.contains("Loading...", ignoreCase = true)
        val isError = motionText.contains("Error", ignoreCase = true) ||
                motionText.contains("Failed", ignoreCase = true) ||
                motionText.contains("N/A", ignoreCase = true)

        val textColor = when {
            isLoading || !isVerified || isError -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Default/error color
            motionText.equals("Detected", ignoreCase = true) -> Color.Red
            motionText.equals("Not Detected", ignoreCase = true) -> Color.Green
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Fallback for unexpected values
        }

        Text(
            text = motionText,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )

        // Optionally, display verification status if there's an issue
        if (!isVerified && !isLoading && !isError) {
            Text(
                "Verify Failed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (motionText == "Decrypt Error") { // Specific error message
            Text(
                motionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}


// ESPLedContent and ArduinoLedContent remain unchanged for now
@Composable
fun BlueLedContent(
    modifier: Modifier = Modifier,
    currentLEDIsOn: Boolean = false,
    isLoading: Boolean,
    onLEDStateChange: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Blue LED", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (currentLEDIsOn) "On" else "Off",
            style = MaterialTheme.typography.bodyLarge,
            color = if (currentLEDIsOn) Color.Green else Color.Red
        )
    }
}

@Composable
fun WhiteLedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("White LED", style = MaterialTheme.typography.titleMedium)
        Text("Off", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
    }
}
