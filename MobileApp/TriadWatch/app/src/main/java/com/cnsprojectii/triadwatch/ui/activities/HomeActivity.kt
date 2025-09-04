package com.cnsprojectii.triadwatch.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
// import androidx.compose.material3.Text // Only if you use the placeholder text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// import androidx.compose.ui.Alignment // Only if you use the placeholder text
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cnsprojectii.triadwatch.ui.dialogs.OtpDialog // Make sure this path is correct
import com.cnsprojectii.triadwatch.ui.navigation.MainApplicationScreen
import com.cnsprojectii.triadwatch.ui.theme.TriadWatchTheme // Your app's theme
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

        // FirebaseApp initialization (if not done in Application class)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this) // Initialize default FirebaseApp
            Log.d("HomeActivity", "FirebaseApp initialized.")
        }

        // If user is null (e.g., session expired, or logged out), go back to LoginActivity
        if (currentUser == null) {
            navigateToLogin()
            return // Don't proceed with setContent if no user
        }

        enableEdgeToEdge()
        setContent {
            TriadWatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // State to manage OTP validation status
                    var isOtpValidated by remember { mutableStateOf(false) }
                    // State to control OTP dialog visibility - show dialog on activity start
                    var showOtpDialog by remember { mutableStateOf(true) }

                    HomeScreenWithOtpValidation(
                        isOtpValidated = isOtpValidated,
                        showOtpDialog = showOtpDialog,
                        onOtpValidated = {
                            isOtpValidated = true
                            showOtpDialog = false
                        },
                        onOtpDismiss = {
                            // MANDATORY OTP: If OTP is dismissed, consider logging out or finishing activity
                            // This example will simply keep the dialog showing until validated or force logout.
                            // If you want to be strict and OTP is essential for this session:
                            Log.w("HomeActivity_OTP", "OTP Dialog dismissed by user. Logging out.")
                            performLogout() // Or finish() if you prefer to just close HomeActivity

                            // If you want to allow them to cancel and retry, you might need a button
                            // in the dialog itself or a different logic here.
                            // For now, dismissing will lead to logout.
                        },
                        currentUser = currentUser, // Pass the currentUser
                        onLogout = { performLogout() } // Pass the logout action
                    )
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
        finish() // Finish HomeActivity so user can't go back to it without logging in
    }
}

@Composable
fun HomeScreenWithOtpValidation(
    isOtpValidated: Boolean,
    showOtpDialog: Boolean,
    onOtpValidated: () -> Unit,
    onOtpDismiss: () -> Unit,
    currentUser: FirebaseUser?, // Added to pass to MainApplicationScreen
    onLogout: () -> Unit        // Added to pass to MainApplicationScreen
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isOtpValidated) {
            // Display Main Application Screen ONLY if OTP is validated
            MainApplicationScreen(
                loggedInUser = currentUser, // Pass the loggedInUser
                onLogout = onLogout         // Pass the onLogout lambda
            )
        } else {
            // Optional: Show a placeholder or loading state while OTP is pending.
            // For example:
            // Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Text("Please verify OTP to access your account.", textAlign = TextAlign.Center)
            // }
            // For now, the screen will appear blank behind the dialog until OTP is validated.
        }

        // OTP Dialog - overlays everything if !isOtpValidated (implicitly handled by showOtpDialog)
        if (showOtpDialog && !isOtpValidated) {
            OtpDialog(
                showDialog = true, // Dialog is shown based on this condition
                onDismissRequest = {
                    // This is called when the user tries to dismiss (e.g., back press, click outside)
                    Log.d("HomeActivity_OTP", "OtpDialog onDismissRequest called.")
                    onOtpDismiss()
                },
                onOtpSubmit = { otp ->
                    // --- TODO: Implement your actual OTP validation logic here ---
                    // This could involve a ViewModel, a network call, etc.
                    Log.d("HomeActivity_OTP", "OTP Submitted: $otp")
                    if (isValidOtp(otp)) { // Replace with your actual validation logic
                        Log.i("HomeActivity_OTP", "OTP Validation Successful!")
                        onOtpValidated() // Callback to update state and hide dialog
                    } else {
                        Log.w("HomeActivity_OTP", "OTP Validation Failed!")
                        // TODO: Show an error message to the user (e.g., within the OtpDialog or a Toast/Snackbar)
                        // The dialog will remain open for another attempt.
                    }
                }
            )
        }
    }
}

// --- Dummy OTP validation function (REPLACE WITH YOUR ACTUAL LOGIC) ---
fun isValidOtp(otp: String): Boolean {
    // IMPORTANT: Replace this with your actual OTP validation logic.
    // This could involve:
    // 1. Comparing with an OTP sent to the user via SMS/email (fetched from your backend).
    // 2. Using a Time-based One-Time Password (TOTP) algorithm if applicable.
    // 3. Any other secure method your system uses.
    // NEVER use a hardcoded OTP like this in a production application.
    return otp == "123456" // Example: Hardcoded OTP - FOR TESTING ONLY
}

@Preview(showBackground = true, name = "OTP Dialog Visible")
@Composable
fun PreviewOtpDialogVisible() {
    TriadWatchTheme {
        var isOtpValidated by remember { mutableStateOf(false) }
        var showOtpDialog by remember { mutableStateOf(true) }
        val mockUser = FirebaseAuth.getInstance().currentUser // Or create a mock FirebaseUser

        HomeScreenWithOtpValidation(
            isOtpValidated = isOtpValidated,
            showOtpDialog = showOtpDialog,
            onOtpValidated = {
                isOtpValidated = true
                showOtpDialog = false
            },
            onOtpDismiss = { Log.d("Preview", "OTP Dialog Dismissed") },
            currentUser = mockUser,
            onLogout = { Log.d("Preview", "Logout Clicked") }
        )
    }
}

@Preview(showBackground = true, name = "Home Screen After OTP")
@Composable
fun PreviewHomeScreenValidated() {
    TriadWatchTheme {
        val mockUser = FirebaseAuth.getInstance().currentUser // Or create a mock FirebaseUser
        HomeScreenWithOtpValidation(
            isOtpValidated = true, // OTP is already validated
            showOtpDialog = false, // Dialog is not shown
            onOtpValidated = { },
            onOtpDismiss = { },
            currentUser = mockUser,
            onLogout = { Log.d("Preview", "Logout Clicked") }
        )
    }
}
