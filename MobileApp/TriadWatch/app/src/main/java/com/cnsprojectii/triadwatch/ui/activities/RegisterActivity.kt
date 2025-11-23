package com.cnsprojectii.triadwatch.ui.activities

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.cnsprojectii.triadwatch.ui.dialogs.OtpDialog
import com.cnsprojectii.triadwatch.ui.dialogs.ResetPasswordDialog
import com.cnsprojectii.triadwatch.ui.viewmodels.ResetPasswordViewModel
import com.cnsprojectii.triadwatch.ui.viewmodels.OtpViewModel
import com.google.firebase.Firebase

class RegisterActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    // OTP dialog control
    var showOtpDialog by mutableStateOf(false)
    private var otpCancelledByUser by mutableStateOf(false)
    private var navigationToLoginAttempted by mutableStateOf(false)

    // RESET password dialog control
    var showResetPasswordDialog by mutableStateOf(false)

    private val otpViewModel: OtpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auth = Firebase.auth

        setContent {
            val showOtpDialogState = remember { mutableStateOf(showOtpDialog) }

            RegistrationForm(
                onRegister = { email, password -> registerNewUser(email, password) },
                onLoginHereClicked = { showOtpDialogState.value = true },
                onResetClicked = { showResetPasswordDialog = true }
            )

            OtpHandler(
                otpViewModel = otpViewModel,
                showDialog = showOtpDialogState.value,
                onDismissRequest = { showOtpDialogState.value = false },
                navigateToLogin = { navigateToLogin() }
            )
        }

    }

    // OTP handler composable
    @Composable
    fun OtpHandler(
        otpViewModel: OtpViewModel,
        showDialog: Boolean,
        onDismissRequest: () -> Unit,
        navigateToLogin: () -> Unit
    ) {
        val uiState by otpViewModel.uiState.collectAsState()

        if (showDialog) {
            OtpDialog(
                otpViewModel = otpViewModel,
                showDialog = true,
                onDismissRequest = {
                    onDismissRequest()
                    // Navigate if OTP verified successfully
                    if (uiState.navigateToHome) {
                        navigateToLogin()
                        otpViewModel.clearNavigationFlag()
                    }
                }
            )
        }
    }


    private fun registerNewUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:success")
                    updateUI(true, auth.currentUser)
                } else {
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI(false, null)
                }
            }
    }

    private fun updateUI(registrationTaskSuccessful: Boolean, user: FirebaseUser?) {
        if (registrationTaskSuccessful && user != null) {
            Log.d(TAG, "User registered successfully: ${user.email}")
            Toast.makeText(
                baseContext, "User registered successfully!",
                Toast.LENGTH_LONG
            ).show()
            navigateToLogin()
        } else if (!registrationTaskSuccessful) {
            Log.d(TAG, "Registration failed or user is null after attempt")
        }
    }

    fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("SHOW_OTP_DIALOG", false)
        startActivity(intent)
        finish()
    }
}

// UI Composables

@Composable
fun WelcomeText() {
    Text(
        text = "Welcome to TriadWatch",
        style = TextStyle(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 0.5.sp
        )
    )
}

@Composable
fun EmailInput(modifier: Modifier = Modifier, email: String, onEmailChange: (String) -> Unit) {
    TextField(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        value = email,
        onValueChange = onEmailChange,
        placeholder = { Text("Enter your preferred email address") }
    )
}

@Composable
fun PasswordInput(modifier: Modifier = Modifier, password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        value = password,
        onValueChange = onPasswordChange,
        placeholder = { Text("Enter your preferred password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
fun RegistrationButton(onRegisterClick: () -> Unit) {
    ElevatedButton(onClick = onRegisterClick) {
        Text("REGISTER")
    }
}

@Composable
fun LoginAndResetLinks(
    onLoginHereClicked: () -> Unit,
    onResetClicked: () -> Unit,
    onDirectLoginClicked: () -> Unit  // NEW: For the extra login link
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Login here (triggers OTP)
        val loginAnnotatedString = buildAnnotatedString {
            append("Already have an account? ")
            pushStringAnnotation(tag = "LOGIN_LINK", annotation = "login")
            withStyle(style = SpanStyle(color = Color.Blue)) { append("Login here") }
            pop()
        }

        ClickableText(
            text = loginAnnotatedString,
            onClick = { offset ->
                loginAnnotatedString.getStringAnnotations(tag = "LOGIN_LINK", start = offset, end = offset)
                    .firstOrNull()?.let { _ ->
                        onLoginHereClicked()  // triggers OTP dialog
                    }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Reset here
        val resetAnnotatedString = buildAnnotatedString {
            append("Forgot your password? ")
            withStyle(SpanStyle(color = Color.Blue)) { append("Reset here") }
        }

        ClickableText(
            text = resetAnnotatedString,
            onClick = { onResetClicked() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Direct login link for testing OTP flow
        val directLoginAnnotated = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold)) {
                append("Proceed to Login")
            }
        }
        ClickableText(
            text = directLoginAnnotated,
            onClick = { onDirectLoginClicked() }  // This navigates immediately to LoginActivity
        )
    }
}


@Composable
fun RegistrationForm(
    onRegister: (String, String) -> Unit,
    onLoginHereClicked: () -> Unit,
    onResetClicked: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val resetPasswordViewModel: ResetPasswordViewModel = viewModel()

    val activity = LocalContext.current as? RegisterActivity
    if (activity?.showResetPasswordDialog == true) {
        ResetPasswordDialog(
            showDialog = true,
            onDismissRequest = {
                activity.showResetPasswordDialog = false
                resetPasswordViewModel.dismissDialog()
            },
            resetPasswordViewModel = resetPasswordViewModel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WelcomeText()
        Spacer(modifier = Modifier.height(16.dp))
        EmailInput(email = email, onEmailChange = { email = it })
        Spacer(modifier = Modifier.height(16.dp))
        PasswordInput(password = password, onPasswordChange = { password = it })
        Spacer(modifier = Modifier.height(16.dp))
        RegistrationButton(onRegisterClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                onRegister(email, password)
            } else {
                Log.w(TAG, "Email or password is blank")
            }
        })
        Spacer(modifier = Modifier.height(16.dp))
        LoginAndResetLinks(
            onLoginHereClicked = onLoginHereClicked,
            onResetClicked = onResetClicked,
            onDirectLoginClicked = { activity?.navigateToLogin() } // NEW
        )

    }
}
