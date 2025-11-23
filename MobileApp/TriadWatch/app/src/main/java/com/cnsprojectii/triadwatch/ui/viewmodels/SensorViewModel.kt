package com.cnsprojectii.triadwatch.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.cnsprojectii.triadwatch.R // Import your app's R class
import com.cnsprojectii.triadwatch.ui.state.SensorReadingsUiState // Already updated, good!
import com.cnsprojectii.triadwatch.utils.CryptoUtils
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import javax.crypto.SecretKey
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
//import androidx.preference.contains
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.text.replace

// MQTT Configuration
private const val MQTT_BROKER_URL = "ssl://192.168.100.7:8883"
private const val MQTT_CLIENT_ID_PREFIX = "TriadWatchAppClient"
private const val MQTT_USERNAME = "arnold"
private const val MQTT_PASSWORD = "7945"

private const val TOPIC_TEMPERATURE = "smart_environment/temperature"
private const val TOPIC_HUMIDITY = "smart_environment/humidity"
private const val TOPIC_LDR = "smart_environment/ldr" // New LDR topic
private const val TOPIC_DISTANCE = "smart_environment/distance" // New Distance topic
private const val TOPIC_MOTION = "smart_environment/motion"

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val _sensorReadingsState = mutableStateOf(SensorReadingsUiState()) // Renamed
    val sensorReadingsState: State<SensorReadingsUiState> = _sensorReadingsState // Renamed

    private val decryptionKey: SecretKey by lazy { CryptoUtils.getDecryptionKey() }

    private var mqttClient: MqttAndroidClient? = null

    init {
        Log.d("SensorViewModel_MQTT", "Initializing and attempting to connect MQTT.")
        connectMqtt()
    }

    fun retryConnection() {
        Log.d("SensorViewModel_MQTT", "Retrying MQTT connection...")
        connectMqtt()
    }

    private fun connectMqtt() {
        val appContext = getApplication<Application>().applicationContext
        val clientId = "TriadWatchAppClient_HomeScreen"
        mqttClient = MqttAndroidClient(appContext, MQTT_BROKER_URL, "${MQTT_CLIENT_ID_PREFIX}_${clientId}")

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.i("SensorViewModel_MQTT", "MQTT Connection Complete. Reconnect: $reconnect. URI: $serverURI")
                    _sensorReadingsState.value = _sensorReadingsState.value.copy(statusMessage = null)
                    subscribeToTopics()
                }
            }

            override fun connectionLost(cause: Throwable?) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.w("SensorViewModel_MQTT", "MQTT Connection Lost.", cause)
                    val currentState = _sensorReadingsState.value
                    _sensorReadingsState.value = currentState.copy(
                        statusMessage = "MQTT Connection Lost: ${cause?.message}",
                        temperature = if (currentState.temperature.contains("Loading")) "N/A" else currentState.temperature,
                        humidity = if (currentState.humidity.contains("Loading")) "N/A" else currentState.humidity,
                        ldrResistance = if (currentState.ldrResistance.contains("Loading")) "N/A" else currentState.ldrResistance, // Updated
                        distance = if (currentState.distance.contains("Loading")) "N/A" else currentState.distance // Updated
                    )
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) {
                    Log.w("SensorViewModel_MQTT", "Null topic or message received.")
                    return
                }
                val payloadString = String(message.payload)
                Log.d("SensorViewModel_MQTT", "Message arrived on topic '$topic': $payloadString")

                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        val jsonPayload = JSONObject(payloadString)
                        val encryptedBase64Data = jsonPayload.optString("cipher")
                        val expectedHashHex = jsonPayload.optString("hash")

                        if (encryptedBase64Data.isEmpty() || expectedHashHex.isEmpty()) {
                            Log.e("SensorViewModel_MQTT", "MQTT JSON payload missing 'cipher' or 'hash'. Payload: $payloadString")
                            updateStateWithError(topic, "Malformed Payload")
                            return@launch
                        }
                        processSensorMessage(topic, encryptedBase64Data, expectedHashHex)
                    } catch (e: Exception) {
                        Log.e("SensorViewModel_MQTT", "Failed to parse MQTT JSON payload: $payloadString", e)
                        updateStateWithError(topic, "Payload Parse Error")
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not used for subscribers
            }
        })

        val options = MqttConnectOptions().apply {
            userName = MQTT_USERNAME
            password = MQTT_PASSWORD.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            try {
                val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                val caInput: java.io.InputStream = appContext.resources.openRawResource(R.raw.deb11ca)
                val ca: java.security.cert.X509Certificate = caInput.use {
                    cf.generateCertificate(it) as java.security.cert.X509Certificate
                }
                val keyStoreType = java.security.KeyStore.getDefaultType()
                val keyStore = java.security.KeyStore.getInstance(keyStoreType)
                keyStore.load(null, null)
                keyStore.setCertificateEntry("ca", ca)
                val tmfAlgorithm = javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                val tmf = javax.net.ssl.TrustManagerFactory.getInstance(tmfAlgorithm)
                tmf.init(keyStore)
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, tmf.trustManagers, null)
                this.socketFactory = sslContext.socketFactory
                Log.i("SensorViewModel_MQTT", "SSLSocketFactory configured successfully with CA cert from res/raw.")
            } catch (e: Exception) {
                Log.e("SensorViewModel_MQTT", "Error setting up SSLSocketFactory for MQTT from res/raw", e)
                viewModelScope.launch(Dispatchers.Main) {
                    _sensorReadingsState.value = _sensorReadingsState.value.copy(statusMessage = "MQTT SSL Setup Error (Raw CA)")
                }
            }
        }

        try {
            Log.i("SensorViewModel_MQTT", "Attempting to connect MQTT client...")
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("SensorViewModel_MQTT", "MQTT Connection initiated successfully.")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.e("SensorViewModel_MQTT", "MQTT Connection initiation failed.", exception)
                        _sensorReadingsState.value = _sensorReadingsState.value.copy(
                            statusMessage = "MQTT Connection Failed: ${exception?.message}",
                            temperature = "N/A",
                            humidity = "N/A",
                            ldrResistance = "N/A", // Updated
                            distance = "N/A", // Updated
                            motion = "N/A"
                        )
                    }
                }
            })
        } catch (e: MqttException) {
            viewModelScope.launch(Dispatchers.Main) {
                Log.e("SensorViewModel_MQTT", "MQTT MqttException during connect.", e)
                _sensorReadingsState.value = _sensorReadingsState.value.copy(
                    statusMessage = "MQTT Error: ${e.message}",
                    temperature = "N/A",
                    humidity = "N/A",
                    ldrResistance = "N/A", // Updated
                    distance = "N/A", // Updated
                    motion = "N/A"
                )
            }
        }
    }

    private fun subscribeToTopics() {
        try {
            mqttClient?.subscribe(TOPIC_TEMPERATURE, 1)
            mqttClient?.subscribe(TOPIC_HUMIDITY, 1)
            mqttClient?.subscribe(TOPIC_LDR, 1) // Subscribe to LDR
            mqttClient?.subscribe(TOPIC_DISTANCE, 1) // Subscribe to Distance
            mqttClient?.subscribe(TOPIC_MOTION, 1) // Subscribe to Motion topic
            Log.i("SensorViewModel_MQTT", "Subscribed to MQTT topics: $TOPIC_TEMPERATURE, $TOPIC_HUMIDITY, $TOPIC_LDR, $TOPIC_DISTANCE")
        } catch (e: MqttException) {
            Log.e("SensorViewModel_MQTT", "MQTT Subscription failed.", e)
            _sensorReadingsState.value = _sensorReadingsState.value.copy(statusMessage = "MQTT Subscribe Error: ${e.message}")
        }
    }

    private fun processSensorMessage(topic: String, encryptedData: String, expectedHash: String) {
        val decryptedValue = CryptoUtils.decrypt(encryptedData, decryptionKey)
        val currentTimestamp = System.currentTimeMillis() / 1000

        if (decryptedValue != null) {
            Log.i("SensorViewModel_MQTT", "Decrypted for topic '$topic': '$decryptedValue'")
            val isVerified = CryptoUtils.verifyHash(decryptedValue, expectedHash)
            Log.i("SensorViewModel_MQTT", "Verification for topic '$topic': $isVerified")

            val displayValue = if (isVerified) {
                // For LDR and Distance, no prefix is expected.
                // For Temp and Humidity, original prefix logic is kept.
                val prefix = when (topic) {
                    TOPIC_TEMPERATURE -> "Temp:"
                    TOPIC_HUMIDITY -> "Humidity:"
                    TOPIC_LDR -> "LDR:"           // Placeholder for your LDR prefix
                    TOPIC_DISTANCE -> "Distance:"
                    TOPIC_MOTION -> ""
                    else -> "" // No prefix for LDR and Distance
                }
                decryptedValue.substringAfter(prefix, "").trim().ifEmpty { decryptedValue }
            } else {
                "Verify Failed"
            }
            updateStateWithValue(topic, displayValue, isVerified, currentTimestamp)
            saveEncryptedToFirebase(topic, encryptedData, expectedHash, currentTimestamp)
        } else {
            Log.w("SensorViewModel_MQTT", "Decryption failed for topic '$topic'")
            updateStateWithError(topic, "Decrypt Error")
        }
    }

    private fun updateStateWithValue(topic: String, value: String, isVerified: Boolean, timestamp: Long) {
        val current = _sensorReadingsState.value
        var newStatus = current.statusMessage
        val integrityFailMsgTemp = "Temp data integrity fail."
        val integrityFailMsgHum = "Hum data integrity fail."
        val integrityFailMsgLdr = "LDR data integrity fail." // New
        val integrityFailMsgDist = "Dist data integrity fail." // New
        val integrityFailMsgMotion = "Motion data integrity fail." // New for motion

        when (topic) {
            TOPIC_TEMPERATURE -> {
                newStatus = if (!isVerified && value == "Verify Failed") {
                    if (newStatus?.contains(integrityFailMsgTemp) == false) "${newStatus ?: ""} $integrityFailMsgTemp".trim() else integrityFailMsgTemp
                } else {
                    newStatus?.replace(integrityFailMsgTemp, "")?.trim()?.ifEmpty { null }
                }
                _sensorReadingsState.value = current.copy(
                    temperature = value,
                    isTemperatureVerified = isVerified,
                    lastUpdateTimestamp = timestamp,
                    statusMessage = newStatus
                )
            }
            TOPIC_HUMIDITY -> {
                newStatus = if (!isVerified && value == "Verify Failed") {
                    if (newStatus?.contains(integrityFailMsgHum) == false) "${newStatus ?: ""} $integrityFailMsgHum".trim() else integrityFailMsgHum
                } else {
                    newStatus?.replace(integrityFailMsgHum, "")?.trim()?.ifEmpty { null }
                }
                _sensorReadingsState.value = current.copy(
                    humidity = value,
                    isHumidityVerified = isVerified,
                    lastUpdateTimestamp = timestamp,
                    statusMessage = newStatus
                )
            }
            TOPIC_LDR -> { // New case for LDR
                newStatus = if (!isVerified && value == "Verify Failed") {
                    if (newStatus?.contains(integrityFailMsgLdr) == false) "${newStatus ?: ""} $integrityFailMsgLdr".trim() else integrityFailMsgLdr
                } else {
                    newStatus?.replace(integrityFailMsgLdr, "")?.trim()?.ifEmpty { null }
                }
                _sensorReadingsState.value = current.copy(
                    ldrResistance = value,
                    isLdrResistanceVerified = isVerified,
                    lastUpdateTimestamp = timestamp,
                    statusMessage = newStatus
                )
            }
            TOPIC_DISTANCE -> { // New case for Distance
                newStatus = if (!isVerified && value == "Verify Failed") {
                    if (newStatus?.contains(integrityFailMsgDist) == false) "${newStatus ?: ""} $integrityFailMsgDist".trim() else integrityFailMsgDist
                } else {
                    newStatus?.replace(integrityFailMsgDist, "")?.trim()?.ifEmpty { null }
                }
                _sensorReadingsState.value = current.copy(
                    distance = value,
                    isDistanceVerified = isVerified,
                    lastUpdateTimestamp = timestamp,
                    statusMessage = newStatus
                )
            }
            TOPIC_MOTION -> { // New case for Motion
                newStatus = if (!isVerified && value == "Verify Failed") {
                    if (newStatus?.contains(integrityFailMsgMotion) == false) "${newStatus ?: ""} $integrityFailMsgMotion".trim() else integrityFailMsgMotion
                } else {
                    newStatus?.replace(integrityFailMsgMotion, "")?.trim()?.ifEmpty { null }
                }
                _sensorReadingsState.value = current.copy(
                    motion = value, // Update motion state
                    isMotionVerified = isVerified, // Update motion verification
                    lastUpdateTimestamp = timestamp,
                    statusMessage = newStatus
                )
            }
        }
    }

    private fun updateStateWithError(topic: String, errorType: String) {
        val current = _sensorReadingsState.value
        when (topic) {
            TOPIC_TEMPERATURE -> {
                _sensorReadingsState.value = current.copy(
                    temperature = errorType,
                    isTemperatureVerified = false
                )
            }
            TOPIC_HUMIDITY -> {
                _sensorReadingsState.value = current.copy(
                    humidity = errorType,
                    isHumidityVerified = false
                )
            }
            TOPIC_LDR -> { // New case for LDR
                _sensorReadingsState.value = current.copy(
                    ldrResistance = errorType,
                    isLdrResistanceVerified = false
                )
            }
            TOPIC_DISTANCE -> { // New case for Distance
                _sensorReadingsState.value = current.copy(
                    distance = errorType,
                    isDistanceVerified = false
                )
            }
            TOPIC_MOTION -> {
                _sensorReadingsState.value = current.copy(
                    motion = errorType,
                    isMotionVerified = false
                )
            }
        }
    }

    private fun saveEncryptedToFirebase(topic: String, cipher: String, hash: String, timestamp: Long) {
        val database = FirebaseDatabase.getInstance()
        val ref = database.reference.child("smart_environment_encrypted")

        val data = mapOf(
            "topic" to topic,
            "cipher" to cipher,
            "hash" to hash,
            "timestamp" to timestamp
        )

        ref.child(topic.replace("/", "_")).push().setValue(data)
            .addOnSuccessListener { Log.i("Firebase", "Encrypted data saved for $topic") }
            .addOnFailureListener { e -> Log.e("Firebase", "Failed to save data for $topic", e) }
    }




    override fun onCleared() {
        super.onCleared()
        Log.d("SensorViewModel_MQTT", "ViewModel cleared. Disconnecting MQTT client.")

        try {
            mqttClient?.let { client ->
                // Safely unsubscribe only if connected
                if (client.isConnected) {
                    try {
                        client.unsubscribe(
                            arrayOf(TOPIC_TEMPERATURE, TOPIC_HUMIDITY, TOPIC_LDR, TOPIC_DISTANCE, TOPIC_MOTION)
                        )
                        client.disconnect()
                    } catch (e: MqttException) {
                        Log.w("SensorViewModel_MQTT", "Error during unsubscribe/disconnect: ${e.message}")
                    }
                }

                try {
                    client.close()
                    Log.i("SensorViewModel_MQTT", "MQTT client disconnected and closed.")
                } catch (e: IllegalArgumentException) {
                    // This is the crash you saw
                    Log.w("SensorViewModel_MQTT", "MQTT client already closed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SensorViewModel_MQTT", "Unexpected error during MQTT cleanup", e)
        } finally {
            mqttClient = null
        }
    }

}
