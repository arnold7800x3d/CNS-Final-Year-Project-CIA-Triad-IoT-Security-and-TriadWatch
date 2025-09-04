package com.cnsprojectii.triadwatch.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageHistory
import androidx.compose.material.icons.outlined.Settings
//import androidx.compose.ui.graphics.BlendMode.Companion.Screen
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object History: Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Settings: Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

// list of bottom bar screens
val bottomBarScreens: List<Screen> = listOf(
    Screen.Home,
    Screen.History,
    Screen.Settings
)