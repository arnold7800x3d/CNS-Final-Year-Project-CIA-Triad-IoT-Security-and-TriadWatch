package com.cnsprojectii.triadwatch.ui.state

data class TempHumidityUiStates(
    val temperature: String = "Loading...",
    val humidity: String = "Loading...",
    val isTemperatureVerified: Boolean = false,
    val isHumidityVerified: Boolean = false,
    val lastUpdateTimestamp: Long = 0L,
    val statusMessage: String? = null
)