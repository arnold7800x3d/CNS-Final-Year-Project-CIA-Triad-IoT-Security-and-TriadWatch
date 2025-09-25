package com.cnsprojectii.triadwatch.viewmodels // Or your ViewModels package

import android.app.Application // Import Application
import android.util.Log
import androidx.compose.animation.core.copy
import androidx.lifecycle.AndroidViewModel // Use AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cnsprojectii.triadwatch.R // For CA cert
import com.cnsprojectii.triadwatch.ui.state.OtpUiState // Your OtpUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

// --- MQTT Configuration (Match your LEDControlViewModel or centralize if possible) ---
private const val MQTT_BROKER_URL = "ssl://192.168.1.8:8883" // Keep consistent
private const val MQTT_CLIENT_ID_PREFIX =
    "TriadWatchAppClient_OTP_" // Unique prefix for this client
private const val MQTT_USERNAME = "arnold" // Keep consistent
private const val MQTT_PASSWORD = "7945"   // Keep consistent

// --- MQTT Topic for OTP Request ---
private const val TOPIC_OTP_REQUEST = "bank_monitoring/otpRequest"
private const val TOPIC_OTP_RESPONSE = "bank_monitoring/otpResponse"


class OtpViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OtpUiState())
    val uiState: StateFlow<OtpUiState> = _uiState.asStateFlow()

    private var mqttClient: MqttAndroidClient? = null
    private var isMqttManuallyDisconnected: Boolean =
        false // To prevent auto-reconnect on deliberate disconnect

    init {
        Log.d("OtpViewModel_MQTT", "Initializing and attempting to connect MQTT for OTP.")
        connectMqtt()
    }

    private fun isConnected(): Boolean =
        mqttClient?.isConnected == true && !isMqttManuallyDisconnected

    private fun connectMqtt() {
        if (isConnected() || isMqttManuallyDisconnected) {
            Log.d(
                "OtpViewModel_MQTT",
                "MQTT already connected or manually disconnected. Skipping new connection attempt."
            )
            // Update UI if needed, e.g. if isMqttManuallyDisconnected is true, show specific message
            if (isConnected()) { // if already connected, ensure UI state reflects that
                _uiState.update { it.copy(otpRequestError = null) } // Clear any previous connection error
            }
            return
        }

        val appContext = getApplication<Application>().applicationContext
        val clientId = "${MQTT_CLIENT_ID_PREFIX}${MqttClient.generateClientId()}"
        mqttClient = MqttAndroidClient(appContext, MQTT_BROKER_URL, clientId)

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.i(
                        "OtpViewModel_MQTT",
                        "MQTT Connection Complete for OTP. Reconnect: $reconnect. URI: $serverURI"
                    )
                    // No specific subscriptions needed for just sending an OTP request
                    _uiState.update { it.copy(otpRequestError = null) } // Clear any connection error
                }

                mqttClient?.subscribe(TOPIC_OTP_RESPONSE, 1, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("OtpViewModel_MQTT", "Subscribed to OTP response topic successfully.")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("OtpViewModel_MQTT", "Failed to subscribe to OTP response topic", exception)
                    }
                })
            }

            override fun connectionLost(cause: Throwable?) {
                viewModelScope.launch(Dispatchers.Main) {
                    Log.w("OtpViewModel_MQTT", "MQTT Connection Lost for OTP.", cause)
                    _uiState.update { currentState ->
                        currentState.copy(
                            isRequestingOtp = false, // Stop loading if it was
                            otpRequestError = currentState.otpRequestError
                                ?: "MQTT Disconnected: ${cause?.message}",
                            otpRequestSuccessMessage = null
                        )
                    }
                    // Attempt to reconnect only if not manually disconnected
                    if (!isMqttManuallyDisconnected) {
                        Log.d(
                            "OtpViewModel_MQTT",
                            "Attempting to reconnect after connection loss..."
                        )
                        // Simple delay before reconnect attempt to avoid spamming
                        launch {
                            kotlinx.coroutines.delay(5000)
                            connectMqtt()
                        }
                    }
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("OtpViewModel_MQTT", "Message arrived on topic '$topic': ${message.toString()}")
                if (topic == TOPIC_OTP_RESPONSE) {
                    val response = message.toString()
                    viewModelScope.launch(Dispatchers.Main) {
                        if (response == "SUCCESS") {
                            _uiState.update {
                                it.copy(isVerifyingOtp = false, otpVerificationError = null, navigateToHome = true)
                            }
                            updateOtpVerificationTime()
                        } else {
                            _uiState.update {
                                it.copy(isVerifyingOtp = false, otpVerificationError = "Invalid OTP")
                            }
                        }
                    }
                }
            }


            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d(
                    "OtpViewModel_MQTT",
                    "MQTT Message Delivery Complete for OTP token: ${token?.messageId}"
                )
                // UI updates for OTP request success/failure are handled in the publish action listeners
            }
        })

        val options = MqttConnectOptions().apply {
            userName = MQTT_USERNAME
            password = MQTT_PASSWORD.toCharArray()
            isAutomaticReconnect = true // Paho's built-in auto-reconnect
            isCleanSession = true
            connectionTimeout = 10 // seconds
            keepAliveInterval = 20 // seconds
            try {
                val cf = CertificateFactory.getInstance("X.509")
                val caInput: InputStream = appContext.resources.openRawResource(R.raw.deb11ca)
                val ca = caInput.use { cf.generateCertificate(it) as X509Certificate }
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setCertificateEntry("ca", ca)
                }
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply {
                        init(keyStore)
                    }
                val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                    init(null, tmf.trustManagers, null)
                }
                this.socketFactory = sslContext.socketFactory
                Log.i("OtpViewModel_MQTT", "SSLSocketFactory configured for OTP VM.")
            } catch (e: Exception) {
                Log.e("OtpViewModel_MQTT", "Error setting up SSLSocketFactory for OTP VM", e)
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isRequestingOtp = false,
                            otpRequestError = "MQTT SSL Setup Error: ${e.message}",
                            otpRequestSuccessMessage = null
                        )
                    }
                }
                return
            }
        }

        try {
            Log.i("OtpViewModel_MQTT", "Attempting to connect MQTT client for OTP VM...")
            isMqttManuallyDisconnected = false // Reset manual disconnect flag on new attempt
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i("OtpViewModel_MQTT", "MQTT Connection initiated successfully for OTP VM.")
                    // connectComplete callback will handle more detailed connected state update
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Log.e(
                            "OtpViewModel_MQTT",
                            "MQTT Connection initiation failed for OTP VM.",
                            exception
                        )
                        _uiState.update {
                            it.copy(
                                isRequestingOtp = false,
                                otpRequestError = "MQTT Connection Failed: ${exception?.message}",
                                otpRequestSuccessMessage = null
                            )
                        }
                    }
                }
            })
        } catch (e: MqttException) {
            viewModelScope.launch(Dispatchers.Main) {
                Log.e("OtpViewModel_MQTT", "MqttException during connect for OTP VM.", e)
                _uiState.update {
                    it.copy(
                        isRequestingOtp = false,
                        otpRequestError = "MQTT Error: ${e.message}",
                        otpRequestSuccessMessage = null
                    )
                }
            }
        }
    }

    fun requestOTP() {
        if (!isConnected()) {
            Log.w("OtpViewModel_MQTT", "MQTT not connected. Cannot request OTP.")
            _uiState.update {
                it.copy(
                    isRequestingOtp = false,
                    otpRequestError = "MQTT Connection Lost. Please try again.",
                    otpRequestSuccessMessage = null
                )
            }
            // Attempt to reconnect if not connected.
            if (!isMqttManuallyDisconnected) connectMqtt()
            return
        }

        _uiState.update {
            it.copy(
                isRequestingOtp = true,
                otpRequestError = null,
                otpRequestSuccessMessage = null
            )
        }

        val messagePayload = "true"
        val mqttMessage = MqttMessage(messagePayload.toByteArray(Charsets.UTF_8))
        mqttMessage.qos = 1 // At least once
        mqttMessage.isRetained = false

        viewModelScope.launch(Dispatchers.IO) { // Perform publish on IO dispatcher
            try {
                mqttClient?.publish(
                    TOPIC_OTP_REQUEST,
                    mqttMessage,
                    null,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.i(
                                "OtpViewModel_MQTT",
                                "Successfully published OTP request to '$TOPIC_OTP_REQUEST'"
                            )
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isRequestingOtp = false,
                                        otpRequestSuccessMessage = "OTP request sent. Check your device."
                                    )
                                }
                            }
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
                        ) {
                            Log.e(
                                "OtpViewModel_MQTT",
                                "Failed to publish OTP request to '$TOPIC_OTP_REQUEST'",
                                exception
                            )
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.update {
                                    it.copy(
                                        isRequestingOtp = false,
                                        otpRequestError = "Failed to request OTP: ${exception?.message ?: "Unknown error"}"
                                    )
                                }
                            }
                        }
                    })
            } catch (e: MqttException) {
                Log.e(
                    "OtpViewModel_MQTT",
                    "MqttException when publishing OTP request to '$TOPIC_OTP_REQUEST'",
                    e
                )
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isRequestingOtp = false,
                            otpRequestError = "MQTT Error: ${e.message ?: "Publishing failed"}"
                        )
                    }
                }
            }
        }
    }

    // OTP Verification

    fun verifyOtp(otp: String) {
        Log.d("OtpViewModel", "Attempting to verify OTP: $otp")
        _uiState.update { it.copy(isVerifyingOtp = true, otpVerificationError = null) }


        if (!isConnected()) {
            _uiState.update {
                it.copy(
                    isVerifyingOtp = false,
                    otpVerificationError = "MQTT not connected. Cannot verify OTP."
                )
            }
            retryConnection() // try reconnecting
            return
        }

        // Publish OTP to MQTT topic
        val mqttMessage = MqttMessage(otp.toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = false
        }



        try {
            mqttClient?.publish(
                "bank_monitoring/otpVerify",
                mqttMessage,
                null,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i("OtpViewModel_MQTT", "OTP sent to MQTT topic successfully")
                        // Optionally, you can mark verification as in-progress and wait for backend confirmation
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("OtpViewModel_MQTT", "Failed to send OTP to MQTT", exception)
                        _uiState.update {
                            it.copy(
                                isVerifyingOtp = false,
                                otpVerificationError = "Failed to send OTP: ${exception?.message ?: "Unknown error"}"
                            )
                        }
                    }
                })
        } catch (e: MqttException) {
            Log.e("OtpViewModel_MQTT", "MqttException while publishing OTP", e)
            _uiState.update {
                it.copy(
                    isVerifyingOtp = false,
                    otpVerificationError = "MQTT Error: ${e.message}"
                )
            }
        }
    }


    fun clearOtpRequestError() {
        _uiState.update { it.copy(otpRequestError = null) }
    }

    fun clearOtpRequestSuccessMessage() {
        _uiState.update { it.copy(otpRequestSuccessMessage = null) }
    }

    fun clearOtpVerificationError() {
        _uiState.update { it.copy(otpVerificationError = null) }
    }

    fun onDialogDismissed() {
        _uiState.update {
            it.copy(
                otpRequestError = null,
                otpRequestSuccessMessage = null,
                otpVerificationError = null
            )
        }
    }

    fun clearNavigationFlag() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    private val _lastOtpVerificationTime = MutableStateFlow(System.currentTimeMillis())
    val lastOtpVerificationTime: StateFlow<Long> = _lastOtpVerificationTime.asStateFlow()

    fun updateOtpVerificationTime() {
        _lastOtpVerificationTime.value = System.currentTimeMillis()
    }

    fun retryConnection() {
        Log.d("OtpViewModel_MQTT", "Retrying MQTT connection for OTP.")
        if (mqttClient?.isConnected == false || mqttClient == null) {
            isMqttManuallyDisconnected = false // Allow reconnection attempts
            connectMqtt()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("OtpViewModel_MQTT", "ViewModel cleared. Disconnecting MQTT client for OTP VM.")

        isMqttManuallyDisconnected = true // Prevent auto-reconnect

        try {
            mqttClient?.let { client ->
                if (client.isConnected) { // Only disconnect if connected
                    client.disconnect(0)
                }
                try {
                    client.close() // Close safely, Paho sometimes allows close even if disconnected
                } catch (e: IllegalArgumentException) {
                    Log.w("OtpViewModel_MQTT", "MQTT client handle invalid during close, ignoring.", e)
                }
                Log.i("OtpViewModel_MQTT", "MQTT client disconnected and closed for OTP VM.")
            }
        } catch (e: MqttException) {
            Log.e("OtpViewModel_MQTT", "Error during MQTT disconnect for OTP VM", e)
        }

        mqttClient = null
    }

}
