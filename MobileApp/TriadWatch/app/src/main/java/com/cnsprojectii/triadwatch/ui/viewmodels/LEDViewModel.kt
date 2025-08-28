package com.cnsprojectii.triadwatch.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Firebase Paths (ensure these EXACTLY match your ESP32 code)
// If your ESP32 code for the Blue LED still uses "/triadwatch/commands/espLED", update this path.
private const val BLUE_LED_DB_PATH = "/triadwatch/commands/blueLED"
private const val WHITE_LED_DB_PATH = "/triadwatch/commands/whiteLED"

// Consistent name for individual LED state
data class IndividualLEDUiState(
    val isLEDOn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class AllLEDsUiState(
    val blueLEDState: IndividualLEDUiState = IndividualLEDUiState(), // Using consistent name
    val whiteLEDState: IndividualLEDUiState = IndividualLEDUiState() // Using consistent name
)

enum class LEDType { BLUE, WHITE } // Moved enum here for better organization within the file

class LEDControlViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance()

    private val _uiState = MutableStateFlow(AllLEDsUiState())
    val uiState: StateFlow<AllLEDsUiState> = _uiState.asStateFlow()

    private var blueLEDListener: ValueEventListener? = null
    private var whiteLEDListener: ValueEventListener? = null

    init {
        Log.d("LEDControlViewModel", "Initializing and attaching listeners.")
        listenToLEDState(LEDType.BLUE)
        listenToLEDState(LEDType.WHITE)
    }

    fun setLEDState(ledType: LEDType, newState: Boolean) {
        viewModelScope.launch {
            val path = if (ledType == LEDType.BLUE) BLUE_LED_DB_PATH else WHITE_LED_DB_PATH
            Log.d("LEDControlViewModel", "Setting ${ledType.name} LED at path $path to $newState")

            // Update UI immediately to show loading state
            updateSpecificLEDUiState(ledType) { it.copy(isLoading = true, error = null) }

            try {
                database.getReference(path).setValue(newState)
                    .addOnSuccessListener {
                        Log.d("LEDControlViewModel", "${ledType.name} state set to $newState successfully in Firebase.")
                        // The ValueEventListener will update the isLEDOn state and set isLoading to false
                        // when the data change is confirmed from Firebase.
                        // No need to call updateSpecificLEDUiState here for isLoading = false,
                        // as the listener will handle it, making the listener the source of truth.
                    }
                    .addOnFailureListener { e ->
                        Log.e("LEDControlViewModel", "Failed to set ${ledType.name} LED state in Firebase", e)
                        updateSpecificLEDUiState(ledType) {
                            it.copy(
                                isLoading = false,
                                error = "Failed to set ${ledType.name} state" // Simplified error message
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("LEDControlViewModel", "Exception while trying to set ${ledType.name} LED state", e)
                updateSpecificLEDUiState(ledType) {
                    it.copy(isLoading = false, error = "Error setting ${ledType.name} state")
                }
            }
        }
    }

    private fun listenToLEDState(ledType: LEDType) {
        val path = if (ledType == LEDType.BLUE) BLUE_LED_DB_PATH else WHITE_LED_DB_PATH
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isLEDOn = snapshot.getValue(Boolean::class.java) ?: false
                Log.d("LEDControlViewModel", "${ledType.name} LED state from DB ($path): $isLEDOn")
                updateSpecificLEDUiState(ledType) {
                    it.copy(isLEDOn = isLEDOn, isLoading = false, error = null)
                }
            }

            // Corrected this method name from onCancelLED to onCancelled as per Firebase API
            override fun onCancelled(error: DatabaseError) {
                Log.e("LEDControlViewModel", "Error listening to ${ledType.name} LED state ($path)", error.toException())
                updateSpecificLEDUiState(ledType) {
                    it.copy(
                        isLoading = false,
                        error = "Error listening to ${ledType.name}" // Simplified error message
                    )
                }
            }
        }

        database.getReference(path).addValueEventListener(listener)
        if (ledType == LEDType.BLUE) {
            blueLEDListener = listener
            Log.d("LEDControlViewModel", "Attached listener for BLUE LED at $path")
        } else {
            whiteLEDListener = listener
            Log.d("LEDControlViewModel", "Attached listener for WHITE LED at $path")
        }
    }

    // This function now correctly uses IndividualLEDUiState as its parameter type
    private fun updateSpecificLEDUiState(ledType: LEDType, updateAction: (IndividualLEDUiState) -> IndividualLEDUiState) {
        _uiState.update { currentState ->
            if (ledType == LEDType.BLUE) {
                currentState.copy(blueLEDState = updateAction(currentState.blueLEDState))
            } else {
                currentState.copy(whiteLEDState = updateAction(currentState.whiteLEDState))
            }
        }
    }

    fun retryListeners() {
        Log.d("LEDControlViewModel", "Retrying listeners...")
        // Remove existing listeners first to avoid duplicates
        blueLEDListener?.let {
            database.getReference(BLUE_LED_DB_PATH).removeEventListener(it)
            Log.d("LEDControlViewModel", "Removed existing BLUE LED listener.")
        }
        whiteLEDListener?.let {
            database.getReference(WHITE_LED_DB_PATH).removeEventListener(it)
            Log.d("LEDControlViewModel", "Removed existing WHITE LED listener.")
        }
        blueLEDListener = null // Ensure old listeners are cleared
        whiteLEDListener = null

        // Reset error states and set loading true before re-attaching
        _uiState.update {
            it.copy(
                blueLEDState = it.blueLEDState.copy(error = null, isLoading = true),
                whiteLEDState = it.whiteLEDState.copy(error = null, isLoading = true)
            )
        }

        listenToLEDState(LEDType.BLUE)
        listenToLEDState(LEDType.WHITE)
    }

    override fun onCleared() {
        super.onCleared()
        blueLEDListener?.let { database.getReference(BLUE_LED_DB_PATH).removeEventListener(it) }
        whiteLEDListener?.let { database.getReference(WHITE_LED_DB_PATH).removeEventListener(it) }
        Log.d("LEDControlViewModel", "ViewModel cleared, LED listeners removed.")
    }
}
