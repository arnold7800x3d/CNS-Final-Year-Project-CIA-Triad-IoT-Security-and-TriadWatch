package com.cnsprojectii.triadwatch.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cnsprojectii.triadwatch.ui.screens.HomeScreenContent
import com.cnsprojectii.triadwatch.ui.screens.NodesScreenContent
import com.cnsprojectii.triadwatch.ui.screens.SettingsScreenContent
import com.cnsprojectii.triadwatch.ui.screens.HistoryScreenContent
import com.google.firebase.auth.FirebaseUser

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    // Observe the current back stack entry to get the current destination
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if the current destination is one of the screens meant for the bottom bar
    // This allows you to have other screens in your NavHost that don't show the bottom bar
    val shouldShowBottomBar = bottomBarScreens.any { screen ->
        // Check if the current route matches any of the screen routes in bottomBarScreens
        // It's also good practice to check the hierarchy in case of nested navigation
        currentDestination?.hierarchy?.any { dest -> dest.route == screen.route } == true
    }

    if (shouldShowBottomBar) {
        NavigationBar(
            modifier = modifier,
            // Handles insets for edge-to-edge if your app is set up for it
            windowInsets = NavigationBarDefaults.windowInsets
            // You can customize containerColor, contentColor, etc. here if needed
            // E.g., containerColor = MaterialTheme.colorScheme.surfaceVariant
            // E.g., contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            bottomBarScreens.forEach { screen ->
                // Check if the current item is selected
                val selected =
                    currentDestination?.hierarchy?.any { it.route == screen.route } == true

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // re-selecting the same item.
                            launchSingleTop = true
                            // Restore state when re-selecting a previously selected item.
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(screen.title) },
                    alwaysShowLabel = true, // Set to false to only show label for selected item
                    colors = NavigationBarItemDefaults.colors(
                        // Optional: Customize colors if MaterialTheme defaults aren't what you want
                        // selectedIconColor = YourSelectedIconColor,
                        // selectedTextColor = YourSelectedTextColor,
                        // indicatorColor = YourIndicatorColor, // The bubble behind the selected item
                        // unselectedIconColor = YourUnselectedIconColor,
                        // unselectedTextColor = YourUnselectedTextColor
                    )
                )
            }
        }
    }
}

@Composable
fun MainApplicationScreen(
    loggedInUser: FirebaseUser?,
    onLogout: () -> Unit
) { // pass the FirebaseUser to the MainApplicationScreen
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        // hosting the various screen destination composable functions
        ApplicationNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            loggedInUser = loggedInUser, // pass the FirebaseUser to the ApplicationNavHost
            onLogout = onLogout
        )
    }
}

@Composable
fun ApplicationNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    loggedInUser: FirebaseUser?, // pass the FirebaseUser
    onLogout: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route, // default screen on start
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreenContent(userEmail = loggedInUser?.email) }
        composable(Screen.History.route) { HistoryScreenContent() }
        composable(Screen.Nodes.route) { NodesScreenContent() }
        composable(Screen.Settings.route) {
            SettingsScreenContent(
                userEmail = loggedInUser?.email,
                onLogoutClicked = onLogout
            )
        }
    }
}