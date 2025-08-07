package com.cnsprojectii.triadwatch.ui.activities

import android.content.ContentValues.TAG
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

class LoginActivity : ComponentActivity() {

    // object
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // initialize Firebase Auth
        auth = Firebase.auth

        setContent {
            // loginExistingUser function as a lambda
            LoginForm(onRegister = { email, password ->
                loginExistingUser(email, password)
            })
        }

    }

    // login a user
    private fun loginExistingUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    updateUI(null)
                }
            }
    }

    // update UI logic
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // navigate to home activity
            Log.d(TAG, "User is signed in ${user.email}")
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

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if(currentUser != null){
            reload();
        }
    }
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

// Email input
@Composable
fun LoginEmailInput(email: String, onEmailChange: (String) -> Unit) {
    TextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email Address")}
    )
}

// Password Input
@Composable
fun LoginPasswordInput(password: String, onPasswordChange: (String) -> Unit) {
    TextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password")},
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

// Registration Button
@Composable
fun LoginButton(onRegisterClick: () -> Unit) {
    ElevatedButton(onClick = onRegisterClick) {
        Text("LOGIN")
    }
}

// Login and reset links
@Composable
fun RegisterAndResetLinks() {
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
fun LoginForm(onRegister: (String, String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
        LoginButton(onRegisterClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                onRegister(email, password)
            } else {
                Log.w(TAG, "Email or password is blank")
            }
        })
        Spacer(modifier = Modifier.height(16.dp))
        RegisterAndResetLinks()
    }
}