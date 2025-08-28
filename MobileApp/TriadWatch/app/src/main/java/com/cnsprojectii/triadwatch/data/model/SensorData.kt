package com.cnsprojectii.triadwatch.data.model

@com.google.firebase.database.IgnoreExtraProperties
data class SensorTypeData(
    val latest: String? = null,
    val hash: String? = null,
    val timestamp: Long? = null // Assuming ESP32 sends UNIX timestamp in seconds
)