package com.cnsprojectii.triadwatch.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreenContent(userEmail: String?, onLogoutClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp)
            //.statusBarsPadding()
            .padding(16.dp), // padding around the entire settings screen
        horizontalAlignment = Alignment.CenterHorizontally // center content horizontally
    ) {
        // Profile Heading
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
        )

        // icon and profile information
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically // align items vertically
        ) {
            // profile icon
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Profile",
                modifier = Modifier
                    .size(64.dp)
                    .padding(end = 16.dp), // space between icon and text
                tint = MaterialTheme.colorScheme.primary // tint icon
            )

            // email and reset password column
            Column(
                modifier = Modifier.weight(1f) // consume remaining row space
            ) {
                Text(
                    text = userEmail
                        ?: "Not logged in", // display email or this text as a fallback in case no email is returned
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Reset password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        println("Reset password clicked")
                    }
                )
            }
        }

        // settings section
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
        )

        // settings options
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
                // .fillMaxWidth() make the entire width of the option clickable
                .clickable {
                    println("App theme clicked")
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Metrics units",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable {
                    println("Metrics units clicked")
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Notifications",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable {
                    println("Notifications clicked")
                }
        )

        // app information section
        Text(
            text = "App Information",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
        )

        // settings options
        Text(
            text = "Version",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.Start)
        )

        Text(
            text = "1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Contact/Support Information",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
        )

        // handle contact information to allow calling the phone number provided when clicked
        val context = LocalContext.current
        val phoneNumber = "+254795975000"
        val developerName = "Arnold Ochieng' (App Developer)"
        val fullContactText = "$phoneNumber - $developerName"

        Text(
            text = fullContactText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable {
                    // intent to open the dialer
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phoneNumber")
                    }
                    Log.d("DialIntentDebug", "Intent Action: ${dialIntent.action}")
                    Log.d("DialIntentDebug", "Intent Data: ${dialIntent.dataString}")

                    // verify the existence of an app to handle the intent
                    if (dialIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(dialIntent)
                    } else {
                        // no app found to handle the dial intent
                        Log.e(
                            "DialIntentDebug",
                            "No activity found to handle intent. Package Manager: ${context.packageManager}"
                        )
                        println("No app found to handle dialing for $phoneNumber")
                    }
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View Docs",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable {
                    println("View Docs clicked")
                }
        )

        // spacer to consume the space left empty
        Spacer(modifier = Modifier.weight(1f))

        // bar
        Divider(
            color = Color.LightGray,
            thickness = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp) // space below and above the divider
        )

        // logout text button
        Text(
            text = "Logout",
            color = Color.Red,
            style = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center // center the logout button
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onLogoutClicked() // call lambda function
                }
                .padding(bottom = 16.dp)
        )
    }
}