package com.cnsprojectii.triadwatch.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlin.jvm.java
import com.cnsprojectii.triadwatch.ui.navigation.MainApplicationScreen

class HomeActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser

        // ADDED: FirebaseApp initialization (if not done in Application class)
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
            Log.d("HomeActivity", "FirebaseApp initialized.")
        }

        // if user is null somehow then go back to the LoginActivity
        if (currentUser == null) {
            navigateToLogin()
            return // if no user then don't proceed with setContent
        }
        enableEdgeToEdge()
        setContent {
            MainApplicationScreen(
                loggedInUser = currentUser,
                onLogout = { performLogout() })// UI for the HomeActivity
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

