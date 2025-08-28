package com.cnsprojectii.triadwatch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cnsprojectii.triadwatch.viewmodels.IndividualLEDUiState // Import the state

@Composable
fun LEDDisplayContent( // This is the content that goes INSIDE a LargeRoundedBox
    modifier: Modifier = Modifier,
    ledName: String,
    ledUiState: IndividualLEDUiState
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = ledName,
            style = MaterialTheme.typography.titleMedium
        )
        if (ledUiState.isLoading) {
            Text(
                text = "Updating...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        } else {
            Text(
                text = if (ledUiState.isLEDOn) "ON" else "OFF", // Consistent casing
                style = MaterialTheme.typography.bodyLarge,
                color = if (ledUiState.isLEDOn) Color.Green else Color.Red // Consider theme colors
            )
        }
    }
}
