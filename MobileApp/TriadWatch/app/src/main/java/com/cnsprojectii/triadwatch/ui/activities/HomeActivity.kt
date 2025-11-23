package com.cnsprojectii.triadwatch.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.dialogs.OtpDialog
import com.cnsprojectii.triadwatch.ui.viewmodels.OtpViewModel
import com.cnsprojectii.triadwatch.ui.navigation.MainApplicationScreen
import com.cnsprojectii.triadwatch.ui.theme.TriadWatchTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser

        // Initialize Firebase if not done in Application class
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
            Log.d("HomeActivity", "FirebaseApp initialized.")
        }


        enableEdgeToEdge()

        setContent {
            TriadWatchTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // OTP state
                    var isOtpValidated by remember { mutableStateOf(false) }
                    var showOtpDialog by remember { mutableStateOf(true) } // always true initially


                    // If no user, navigate to login
                    val firebaseUser = auth.currentUser
                    if (firebaseUser == null) {
                        LaunchedEffect(Unit) {
                            navigateToLogin()
                        }
                    } else {
                        // Render OTP screen or MainApplicationScreen based on OTP validation
                        HomeScreenWithOtpValidation(
                            isOtpValidated = isOtpValidated,
                            showOtpDialog = showOtpDialog,
                            onOtpValidated = {
                                isOtpValidated = true
                                showOtpDialog = false
                            },
                            onOtpDismiss = { performLogout() },
                            currentUser = auth.currentUser,
                            onLogout = { performLogout() }
                        )
                    }
                }
            }
        }
    }

    private fun performLogout() {
        auth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        val intent = Intent(this@HomeActivity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun HomeScreenWithOtpValidation(
    isOtpValidated: Boolean,
    showOtpDialog: Boolean,
    onOtpValidated: () -> Unit,
    onOtpDismiss: () -> Unit,
    currentUser: FirebaseUser?,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val otpViewModel: OtpViewModel = viewModel()
    val otpState by otpViewModel.uiState.collectAsState()

    // Timer state
    var otpSessionActive by remember { mutableStateOf(false) }

    // Observe OTP verification
    LaunchedEffect(otpState.isVerifyingOtp, otpState.otpVerificationError) {
        if (!otpState.isVerifyingOtp &&
            otpState.otpVerificationError == null &&
            showOtpDialog
        ) {
            Log.d("HomeActivity_OTP", "OTP verified successfully!")
            onOtpValidated()
            otpSessionActive = true // start timer
        }
    }

    // Timer countdown after OTP validated
    LaunchedEffect(otpSessionActive) {
        if (otpSessionActive) {
            // Duration of OTP session
            val totalDuration = 600_000L // 10ban min
            val warningTime = 50_000L  // 50s -> 10s before expiry

            kotlinx.coroutines.delay(warningTime)
            Toast.makeText(
                context,
                "Session expiring soon. You’ll need to re-enter OTP.",
                Toast.LENGTH_SHORT
            ).show()

            // Wait remaining time
            kotlinx.coroutines.delay(totalDuration - warningTime)

            // Expire session -> show OTP dialog again
            otpSessionActive = false
            onOtpDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showOtpDialog && !isOtpValidated) {
            OtpDialog(
                otpViewModel = otpViewModel,
                showDialog = true,
                onDismissRequest = { onOtpDismiss() }
            )
        }

        if (isOtpValidated && currentUser != null) {
            MainApplicationScreen(
                loggedInUser = currentUser,
                onLogout = onLogout
            )
        }
    }
}


