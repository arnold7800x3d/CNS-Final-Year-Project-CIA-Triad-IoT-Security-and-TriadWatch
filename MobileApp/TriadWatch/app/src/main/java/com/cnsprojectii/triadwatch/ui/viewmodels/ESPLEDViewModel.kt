package com.cnsprojectii.triadwatch.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LedUiState(
    val isEspLedOn: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

class EspLedViewModel : ViewModel() {

    // Define the specific path in your Firebase Realtime Database
    // Make sure this path matches what your ESP32 is listening to
    private val database = FirebaseDatabase.getInstance().getReference("triadwatch/commands/espLed")

    private val _espLedUiState = MutableStateFlow(LedUiState())
    val espLedUiState: StateFlow<LedUiState> = _espLedUiState

    private var valueEventListener: ValueEventListener? = null

    init {
        listenToLedState()
    }

    private fun listenToLedState() {
        _espLedUiState.value = _espLedUiState.value.copy(isLoading = true, error = null)

        // Remove any existing listener before adding a new one
        valueEventListener?.let {
            database.removeEventListener(it)
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Try to get the boolean value; default to false if null or wrong type
                val isLedOn = snapshot.getValue(Boolean::class.java) ?: false
                _espLedUiState.value = LedUiState(isEspLedOn = isLedOn, isLoading = false)
            }

            override fun onCancelled(error: DatabaseError) {
                _espLedUiState.value = LedUiState(
                    isLoading = false,
                    error = "Failed to read LED state: ${error.message}"
                )
            }
        }
        database.addValueEventListener(valueEventListener!!)
    }

    fun setEspLedState(isOn: Boolean) {
        // You could optimistically update the UI here if you want faster feedback:
        // _espLedUiState.value = _espLedUiState.value.copy(isEspLedOn = isOn, isLoading = true, error = null)

        viewModelScope.launch {
            database.setValue(isOn)
                .addOnSuccessListener {
                    // Data successfully written to Firebase.
                    // The ValueEventListener will automatically update the UI state
                    // when it detects this change.
                    // If you optimistically updated, you might set isLoading to false here.
                    // _espLedUiState.value = _espLedUiState.value.copy(isLoading = false)
                    // Or, if you did not optimistically update:
                    // _espLedUiState.value = LedUiState(isEspLedOn = isOn, isLoading = false)
                    // However, relying on the listener is often cleaner to ensure UI reflects the true DB state.
                }
                .addOnFailureListener { exception ->
                    // Failed to write to Firebase.
                    // Revert optimistic update if you made one, and show an error.
                    _espLedUiState.value = _espLedUiState.value.copy(
                        isLoading = false, // Stop loading indicator
                        error = "Failed to set LED state: ${exception.message}"
                        // isEspLedOn = !isOn // Revert optimistic update if needed
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Important: Remove the Firebase listener when the ViewModel is destroyed
        // to prevent memory leaks and unwanted background updates.
        valueEventListener?.let {
            database.removeEventListener(it)
        }
        valueEventListener = null
    }
}
