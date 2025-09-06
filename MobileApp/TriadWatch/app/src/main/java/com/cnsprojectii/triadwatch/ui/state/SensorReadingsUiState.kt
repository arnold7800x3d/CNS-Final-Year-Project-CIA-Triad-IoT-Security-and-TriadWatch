package com.cnsprojectii.triadwatch.ui.state

data class SensorReadingsUiState(
    val temperature: String = "Loading...",
    val isTemperatureVerified: Boolean = false,
    val humidity: String = "Loading...",
    val isHumidityVerified: Boolean = false,
    val ldrResistance: String = "Loading...",       // New field for LDR
    val isLdrResistanceVerified: Boolean = false,  // New field
    val distance: String = "Loading...",           // New field for Distance
    val isDistanceVerified: Boolean = false,
    val motion: String = "Loading...",
    val isMotionVerified: Boolean = false,// New field
    val lastUpdateTimestamp: Long = 0L,
    val statusMessage: String? = null
)

// Represents the state of a single LED
data class LEDState(
    val isLEDOn: Boolean = false,
    val isLoading: Boolean = false, // For when a command is sent, waiting for confirmation
    val error: String? = null
)

// Represents the combined state of all controllable LEDs
data class AllLEDsUiState(
    val blueLEDState: LEDState = LEDState(isLoading = false), // Start as loading
    val whiteLEDState: LEDState = LEDState(isLoading = false), // Start as loading
    val isMqttConnected: Boolean = false // Overall MQTT connection status for UI feedback
)
