package com.cnsprojectii.triadwatch.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cnsprojectii.triadwatch.ui.viewmodels.SettingsViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cnsprojectii.triadwatch.ui.dialogs.ResetPasswordDialog
import com.cnsprojectii.triadwatch.ui.viewmodels.ResetPasswordViewModel


@Composable
fun SettingsScreenContent(
    userEmail: String?,
    onLogoutClicked: () -> Unit,
    // Add the new ViewModel as a parameter
    settingsViewModel: SettingsViewModel = viewModel()
) {
    // Get the current theme state from the ViewModel
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()

    var showResetPasswordDialog by remember { mutableStateOf(false) }
    val resetPasswordViewModel: ResetPasswordViewModel = viewModel()

    if (showResetPasswordDialog) {
        ResetPasswordDialog(
            showDialog = true,
            onDismissRequest = {
                showResetPasswordDialog = false
                resetPasswordViewModel.dismissDialog() // Reset state on close
            },
            resetPasswordViewModel = resetPasswordViewModel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Profile Section (unchanged) ---
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Profile",
                modifier = Modifier.size(64.dp).padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userEmail ?: "Not logged in",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reset password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showResetPasswordDialog = true }
                )
            }
        }

        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Settings Section (updated) ---
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)
        )

        // --- THEME TOGGLE ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { settingsViewModel.toggleTheme() } // Click anywhere on the row
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Toggle Dark Mode",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkMode,
                onCheckedChange = { settingsViewModel.toggleTheme() }
            )
        }

        // --- Other settings can be added here ---

        // Spacer to push everything below it down
        Spacer(modifier = Modifier.weight(1f))

        // --- Logout Section (unchanged, but added a divider above) ---
        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
        Text(
            text = "Logout",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().clickable { onLogoutClicked() }.padding(bottom = 16.dp)
        )
    }
}
