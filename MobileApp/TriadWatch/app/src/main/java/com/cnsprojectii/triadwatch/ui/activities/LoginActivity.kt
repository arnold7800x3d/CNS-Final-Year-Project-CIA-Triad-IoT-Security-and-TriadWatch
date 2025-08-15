package com.cnsprojectii.triadwatch.ui.activities

import android.content.ContentValues.TAG
import android.content.Intent // Import Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
// Import your HomeActivity
import com.cnsprojectii.triadwatch.ui.activities.HomeActivity // Adjust if your package is different
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth

        setContent {
            // Renamed onRegister to onLogin for clarity
            LoginForm(onLogin = { email, password ->
                loginExistingUser(email, password)
            })
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // If user is already logged in, navigate to HomeActivity and finish LoginActivity
            Log.d(TAG, "User '${currentUser.email}' already signed in. Navigating to HomeActivity.")
            navigateToHomeActivity()
        } else {
            Log.d(TAG, "No user signed in. Login form will be shown.")
        }
    }

    private fun loginExistingUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    // auth.currentUser will be the successfully signed-in user
                    updateUI(auth.currentUser)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG, // Longer for more detailed error
                    ).show()
                    updateUI(null) // Indicate failure, stay on login
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // Login was successful or user was already logged in (from onStart)
            Log.d(TAG, "User '${user.email}' signed in successfully. Navigating to HomeActivity.")
            navigateToHomeActivity()
        } else {
            // Login failed or user explicitly signed out.
            // Stay on LoginActivity. UI is already showing the login form.
            Log.d(TAG, "updateUI: User is null. Staying on Login screen.")
            // You could clear password fields here if desired by managing state in LoginForm
        }
    }

    private fun navigateToHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish() // Finish LoginActivity so user can't navigate back to it
    }

    // reload() function is less critical if onStart and updateUI handle navigation directly
    // private fun reload() {
    //    Log.d(TAG, "Reloading user state")
    //    updateUI(auth.currentUser)
    // }
}

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
fun LoginEmailInput(email: String, onEmailChange: (String) -> Unit) {
    TextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address") }
    )
}

@Composable
fun LoginPasswordInput(password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

// Renamed onRegisterClick to onLoginClick for clarity
@Composable
fun LoginButton(onLoginClick: () -> Unit) {
    ElevatedButton(onClick = onLoginClick) {
        Text("LOGIN")
    }
}

// In RegisterAndResetLinks, consider making texts clickable to navigate
@Composable
fun RegisterAndResetLinks() {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(buildAnnotatedString {
            append("Don't have an account? ")
            withStyle(SpanStyle(color = Color.Blue)) { // Consider using MaterialTheme.colorScheme.primary
                append("Register here")
                // TODO: Add click handler:
                // pushStringAnnotation("NAV_REGISTER", "NAV_REGISTER")
                // addStyle(SpanStyle(textDecoration = TextDecoration.Underline), annotationStart, annotationEnd)
            }
        }
            // TODO: Add Modifier.clickable { annotatedString.getStringAnnotations("NAV_REGISTER", ...).firstOrNull()?.let { navigate to RegisterActivity } }
        )

        Text(buildAnnotatedString {
            append("Forgot your password? ")
            withStyle(SpanStyle(color = Color.Blue)) { // Consider using MaterialTheme.colorScheme.primary
                append("Reset here")
                // TODO: Add click handler for password reset flow
            }
        })
    }
}

// Renamed onRegister to onLogin for clarity
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
        LoginButton(onLoginClick = { // Changed from onRegisterClick
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
