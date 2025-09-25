package com.cnsprojectii.triadwatch.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cnsprojectii.triadwatch.viewmodels.OtpViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OtpDialog(
    otpViewModel: OtpViewModel,
    showDialog: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!showDialog) return

    val scope = rememberCoroutineScope()
    val otpState by otpViewModel.uiState.collectAsState()

    // 6-digit OTP state
    var otpDigits by remember { mutableStateOf(List(6) { "" }) }

    // FocusRequesters for auto-focus
    val focusRequesters = List(6) { FocusRequester() }

    // Track if the user has submitted OTP
    var hasSubmittedOtp by remember { mutableStateOf(false) }

    val navigateToHome = otpState.navigateToHome


    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Enter OTP") },
        text = {
            Column {
                // OTP Input Row
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        otpDigits.forEachIndexed { index, value ->
                            TextField(
                                value = value,
                                onValueChange = { newValue ->
                                    val updatedList = otpDigits.toMutableList()

                                    if (newValue.length > otpDigits[index].length) {
                                        // Typing a digit
                                        if (newValue.all { it.isDigit() }) {
                                            updatedList[index] = newValue.last().toString()
                                            otpDigits = updatedList
                                            if (index < 5) {
                                                scope.launch {
                                                    delay(50)
                                                    focusRequesters[index + 1].requestFocus()
                                                }
                                            }
                                        }
                                    } else if (newValue.isEmpty()) {
                                        // Backspace
                                        updatedList[index] = ""
                                        otpDigits = updatedList
                                        if (index > 0) {
                                            scope.launch {
                                                delay(50)
                                                focusRequesters[index - 1].requestFocus()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(56.dp)
                                    .focusRequester(focusRequesters[index]),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // OTP request feedback
                otpState.otpRequestSuccessMessage?.let {
                    Text(it, color = Color.Green)
                }
                otpState.otpRequestError?.let {
                    Text(it, color = Color.Red)
                }
                otpState.otpVerificationError?.let {
                    Text(it, color = Color.Red)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val otpString = otpDigits.joinToString("")
                    hasSubmittedOtp = true
                    otpViewModel.verifyOtp(otpString)
                },
                enabled = otpDigits.all { it.isNotEmpty() } && !otpState.isVerifyingOtp
            ) {
                if (otpState.isVerifyingOtp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { otpViewModel.requestOTP() }) {
                    Text("Request OTP")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        }
    )

    // Trigger success callback if OTP verified
    LaunchedEffect(otpState.isVerifyingOtp, otpState.otpVerificationError, otpState.otpRequestSuccessMessage) {
        if (hasSubmittedOtp && !otpState.isVerifyingOtp && otpState.otpVerificationError == null) {
            // OTP verified successfully
            onDismissRequest() // Hide dialog
        }
    }

    LaunchedEffect(navigateToHome) {
        if (navigateToHome) {
            // Call navigation lambda or NavController navigate
            onDismissRequest() // Dismiss OTP dialog
            // Navigate to Home screen
            // Example if using NavController:
            // navController.navigate("home") {
            //     popUpTo("otp") { inclusive = true } // remove OTP screen from back stack
            // }

            // Reset the navigation flag in ViewModel
            otpViewModel.clearNavigationFlag()
        }
    }
}
