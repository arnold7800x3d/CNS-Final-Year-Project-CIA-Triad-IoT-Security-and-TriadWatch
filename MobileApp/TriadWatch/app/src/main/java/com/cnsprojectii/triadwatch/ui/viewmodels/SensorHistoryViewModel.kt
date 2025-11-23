package com.cnsprojectii.triadwatch.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cnsprojectii.triadwatch.R
import com.cnsprojectii.triadwatch.utils.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import javax.crypto.SecretKey

// --- Constants (copied from SensorViewModel) ---
private const val MQTT_BROKER_URL = "ssl://192.168.100.7:8883"
private const val MQTT_CLIENT_ID_PREFIX = "TriadWatchHistoryClient" // Use a unique prefix
private const val MQTT_USERNAME = "arnold"
private const val MQTT_PASSWORD = "7945"
private const val HISTORY_LIST_SIZE = 100 // How many points to keep for the charts

// --- Topics (copied from SensorViewModel) ---
private const val TOPIC_TEMPERATURE = "smart_environment/temperature"
private const val TOPIC_HUMIDITY = "smart_environment/humidity"
private const val TOPIC_LDR = "smart_environment/ldr"
private const val TOPIC_DISTANCE = "smart_environment/distance"

/**
 * A dedicated ViewModel for the History Screen.
 * It connects to MQTT and builds live history lists for the charts.
 * It is completely independent of SensorViewModel and does NOT use Firebase.
 */
class SensorHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SensorHistoryVM"
    private val decryptionKey: SecretKey by lazy { CryptoUtils.getDecryptionKey() }
    private var mqttClient: MqttAndroidClient? = null

    // --- State for History Screen Charts ---
    private val _temperatureHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val temperatureHistory = _temperatureHistory.asStateFlow()

    private val _humidityHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val humidityHistory = _humidityHistory.asStateFlow()

    private val _distanceHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val distanceHistory = _distanceHistory.asStateFlow()

    private val _ldrHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val ldrHistory = _ldrHistory.asStateFlow()

    init {
        // REMOVED: No longer fetches initial data from Firebase.
        // Charts will start empty and populate as live data arrives.

        // Connect to MQTT to get live, real-time updates
        viewModelScope.launch(Dispatchers.IO) {
            connectMqtt()
        }
    }

    private fun connectMqtt() {
        val appContext = getApplication<Application>().applicationContext
        val clientId = "TriadWatchAppClient_HistoryScreen"
        // Use a unique client ID to avoid conflicts with the other ViewModel
        mqttClient = MqttAndroidClient(appContext, MQTT_BROKER_URL, "${MQTT_CLIENT_ID_PREFIX}_${clientId}")

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.i(TAG, "MQTT Connection Complete.")
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "MQTT Connection Lost.", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payloadString = message?.toString()
                if (topic == null || payloadString == null) return

                viewModelScope.launch(Dispatchers.Main) {
                    try {
                        val jsonPayload = JSONObject(payloadString)
                        val encryptedData = jsonPayload.optString("cipher")
                        val expectedHash = jsonPayload.optString("hash")
                        processLiveSensorMessage(topic, encryptedData, expectedHash)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse MQTT JSON payload", e)
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        // SSL and connection options are identical to SensorViewModel
        val options = MqttConnectOptions().apply {
            userName = MQTT_USERNAME
            password = MQTT_PASSWORD.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            try {
                val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                val caInput: java.io.InputStream = appContext.resources.openRawResource(R.raw.deb11ca)
                val ca = caInput.use { cf.generateCertificate(it) as java.security.cert.X509Certificate }
                val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply { load(null, null) }
                keyStore.setCertificateEntry("ca", ca)
                val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
                socketFactory = javax.net.ssl.SSLContext.getInstance("TLSv1.2").apply { init(null, tmf.trustManagers, null) }.socketFactory
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up SSLSocketFactory for MQTT", e)
            }
        }

        try {
            mqttClient?.connect(options)
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT connection failed", e)
        }
    }

    private fun subscribeToTopics() {
        try {
            // We only need the topics that have charts
            val topics = arrayOf(TOPIC_TEMPERATURE, TOPIC_HUMIDITY, TOPIC_LDR, TOPIC_DISTANCE)
            val qos = intArrayOf(1, 1, 1, 1)
            mqttClient?.subscribe(topics, qos)
            Log.i(TAG, "Subscribed to chart topics.")
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT Subscription failed.", e)
        }
    }

    /**
     * Processes a LIVE message from MQTT and updates the history lists.
     */
    private fun processLiveSensorMessage(topic: String, encryptedData: String, expectedHash: String) {
        val decryptedValue = CryptoUtils.decrypt(encryptedData, decryptionKey)
        if (decryptedValue == null) {
            Log.w(TAG, "Decryption failed for live message on topic '$topic'")
            return
        }

        if (CryptoUtils.verifyHash(decryptedValue, expectedHash)) {
            // Sanitize the string to get only the number
            val numericValue = decryptedValue.replace(Regex("[^0-9.]"), "").toFloatOrNull()

            if (numericValue != null) {
                val newPoint = Pair(System.currentTimeMillis() / 1000f, numericValue)
                when (topic) {
                    TOPIC_TEMPERATURE -> _temperatureHistory.update { it.plus(newPoint).takeLast(HISTORY_LIST_SIZE) }
                    TOPIC_HUMIDITY -> _humidityHistory.update { it.plus(newPoint).takeLast(HISTORY_LIST_SIZE) }
                    TOPIC_DISTANCE -> _distanceHistory.update { it.plus(newPoint).takeLast(HISTORY_LIST_SIZE) }
                    TOPIC_LDR -> _ldrHistory.update { it.plus(newPoint).takeLast(HISTORY_LIST_SIZE) }
                }
            } else {
                Log.w(TAG, "Could not convert live value '$decryptedValue' to a number for chart.")
            }
        } else {
            Log.w(TAG, "Hash verification failed for live message on topic '$topic'")
        }
    }

    // REMOVED: All Firebase fetching functions (fetchInitialData, fetchFromFirebase) are gone.

    override fun onCleared() {
        super.onCleared()
        try {
            // Important to prevent connection leaks
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during MQTT disconnect.", e)
        }
    }
}
