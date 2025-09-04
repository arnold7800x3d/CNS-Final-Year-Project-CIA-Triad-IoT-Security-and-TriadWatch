package com.cnsprojectii.triadwatch.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cnsprojectii.triadwatch.R
import com.cnsprojectii.triadwatch.ui.screens.HomeScreenContent
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

@OptIn(ExperimentalMaterial3Api::class) // Annotation for experimental Material 3 APIs like TopAppBar
@Composable
fun MainApplicationScreen(
    loggedInUser: FirebaseUser?,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()

    // Determine the current route to conditionally show/hide TopAppBar or change its title
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Define which screens should not have the main TopAppBar
    // or might have a different one (e.g., settings might have its own specific app bar)
    val screensWithoutMainTopBar: List<String> = listOf(
        // Add routes here if they should not show this generic TopAppBar
        // Screen.Settings.route // Example: if Settings has its own specialized TopAppBar
    )

    val showMainTopBar = !screensWithoutMainTopBar.contains(currentRoute) &&
            bottomBarScreens.any { currentRoute == it.route } // Show only for main bottom bar screens

    Scaffold(
        topBar = {
            TriadWatchTopAppBar()
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        ApplicationNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            loggedInUser = loggedInUser,
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
        composable(Screen.Settings.route) {
            SettingsScreenContent(
                userEmail = loggedInUser?.email,
                onLogoutClicked = onLogout
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriadWatchTopAppBar(modifier: Modifier = Modifier) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier
                        .size(80.dp),
                        //.padding(),
                    painter = painterResource(R.drawable.triadwatchlogo),
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        },
        modifier = modifier
            .height(80.dp)
    )
}