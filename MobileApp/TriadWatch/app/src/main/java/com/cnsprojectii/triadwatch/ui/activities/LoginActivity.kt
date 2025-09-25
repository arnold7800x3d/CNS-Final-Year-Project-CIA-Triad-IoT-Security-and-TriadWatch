package com.cnsprojectii.triadwatch.ui.activities

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels // Import for viewModels delegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
// Removed ViewModelProvider import as viewModels delegate is used
import com.cnsprojectii.triadwatch.ui.activities.HomeActivity
import com.cnsprojectii.triadwatch.ui.dialogs.OtpDialog // Your existing OtpDialog
import com.cnsprojectii.triadwatch.viewmodels.OtpViewModel // Your OtpViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var showOtpDialog by mutableStateOf(true) // Start with OTP dialog if needed for initial app open
    private var otpCancelledByUser by mutableStateOf(false)
    private var navigationToHomeAttempted by mutableStateOf(false)

    private val otpViewModel: OtpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth

        // If there's no current user when the activity starts, 
        // and you want to go directly to login instead of OTP first, 
        // you might adjust initial showOtpDialog state here.
        // For now, it defaults to true, meaning OTP dialog appears first.
        // If a user is already signed in (app restart), OTP will show, then home.
        // If no user, OTP shows, then on cancel/fail -> login.

        setContent {
            if (showOtpDialog) {
                OtpDialog(
                    otpViewModel = otpViewModel,
                    showDialog = true,
                    onDismissRequest = {
                        Log.d(TAG, "OtpDialog dismissed.")
                        val wasOtpFlowSuccessfulAndReadyToNavigate = otpViewModel.uiState.value.navigateToHome
                        
                        showOtpDialog = false

                        if (wasOtpFlowSuccessfulAndReadyToNavigate) {
                            Log.d(TAG, "OtpDialog dismissed: Flow indicates navigation intent from ViewModel.")
                            otpCancelledByUser = false
                        } else {
                            Log.d(TAG, "OtpDialog dismissed: Flow indicates cancellation or simple dismiss.")
                            otpCancelledByUser = true
                        }
                    }
                )
            } else {
                PostOtpContentDisplayer()
            }
        }
    }

    @Composable
    private fun PostOtpContentDisplayer() {
        val currentUser = auth.currentUser

        // Corrected LaunchedEffect keys
        LaunchedEffect(currentUser, navigationToHomeAttempted, showOtpDialog, otpCancelledByUser) {
            if (!showOtpDialog && !otpCancelledByUser && currentUser != null && !navigationToHomeAttempted) {
                Log.d(TAG, "PostOtpContentDisplayer: User '${currentUser.email}' signed in, OTP flow complete (not cancelled). Navigating to HomeActivity.")
                navigationToHomeAttempted = true
                navigateToHomeActivity()
            }
        }

        if (otpCancelledByUser || (currentUser == null && !showOtpDialog)) {
            val reason = if (otpCancelledByUser) "OTP was cancelled by user or OTP required after login."
                         else if (!showOtpDialog && currentUser == null) "No user session and OTP process done/skipped."
                         else "Defaulting to login form."
            Log.d(TAG, "PostOtpContentDisplayer: $reason Displaying LoginForm.")
            LoginForm(onLogin = { email, password ->
                otpCancelledByUser = false // Reset for the new login->OTP attempt
                navigationToHomeAttempted = false // Reset for the new login->OTP attempt
                loginExistingUser(email, password)
            })
        } else if (currentUser != null && !showOtpDialog) { // Implies !otpCancelledByUser
            Log.d(TAG, "PostOtpContentDisplayer: User '${currentUser.email}' signed in, OTP flow complete. Showing loading before navigation.")
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Loading user session...")
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        if (showOtpDialog) {
            Log.d(TAG, "onStart: OTP dialog is expected to be visible.")
            return
        }
        if (otpCancelledByUser) {
            Log.d(TAG, "onStart: OTP was cancelled. Login form should be visible.")
            return
        }
        
        val currentUser = auth.currentUser
        if (currentUser != null && !navigationToHomeAttempted) {
            Log.d(TAG, "onStart: User '${currentUser.email}' signed in, OTP not cancelled, dialog not showing. PostOtpContentDisplayer should handle navigation.")
        } else if (currentUser == null) {
            Log.d(TAG, "onStart: No user signed in, OTP not cancelled, dialog not showing. Login form should be visible.")
        }
    }

    private fun loginExistingUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success. User credentials valid. Triggering OTP dialog.")
                    // Credentials are correct. Firebase currentUser is now updated.
                    // Reset flags and show OTP dialog. PostOtpContentDisplayer's onLogin already reset them,
                    // but being explicit here for clarity or if called from elsewhere.
                    otpCancelledByUser = false
                    navigationToHomeAttempted = false
                    showOtpDialog = true // <--- This is the key change to force OTP after login
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                    // Ensure flags are set so user stays on login form and doesn't accidentally navigate
                    navigationToHomeAttempted = false
                    otpCancelledByUser = true // Optional: treat login failure like an OTP cancellation to ensure login form stays
                }
            }
    }

    // updateUI is no longer called from loginExistingUser's success path.
    // If it were called for login failure, it would set navigationToHomeAttempted = false.
    // The loginExistingUser failure case now handles this directly.
    // Consider if updateUI is still needed or if its logic should be refactored.
    /*
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // This path should now be handled after OTP verification
            Log.d(TAG, "updateUI: User available. This should ideally be post-OTP.")
            if (!isFinishing && !isChangingConfigurations() && !navigationToHomeAttempted && !otpCancelledByUser && !showOtpDialog) {
                navigationToHomeAttempted = true
                navigateToHomeActivity()
            }
        } else {
            Log.d(TAG, "updateUI: User is null. Resetting navigation attempt flag.")
            navigationToHomeAttempted = false
        }
    }
    */

    private fun navigateToHomeActivity() {
        if (isFinishing || isChangingConfigurations) {
            Log.d(TAG, "navigateToHomeActivity: Attempted to navigate while finishing or changing config. Aborting.")
            return
        }
        Log.d(TAG, "Navigating to HomeActivity and finishing LoginActivity.")
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}

// LoginForm and other composables remain unchanged from your previous version.

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
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") }
    )
}

@Composable
fun LoginPasswordInput(modifier: Modifier = Modifier, password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
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
fun RegisterAndResetLinks() {
    // val context = LocalContext.current // Context not used here
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(buildAnnotatedString {
            append("Don't have an account? ")
            withStyle(SpanStyle(color = Color.Blue)) { 
                append("Register here")
            }
        })

        Text(buildAnnotatedString {
            append("Forgot your password? ")
            withStyle(SpanStyle(color = Color.Blue)) {
                append("Reset here")
            }
        })
    }
}

@Composable
fun LoginForm(onLogin: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

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
                Toast.makeText(context, "Email and password cannot be blank.", Toast.LENGTH_SHORT).show()
            }
        })
        Spacer(modifier = Modifier.height(16.dp))
        RegisterAndResetLinks()
    }
}
