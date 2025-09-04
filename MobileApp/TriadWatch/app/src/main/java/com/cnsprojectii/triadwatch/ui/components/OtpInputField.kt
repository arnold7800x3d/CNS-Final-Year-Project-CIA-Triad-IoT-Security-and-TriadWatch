package com.cnsprojectii.triadwatch.ui.components

import android.util.Log
import androidx.compose.foundation.background // Keep for now if you want to test with it
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OtpInputField(
    modifier: Modifier = Modifier,
    otpLength: Int = 6,
    onOtpChanged: (String) -> Unit
) {
    var otpValues by remember { mutableStateOf(List(otpLength) { "" }) }
    val focusRequesters = remember { List(otpLength) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Log theme colors (useful for debugging, can be removed in final production)
    val currentColorScheme = MaterialTheme.colorScheme
    Log.d("OtpInputColors", "--- Theme Colors (OtpInputField) ---")
    Log.d("OtpInputColors", "Surface: ${currentColorScheme.surface}")
    Log.d("OtpInputColors", "OnSurface: ${currentColorScheme.onSurface}")
    Log.d("OtpInputColors", "Primary: ${currentColorScheme.primary}")

    LaunchedEffect(Unit) {
        delay(300) // Small delay to ensure UI is ready
        focusRequesters.firstOrNull()?.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // .background(androidx.compose.ui.graphics.Color.Green) // <<< REMOVE or COMMENT OUT for production
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        otpValues.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                        val newOtpValues = otpValues.toMutableList()
                        newOtpValues[index] = newValue
                        otpValues = newOtpValues
                        onOtpChanged(otpValues.joinToString(""))

                        if (newValue.isNotEmpty() && index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        } else if (newValue.isEmpty() && index > 0 && keyEventWasBackspace(newOtpValues, index)) {
                            // This condition for moving back on empty after backspace might need refinement
                            // The onKeyEvent handler is more robust for backspace.
                            // focusRequesters[index - 1].requestFocus()
                        }

                        if (otpValues.joinToString("").length == otpLength) {
                            keyboardController?.hide()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .aspectRatio(1f)
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp &&
                            keyEvent.key == Key.Backspace &&
                            otpValues[index].isEmpty() && // Current field is now empty
                            index > 0
                        ) {
                            // Focus previous field, it will be cleared by its own onValueChange if needed,
                            // or by the user typing/backspacing further.
                            // The primary action here is to move focus.
                            // Clearing previous field here can be tricky if it was already empty.
                            // Let's ensure the previous field is also cleared and then focus.
                            val newOtpValues = otpValues.toMutableList()
                            newOtpValues[index - 1] = "" // Clear previous field's value in state
                            otpValues = newOtpValues
                            onOtpChanged(otpValues.joinToString("")) // Update state
                            focusRequesters[index - 1].requestFocus()
                            return@onKeyEvent true
                        }
                        false
                    },
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    // fontSize is managed by MaterialTheme.typography.titleLarge by default for TextFields,
                    // but explicitly setting it can be good for consistency if desired.
                    // For OutlinedTextField, the textStyle color is overridden by TextFieldColors.
                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number, // Visible digits
                    imeAction = if (index == otpLength - 1) ImeAction.Done else ImeAction.Next
                ),
                singleLine = true,
                maxLines = 1,
                placeholder = {
                    Text(
                        text = "0", // Typical placeholder for OTP
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        // Color will be derived from TextFieldDefaults placeholderColor
                        fontSize = MaterialTheme.typography.titleLarge.fontSize // Match text style
                    )
                },
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors( // Use .colors() for Material 3
                    // Text and placeholder colors
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), // Standard disabled alpha
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), // Softer placeholder
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),

                    // Container colors
                    focusedContainerColor = MaterialTheme.colorScheme.surface, // Or surfaceVariant, or transparent
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, // Or surfaceVariant, or transparent
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f), // Slightly different for disabled

                    // Indicator (border for OutlinedTextField) colors
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), // Standard outline
                    disabledIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),

                    // Cursor color
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// Helper to check if the last action leading to empty was a backspace.
// This is a bit simplistic and might need more robust state if onKeyEvent isn't sufficient.
// For now, the onKeyEvent handler is the primary way to manage backspace.
private fun keyEventWasBackspace(otpValues: List<String>, currentIndex: Int): Boolean {
    // This helper might not be perfectly accurate without more context on key events.
    // The onKeyEvent is generally preferred for explicit backspace handling.
    return otpValues[currentIndex].isEmpty() // A basic check
}
