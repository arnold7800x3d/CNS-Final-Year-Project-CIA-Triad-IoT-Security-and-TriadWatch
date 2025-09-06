package com.cnsprojectii.triadwatch.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope // Ensure this is imported
import com.cnsprojectii.triadwatch.R // Import your app's R class for the CA cert
import com.cnsprojectii.triadwatch.ui.state.AllLEDsUiState
import com.cnsprojectii.triadwatch.ui.state.LEDState
import com.cnsprojectii.triadwatch.ui.viewmodels.LEDType
import kotlinx.coroutines.Dispatchers // For launching coroutines on Main
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

// --- MQTT Configuration (Ensure these are correct) ---
private const val MQTT_BROKER_URL = "ssl://192.168.1.8:8883" // Match SensorViewModel
private const val MQTT_CLIENT_ID_PREFIX = "TriadWatchAppClient_LED_" // Slightly different prefix
private const val MQTT_USERNAME = "arnold" // Match SensorViewModel
private const val MQTT_PASSWORD = "7945" // Match SensorViewModel

// --- MQTT Topics for Commands ---
private const val TOPIC_LED_WHITE_COMMAND = "bank_monitoring/led/white"
private const val TOPIC_LED_BLUE_COMMAND = "bank_monitoring/led/blue"

class LEDControlViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AllLEDsUiState())
    val uiState = _uiState.asStateFlow()

    private var mqttClient: MqttAndroidClient? = null // Renamed for consistency

    init {
        Log.d("LEDControlViewModel_MQTT", "Initializing and attempting to connect MQTT.")
        connectMqtt()
    }

    private fun connectMqtt() {
        val appContext = getApplication<Application>().applicationContext
        // Use MqttClient.generateClientId() for a unique ID part
        val clientId = "${MQTT_CLIENT_ID_PREFIX}${MqttClient.generateClientId()}"
        mqttClient = MqttAndroidClient(appContext, MQTT_BROKER_URL, clientId)

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                viewModelScope.launch(Dispatchers.Main) { // Launch on Main dispatcher
                    Log.i("LEDControlViewModel_MQTT", "MQTT Connection Complete. Reconnect: $reconnect. URI: $serverURI")
                    _uiState.update { it.copy(isMqttConnected = true) }
                    // No need to subscribe to topics for sending commands
                }
            }

            override fun connectionLost(cause: Throwable?) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.w("LEDControlViewModel_MQTT", "MQTT Connection Lost.", cause)
                    _uiState.update { currentState ->
                        currentState.copy(
                            isMqttConnected = false,
                            // Set error for LEDs if connection is lost
                            blueLEDState = currentState.blueLEDState.copy(
                                error = currentState.blueLEDState.error ?: "MQTT Disconnected",
                                isLoading = false // Stop loading if it was
                            ),
                            whiteLEDState = currentState.whiteLEDState.copy(
                                error = currentState.whiteLEDState.error ?: "MQTT Disconnected",
                                isLoading = false // Stop loading if it was
                            )
                        )
                    }
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("LEDControlViewModel_MQTT", "Unexpected message on topic '$topic'")
                // This ViewModel doesn't expect to receive messages for LED control status
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("LEDControlViewModel_MQTT", "MQTT Message Delivery Complete for token: ${token?.messageId}")
                // The actual state update happens in setLEDState's onSuccess for optimistic update
            }
        })

        val options = MqttConnectOptions().apply {
            userName = MQTT_USERNAME
            password = MQTT_PASSWORD.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true // Important for command-based interactions
            try {
                val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
                val caInput: InputStream = appContext.resources.openRawResource(R.raw.cacert) // Use your CA cert
                val ca: X509Certificate = caInput.use {
                    cf.generateCertificate(it) as X509Certificate
                }
                val keyStoreType = KeyStore.getDefaultType()
                val keyStore = KeyStore.getInstance(keyStoreType)
                keyStore.load(null, null)
                keyStore.setCertificateEntry("ca", ca)
                val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
                val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm)
                tmf.init(keyStore)
                val sslContext: SSLContext = SSLContext.getInstance("TLSv1.2") // Or TLSv1.3 if supported
                sslContext.init(null, tmf.trustManagers, null)
                this.socketFactory = sslContext.socketFactory
                Log.i("LEDControlViewModel_MQTT", "SSLSocketFactory configured successfully for LED VM.")
            } catch (e: Exception) {
                Log.e("LEDControlViewModel_MQTT", "Error setting up SSLSocketFactory for LED VM", e)
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isMqttConnected = false,
                            blueLEDState = it.blueLEDState.copy(error = "MQTT SSL Setup Error", isLoading = false),
                            whiteLEDState = it.whiteLEDState.copy(error = "MQTT SSL Setup Error", isLoading = false)
                        )
                    }
                }
                // Potentially prevent connection attempt if SSL setup fails critically
                return // Stop further connection attempt if SSL setup fails
            }
        }

        try {
            Log.i("LEDControlViewModel_MQTT", "Attempting to connect MQTT client for LED VM...")
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // connectComplete callback will handle the connected state update
                    Log.i("LEDControlViewModel_MQTT", "MQTT Connection initiated successfully for LED VM.")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.e("LEDControlViewModel_MQTT", "MQTT Connection initiation failed for LED VM.", exception)
                        _uiState.update { currentState ->
                            currentState.copy(
                                isMqttConnected = false,
                                blueLEDState = currentState.blueLEDState.copy(isLoading = false, error = "MQTT Conn. Failed"),
                                whiteLEDState = currentState.whiteLEDState.copy(isLoading = false, error = "MQTT Conn. Failed")
                            )
                        }
                    }
                }
            })
        } catch (e: MqttException) {
            viewModelScope.launch(Dispatchers.Main) {
                Log.e("LEDControlViewModel_MQTT", "MQTT MqttException during connect for LED VM.", e)
                _uiState.update { currentState ->
                    currentState.copy(
                        isMqttConnected = false,
                        blueLEDState = currentState.blueLEDState.copy(isLoading = false, error = "MQTT Error"),
                        whiteLEDState = currentState.whiteLEDState.copy(isLoading = false, error = "MQTT Error")
                    )
                }
            }
        }
    }

    fun setLEDState(ledType: LEDType, isOn: Boolean) {
        val currentMqttClient = mqttClient // Capture for thread safety
        if (currentMqttClient == null || !currentMqttClient.isConnected) {
            Log.w("LEDControlViewModel_MQTT", "MQTT client not connected. Cannot set LED state for $ledType.")
            viewModelScope.launch(Dispatchers.Main) { // Ensure UI updates are on main thread
                _uiState.update { currentState ->
                    val errorMsg = "MQTT Disconnected"
                    when (ledType) {
                        LEDType.BLUE -> currentState.copy(blueLEDState = currentState.blueLEDState.copy(error = errorMsg, isLoading = false))
                        LEDType.WHITE -> currentState.copy(whiteLEDState = currentState.whiteLEDState.copy(error = errorMsg, isLoading = false))
                    }
                }
            }
            connectMqtt() // Attempt to reconnect
            return
        }

        val topic = when (ledType) {
            LEDType.BLUE -> TOPIC_LED_BLUE_COMMAND
            LEDType.WHITE -> TOPIC_LED_WHITE_COMMAND
        }
        val messagePayload = if (isOn) "ON" else "OFF"

        viewModelScope.launch(Dispatchers.IO) { // Perform publish on IO dispatcher
            try {
                // Update UI for loading state (on Main thread)
                launch(Dispatchers.Main) {
                    _uiState.update { currentState ->
                        when (ledType) {
                            LEDType.BLUE -> currentState.copy(blueLEDState = currentState.blueLEDState.copy(isLoading = true, error = null))
                            LEDType.WHITE -> currentState.copy(whiteLEDState = currentState.whiteLEDState.copy(isLoading = true, error = null))
                        }
                    }
                }

                val mqttMessage = MqttMessage(messagePayload.toByteArray())
                mqttMessage.qos = 1 // At least once delivery
                mqttMessage.isRetained = false // Commands should generally not be retained

                currentMqttClient.publish(topic, mqttMessage, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("LEDControlViewModel_MQTT", "Successfully published '$messagePayload' to '$topic'")
                        viewModelScope.launch(Dispatchers.Main) { // Back to Main for UI update
                            _uiState.update { currentState ->
                                when (ledType) {
                                    LEDType.BLUE -> currentState.copy(
                                        blueLEDState = currentState.blueLEDState.copy(isLEDOn = isOn, isLoading = false, error = null)
                                    )
                                    LEDType.WHITE -> currentState.copy(
                                        whiteLEDState = currentState.whiteLEDState.copy(isLEDOn = isOn, isLoading = false, error = null)
                                    )
                                }
                            }
                        }
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("LEDControlViewModel_MQTT", "Failed to publish '$messagePayload' to '$topic'", exception)
                        viewModelScope.launch(Dispatchers.Main) { // Back to Main for UI update
                            _uiState.update { currentState ->
                                val errorMsg = "Publish Failed: ${exception?.message?.take(30) ?: "Unknown"}"
                                when (ledType) {
                                    LEDType.BLUE -> currentState.copy(blueLEDState = currentState.blueLEDState.copy(error = errorMsg, isLoading = false))
                                    LEDType.WHITE -> currentState.copy(whiteLEDState = currentState.whiteLEDState.copy(error = errorMsg, isLoading = false))
                                }
                            }
                        }
                    }
                })
            } catch (e: MqttException) {
                Log.e("LEDControlViewModel_MQTT", "MqttException when publishing to $topic", e)
                viewModelScope.launch(Dispatchers.Main) { // Back to Main for UI update
                    _uiState.update { currentState ->
                        val errorMsg = "MQTT Error: ${e.message?.take(30) ?: "Unknown"}"
                        when (ledType) {
                            LEDType.BLUE -> currentState.copy(blueLEDState = currentState.blueLEDState.copy(error = errorMsg, isLoading = false))
                            LEDType.WHITE -> currentState.copy(whiteLEDState = currentState.whiteLEDState.copy(error = errorMsg, isLoading = false))
                        }
                    }
                }
            }
        }
    }

    // Renamed from retryListeners to retryConnection as there are no listeners here.
    fun retryConnection() {
        Log.d("LEDControlViewModel_MQTT", "Retrying MQTT connection.")
        if (mqttClient?.isConnected == false) {
            connectMqtt()
        } else if (mqttClient == null) { // If client was never initialized or got nulled
            connectMqtt()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("LEDControlViewModel_MQTT", "ViewModel cleared. Disconnecting MQTT client for LED VM.")
        try {
            // No subscriptions to unsubscribe from in this command-sending VM
            mqttClient?.disconnect()
            mqttClient?.close() // Release resources
            Log.i("LEDControlViewModel_MQTT", "MQTT client disconnected and closed for LED VM.")
        } catch (e: MqttException) {
            Log.e("LEDControlViewModel_MQTT", "Error during MQTT disconnect/close for LED VM", e)
        }
        mqttClient = null
    }
}
