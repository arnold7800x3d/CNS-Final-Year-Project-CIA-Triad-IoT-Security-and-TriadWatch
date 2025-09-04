package com.cnsprojectii.triadwatch.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cnsprojectii.triadwatch.ui.components.OtpInputField

@Composable
fun OtpDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onOtpSubmit: (String) -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    val otpLength = 6

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Enter OTP") },
            text = {
                Column {
                    Text("A 6-digit code has been sent to your device.") // Or email/phone
                    Spacer(modifier = Modifier.height(16.dp))
                    OtpInputField(
                        otpLength = otpLength,
                        onOtpChanged = { otp ->
                            otpValue = otp
                        }
                    )
                    // Optional: Add an error message display here
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (otpValue.length == otpLength) {
                            onOtpSubmit(otpValue)
                        }
                        // Else, you might want to show an error message
                    },
                    enabled = otpValue.length == otpLength // Enable button only when OTP is full
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}


