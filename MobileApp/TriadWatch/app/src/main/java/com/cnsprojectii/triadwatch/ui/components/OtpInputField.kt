package com.cnsprojectii.triadwatch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OtpInputField(
    otpLength: Int = 6,
    onOtpChanged: (String) -> Unit
) {
    var otpValues by remember { mutableStateOf(List(otpLength) { "" }) }
    val focusRequesters = remember { List(otpLength) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus first field
    LaunchedEffect(Unit) {
        focusRequesters.firstOrNull()?.requestFocus()
        keyboardController?.show()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        otpValues.forEachIndexed { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                        val newOtp = otpValues.toMutableList()
                        newOtp[index] = newValue
                        otpValues = newOtp
                        onOtpChanged(otpValues.joinToString(""))

                        if (newValue.isNotEmpty() && index < otpLength - 1) {
                            focusRequesters[index + 1].requestFocus()
                        } else if (otpValues.joinToString("").length == otpLength) {
                            keyboardController?.hide()
                        }
                    }
                },
                modifier = Modifier
                    .size(50.dp)
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp &&
                            keyEvent.key == Key.Backspace &&
                            otpValues[index].isEmpty() &&
                            index > 0
                        ) {
                            val newOtp = otpValues.toMutableList()
                            newOtp[index - 1] = ""
                            otpValues = newOtp
                            onOtpChanged(otpValues.joinToString(""))
                            focusRequesters[index - 1].requestFocus()
                            return@onKeyEvent true
                        }
                        false
                    },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    color = Color.Black
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == otpLength - 1) ImeAction.Done else ImeAction.Next
                ),
                placeholder = {
                    Text(
                        "0",
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp,
                        color = Color.Gray
                    )
                },
//                colors = TextFieldDefaults.outlinedTextFieldColors(
//                    textColor = Color.Black,
//                    cursorColor = Color.Black,
//                    placeholderColor = Color.Gray,
//                    focusedBorderColor = Color.Black,
//                    unfocusedBorderColor = Color.DarkGray
//                )
            )
        }
    }
}
