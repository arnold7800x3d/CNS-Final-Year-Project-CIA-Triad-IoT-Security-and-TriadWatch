package com.cnsprojectii.triadwatch.ui.state

data class SensorReadingsUiState(
    val temperature: String = "Loading...",
    val isTemperatureVerified: Boolean = false,
    val humidity: String = "Loading...",
    val isHumidityVerified: Boolean = false,
    val ldrResistance: String = "Loading...",       // New field for LDR
    val isLdrResistanceVerified: Boolean = false,  // New field
    val distance: String = "Loading...",           // New field for Distance
    val isDistanceVerified: Boolean = false,       // New field
    val lastUpdateTimestamp: Long = 0L,
    val statusMessage: String? = null
)
