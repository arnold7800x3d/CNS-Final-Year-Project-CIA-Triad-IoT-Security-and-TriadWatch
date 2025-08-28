package com.cnsprojectii.triadwatch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun NodesScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp)
            //.statusBarsPadding()
            .padding(16.dp), // padding around the entire settings screen
        horizontalAlignment = Alignment.CenterHorizontally // center content horizontally
    ) {
        // Nodes screen Heading
        Text(
            text = "Nodes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
        )

        // clickable row for ESP32 node
        NodeItemRow(
            icon = Icons.Filled.CheckCircle,
            iconDescription = "ESP32 Node",
            nodeName = "ESP32",
            onNodeClick = {
                println("ESP32 Node clicked")
            }
        )

        // clickable row for Arduino MKR GSM 1400
        NodeItemRow(
            icon = Icons.Filled.CheckCircle,
            iconDescription = "Arduino MKR GSM 1400 Node",
            nodeName = "Arduino MKR GSM 1400",
            onNodeClick = {
                println("Arduino MKR GSM 1400 Node Clicked")
            }

        )
    }
}

// display a node item
@Composable
fun NodeItemRow(
    icon: ImageVector,
    iconDescription: String,
    nodeName: String,
    onNodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNodeClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = nodeName,
            style = MaterialTheme.typography.titleMedium
        )
    }
}