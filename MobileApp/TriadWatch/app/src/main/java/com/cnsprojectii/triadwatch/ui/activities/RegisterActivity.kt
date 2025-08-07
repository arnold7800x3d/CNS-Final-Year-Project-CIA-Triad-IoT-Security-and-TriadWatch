package com.cnsprojectii.triadwatch.ui.activities

import android.content.ContentValues.TAG
import android.content.Intent
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class RegisterActivity : ComponentActivity() {

    // object
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // initialize Firebase Auth
        auth = Firebase.auth

        setContent {
            // registerNewUser function as a lambda
            RegistrationForm(onRegister = { email, password ->
                registerNewUser(email, password)
            })
        }
    }

    // register a user
    private fun registerNewUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "createUserWithEmail:success")
                    updateUI(auth.currentUser) // pass the user to updateUI
                } else {
                    // If sign in fails, display a message to the user
                    Log.w(TAG, "createUserWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                    updateUI(null)
                }
            }
    }

    // update UI logic
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // successful registration
            Log.d(TAG, "User registered successfully: ${user.email}")
            Toast.makeText(
                baseContext, "User registered successfully!",
                Toast.LENGTH_LONG,
            ).show()

            // navigate to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            // update UI for logged-out state
            Log.d(TAG, "User is signed out")
        }
    }

    // reload logic
    private fun reload() {
        Log.d(TAG, "Reloading user state")
        // call updateUI function
        updateUI(auth.currentUser)
    }

    // check to see if user is logged in
    public override fun onStart() {
        super.onStart()
        // check if user is signed in (non-null) and update UI accordingly
        val currentUser = auth.currentUser
        if (currentUser != null) {
            reload()
        }
    }
}

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

// Email input
@Composable
fun EmailInput(email: String, onEmailChange: (String) -> Unit) {
    TextField(
        value = email,
        onValueChange = onEmailChange,
        placeholder = { Text("Enter your preferred email address")}
    )
}

// Password Input
@Composable
fun PasswordInput(password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = { Text("Enter your preferred password")},
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

// Registration Button
@Composable
fun RegistrationButton(onRegisterClick: () -> Unit) {
    ElevatedButton(onClick = onRegisterClick) {
        Text("REGISTER")
    }
}

// Login and reset links
@Composable
fun LoginAndResetLinks() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(buildAnnotatedString {
            append("Already have an account? ")
            withStyle(SpanStyle(color = Color.Blue)) {
                append("Login here")
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
fun RegistrationForm(onRegister: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
        LoginAndResetLinks()
    }
}