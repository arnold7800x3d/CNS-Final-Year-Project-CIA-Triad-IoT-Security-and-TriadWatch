package com.cnsprojectii.triadwatch.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.cnsprojectii.triadwatch.data.model.SensorTypeData
import com.cnsprojectii.triadwatch.ui.state.TempHumidityUiState
import com.cnsprojectii.triadwatch.utils.CryptoUtils
import javax.crypto.SecretKey
import kotlin.text.substringAfter

class SensorViewModel : ViewModel() {
    private val database =
        com.google.firebase.database.FirebaseDatabase.getInstance() // Get instance

    // Make sure this path matches your Firebase structure EXACTLY
    private val sensorsRef = database.getReference("bank_monitoring/sensors")

    private val _tempHumidityState = mutableStateOf(TempHumidityUiState())
    val tempHumidityState: State<TempHumidityUiState> = _tempHumidityState

    private val decryptionKey: SecretKey by lazy { CryptoUtils.getDecryptionKey() }

    private val temperatureListener: com.google.firebase.database.ValueEventListener
    private val humidityListener: com.google.firebase.database.ValueEventListener

    init {
        Log.d("SensorViewModel", "Initializing and attaching listeners.")

        temperatureListener = createSensorValueListener(isTemperature = true)
        humidityListener = createSensorValueListener(isTemperature = false)

        sensorsRef.child("temperature").addValueEventListener(temperatureListener)
        sensorsRef.child("humidity").addValueEventListener(humidityListener)
    }

    private fun createSensorValueListener(isTemperature: Boolean): com.google.firebase.database.ValueEventListener {
        return object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                Log.d(
                    "SensorViewModel",
                    "Data changed for ${if (isTemperature) "temperature" else "humidity"}: ${snapshot.value}"
                )
                val sensorData = snapshot.getValue(SensorTypeData::class.java)
                processSensorValue(sensorData, isTemperature, decryptionKey)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(
                    "SensorViewModel",
                    "Firebase listener cancelled for ${if (isTemperature) "temperature" else "humidity"}",
                    error.toException()
                )
                val currentStatus = _tempHumidityState.value.statusMessage
                val newError =
                    "Failed to load ${if (isTemperature) "temperature" else "humidity"}: ${error.message}"
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    statusMessage = if (currentStatus != null) "$currentStatus\n$newError" else newError,
                    temperature = if (isTemperature && _tempHumidityState.value.temperature == "Loading...") "Error" else _tempHumidityState.value.temperature,
                    humidity = if (!isTemperature && _tempHumidityState.value.humidity == "Loading...") "Error" else _tempHumidityState.value.humidity
                )
            }
        }
    }

    private fun processSensorValue(
        sensorData: SensorTypeData?,
        isTemperature: Boolean,
        key: SecretKey
    ) {
        if (sensorData?.latest != null && sensorData.hash != null) {
            Log.i(
                "SensorViewModel",
                "Processing ${if (isTemperature) "Temp" else "Hum"}: Encrypted='${sensorData.latest}', Hash='${sensorData.hash}'"
            )
            val decryptedValue = CryptoUtils.decrypt(sensorData.latest, key)

            if (decryptedValue != null) {
                Log.i(
                    "SensorViewModel",
                    "Decrypted ${if (isTemperature) "Temp" else "Hum"}: '$decryptedValue'"
                )
                val isVerified = CryptoUtils.verifyHash(decryptedValue, sensorData.hash)
                Log.i(
                    "SensorViewModel",
                    "Verification for ${if (isTemperature) "Temp" else "Hum"}: $isVerified"
                )

                val displayValue = if (isVerified) {
                    // Attempt to extract value, assuming format "Type:ValueUnit"
                    // e.g., "Temp:25.5°C" or "Humidity:60.0%"
                    if (isTemperature) {
                        decryptedValue.substringAfter("Temp:").trim().ifEmpty { decryptedValue }
                    } else {
                        decryptedValue.substringAfter("Humidity:").trim().ifEmpty { decryptedValue }
                    }
                } else {
                    "Verify Failed"
                }

                if (isTemperature) {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        temperature = displayValue,
                        isTemperatureVerified = isVerified,
                        lastUpdateTimestamp = sensorData.timestamp
                            ?: _tempHumidityState.value.lastUpdateTimestamp,
                        statusMessage = if (!isVerified && displayValue == "Verify Failed") "Temp data integrity fail." else _tempHumidityState.value.statusMessage?.replace(
                            "Temp data integrity fail.",
                            ""
                        )?.trim()
                    )
                } else {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        humidity = displayValue,
                        isHumidityVerified = isVerified,
                        lastUpdateTimestamp = sensorData.timestamp
                            ?: _tempHumidityState.value.lastUpdateTimestamp,
                        statusMessage = if (!isVerified && displayValue == "Verify Failed") "Hum data integrity fail." else _tempHumidityState.value.statusMessage?.replace(
                            "Hum data integrity fail.",
                            ""
                        )?.trim()
                    )
                }
            } else {
                Log.w(
                    "SensorViewModel",
                    "Decryption failed for ${if (isTemperature) "Temp" else "Hum"}"
                )
                if (isTemperature) {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        temperature = "Decrypt Error",
                        isTemperatureVerified = false
                    )
                } else {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        humidity = "Decrypt Error",
                        isHumidityVerified = false
                    )
                }
            }
        } else {
            Log.w(
                "SensorViewModel",
                "${if (isTemperature) "Temp" else "Hum"} data is missing 'latest' or 'hash', or is null. Data: $sensorData"
            )
            val placeholder = if (isTemperature) "Temp N/A" else "Hum N/A"
            if (isTemperature) {
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    temperature = placeholder,
                    isTemperatureVerified = false
                )
            } else {
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    humidity = placeholder,
                    isHumidityVerified = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("SensorViewModel", "ViewModel cleared. Removing Firebase listeners.")
        sensorsRef.child("temperature").removeEventListener(temperatureListener)
        sensorsRef.child("humidity").removeEventListener(humidityListener)
    }
}