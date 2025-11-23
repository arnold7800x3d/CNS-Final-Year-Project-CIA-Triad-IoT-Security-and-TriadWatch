package com.cnsprojectii.triadwatch.ui.viewmodels

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false
)

class ResetPasswordViewModel : ViewModel() {

    private val _uiState = mutableStateOf(ResetPasswordUiState())
    val uiState: State<ResetPasswordUiState> = _uiState

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = ResetPasswordUiState(message = "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ResetPasswordUiState(isLoading = true)
            Firebase.auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("ResetPasswordVM", "Password reset email sent successfully.")
                        _uiState.value = ResetPasswordUiState(
                            isSuccess = true,
                            message = "Password reset link sent! Check your email inbox (and spam folder)."
                        )
                    } else {
                        Log.w("ResetPasswordVM", "sendPasswordResetEmail:failure", task.exception)
                        _uiState.value = ResetPasswordUiState(
                            message = "Error: ${task.exception?.message}"
                        )
                    }
                }
        }
    }

    fun dismissDialog() {
        // Reset the state when the dialog is dismissed
        _uiState.value = ResetPasswordUiState()
    }
}
