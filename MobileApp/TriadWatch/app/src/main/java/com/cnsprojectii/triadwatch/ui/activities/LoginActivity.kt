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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.dialogs.OtpDialog
import com.cnsprojectii.triadwatch.ui.dialogs.ResetPasswordDialog
import com.cnsprojectii.triadwatch.ui.viewmodels.OtpViewModel
import com.cnsprojectii.triadwatch.ui.viewmodels.ResetPasswordViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    // Persistent state variables
    private var showOtpDialog by mutableStateOf(false)
    private var otpCancelledByUser by mutableStateOf(false)
    private var navigationToHomeAttempted by mutableStateOf(false)
    private var otpRequested by mutableStateOf(false)

    private val otpViewModel: OtpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth

        // Only show OTP if explicitly requested from RegisterActivity
        val showOtpFromRegister = intent.getBooleanExtra("SHOW_OTP_DIALOG", false)
        showOtpDialog = showOtpFromRegister

        setContent {
            if (showOtpDialog) {
                OtpDialog(
                    otpViewModel = otpViewModel,
                    showDialog = showOtpDialog,
                    onDismissRequest = {
                        val wasOtpFlowSuccessful = otpViewModel.uiState.value.navigateToHome
                        otpCancelledByUser = !wasOtpFlowSuccessful
                        showOtpDialog = false
                        otpRequested = false
                        Log.d(TAG, "OTP dismissed, success? $wasOtpFlowSuccessful")
                    }
                )

                // Request OTP only once per dialog appearance
                LaunchedEffect(showOtpDialog) {
                    if (showOtpDialog && !otpRequested) {
                        otpViewModel.requestOTP()
                        otpRequested = true
                    }
                }

            } else {
                PostOtpContentDisplayer()
            }
        }
    }

    @Composable
    private fun PostOtpContentDisplayer() {
        val currentUser = auth.currentUser

        LaunchedEffect(currentUser, navigationToHomeAttempted, showOtpDialog, otpCancelledByUser) {
            if (!showOtpDialog && !otpCancelledByUser && currentUser != null && !navigationToHomeAttempted) {
                Log.d(TAG, "User '${currentUser.email}' signed in. Navigating to HomeActivity.")
                navigationToHomeAttempted = true
                navigateToHomeActivity()
            }
        }

        if (otpCancelledByUser || (currentUser == null && !showOtpDialog)) {
            LoginForm(onLogin = { email, password ->
                otpCancelledByUser = false
                navigationToHomeAttempted = false
                loginExistingUser(email, password)
            })
        } else if (currentUser != null && !showOtpDialog) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading user session...")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Prevent showing OTP again on lifecycle restart
        if (showOtpDialog || otpCancelledByUser) return
    }

    private fun loginExistingUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success. Triggering OTP dialog.")
                    otpCancelledByUser = false
                    navigationToHomeAttempted = false
                    otpRequested = false
                    showOtpDialog = true
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                    navigationToHomeAttempted = false
                    otpCancelledByUser = true
                }
            }
    }

    private fun navigateToHomeActivity() {
        if (isFinishing || isChangingConfigurations) return
        Log.d(TAG, "Navigating to HomeActivity.")
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}

// Composables for LoginForm UI

@Composable
fun LoginWelcomeText() {
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
fun LoginEmailInput(modifier: Modifier = Modifier, email: String, onEmailChange: (String) -> Unit) {
    TextField(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") }
    )
}

@Composable
fun LoginPasswordInput(modifier: Modifier = Modifier, password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
fun LoginButton(onLoginClick: () -> Unit) {
    ElevatedButton(onClick = onLoginClick) {
        Text("LOGIN")
    }
}

@Composable
fun LoginForm(onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var showResetPasswordDialog by remember { mutableStateOf(false) }
    val resetPasswordViewModel: ResetPasswordViewModel = viewModel()
    val context = LocalContext.current

    if (showResetPasswordDialog) {
        ResetPasswordDialog(
            showDialog = true,
            onDismissRequest = {
                showResetPasswordDialog = false
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
        LoginWelcomeText()
        Spacer(modifier = Modifier.height(16.dp))
        LoginEmailInput(email = email, onEmailChange = { email = it })
        Spacer(modifier = Modifier.height(16.dp))
        LoginPasswordInput(password = password, onPasswordChange = { password = it })
        Spacer(modifier = Modifier.height(16.dp))
        LoginButton(onLoginClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                onLogin(email, password)
            } else {
                Log.w(TAG, "Email or password is blank")
            }
        })
        Spacer(modifier = Modifier.height(16.dp))

        // Links for Register & Reset Password
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val registerString = buildAnnotatedString {
                append("Don't have an account? ")
                pushStringAnnotation(tag = "REGISTER", annotation = "register")
                withStyle(style = SpanStyle(color = Color.Blue)) {
                    append("Register here")
                }
                pop()
            }
            androidx.compose.foundation.text.ClickableText(
                text = registerString,
                onClick = { offset ->
                    registerString.getStringAnnotations(tag = "REGISTER", start = offset, end = offset)
                        .firstOrNull()?.let {
                            context.startActivity(Intent(context, RegisterActivity::class.java))
                        }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            val resetString = buildAnnotatedString {
                append("Forgot your password? ")
                pushStringAnnotation(tag = "RESET", annotation = "reset")
                withStyle(style = SpanStyle(color = Color.Blue)) {
                    append("Reset here")
                }
                pop()
            }
            androidx.compose.foundation.text.ClickableText(
                text = resetString,
                onClick = { offset ->
                    resetString.getStringAnnotations(tag = "RESET", start = offset, end = offset)
                        .firstOrNull()?.let {
                            showResetPasswordDialog = true
                        }
                }
            )
        }
    }
}
