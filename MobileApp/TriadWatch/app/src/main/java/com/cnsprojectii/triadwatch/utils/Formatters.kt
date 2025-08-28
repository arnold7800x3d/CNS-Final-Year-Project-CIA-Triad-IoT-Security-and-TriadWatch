package com.cnsprojectii.triadwatch.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Locale

// function to format the timestamp for last sensor update
@Composable
private fun formatTimestamp(timestamp: Long): String {
    val simpleDateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    return simpleDateFormat.format(timestamp)
}

fun formatTimestampForDisplay(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return "N/A"
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        // Firebase timestamp is often in seconds, convert to milliseconds for Date
        val date = java.util.Date(timestampSeconds * 1000)
        sdf.format(date)
    } catch (e: Exception) {
        Log.e("TimestampFormat", "Error formatting Firebase timestamp: $timestampSeconds", e)
        "Invalid Date"
    }
}