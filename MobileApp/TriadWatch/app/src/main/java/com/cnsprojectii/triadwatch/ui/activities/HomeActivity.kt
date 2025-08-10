package com.cnsprojectii.triadwatch.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cnsprojectii.triadwatch.ui.navigation.Screen
import com.cnsprojectii.triadwatch.ui.navigation.bottomBarScreens
import com.google.common.math.LinearTransformation.horizontal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser

        // if user is null somehow then go back to the LoginActivity
        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return // if no user then don't proceed with setContent
        }
        enableEdgeToEdge()
        setContent{
            MainApplicationScreen(loggedInUser = currentUser) // UI for the HomeActivity
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show the bottom bar if the current destination is one of the bottom bar screens
    // This is useful if you have other screens in your NavHost that shouldn't show the bottom bar
    val bottomBarDestination = bottomBarScreens.any { it.route == currentDestination?.route }

    if (bottomBarDestination) {
        NavigationBar(
            modifier = modifier,
            windowInsets = NavigationBarDefaults.windowInsets // Handles insets for edge-to-edge
            // You can customize containerColor, contentColor etc. here if needed
            // E.g., containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            bottomBarScreens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
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
                    alwaysShowLabel = true, // Or false to only show label for selected item
                    colors = NavigationBarItemDefaults.colors(
                        // Optional: Customize colors if MaterialTheme defaults aren't what you want
                        // selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        // selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        // indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        // unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        // unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
@Composable
fun MainApplicationScreen(loggedInUser: FirebaseUser?) { // pass the FirebaseUser to the MainApplicationScreen
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
            loggedInUser = loggedInUser // pass the FirebaseUser to the ApplicationNavHost
        )
    }
}

@Composable
fun ApplicationNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    loggedInUser: FirebaseUser? // pass the FirebaseUser
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route, // default screen on start
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreenContent(userEmail = loggedInUser?.email) }
        composable(Screen.History.route) { HistoryScreenContent() }
        composable(Screen.Nodes.route) { NodesScreenContent() }
        composable(Screen.Settings.route) { SettingsScreenContent() }
    }
}

// UI for the Home Screen
@Composable
fun HomeScreenContent(userEmail: String?) { // pass the userEmail
    var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) } // template timestamp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        val displayEmail = userEmail ?: "Guest User" // defaults to Guest User if email is null

        Text(
            text = "Welcome, $displayEmail!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Last Update: ${formatTimestamp(lastUpdateTime)}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.2f) // 50% of the screen's width
                .height(20.dp) // thickness of the bar
                .clip(RoundedCornerShape(10.dp)) // rounded bar corners
                .background(Color.LightGray) // color of the bar
        )

        Spacer(modifier = Modifier.height(10.dp))

        // boxes to display sensor data
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // row spacing
        ){
            // first row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    TemperatureHumidityContent()
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    DistanceContent()
                }
            }

            // second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    ResistanceContent()
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    MotionContent()
                }
            }

            // third row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    ESPLedContent()
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    ArduinoLedContent()
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        Text("Sensor Data Integrity: Verified",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center)
    }
}

@Composable
fun LargeRoundedBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            //.aspectRatio(1.5f) // square
            .height(150.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f)) // transparent dark gray color
            .padding(16.dp), // padding for content in the box
        contentAlignment = Alignment.Center // Center content
    ) {
        content()
    }
}

/*
    the section below below defines the content for the various boxes in the grid
*/
@Composable
fun TemperatureHumidityContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Temperature", style = MaterialTheme.typography.titleMedium)
        Text("25°C", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Humidity", style = MaterialTheme.typography.titleMedium)
        Text("0.0%", style = MaterialTheme.typography.bodyLarge, color = Color.Blue)
    }
}

@Composable
fun DistanceContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Distance", style = MaterialTheme.typography.titleMedium)
        Text("0.0 cm", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ResistanceContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resistance", style = MaterialTheme.typography.titleMedium)
        Text("0 Ohms", style = MaterialTheme.typography.bodyLarge, color = Color.Cyan)
    }
}

@Composable
fun MotionContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Motion", style = MaterialTheme.typography.titleMedium)
        Text("Not Detected", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
    }
}

@Composable
fun ESPLedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("ESP LED", style = MaterialTheme.typography.titleMedium)
        Text("On", style = MaterialTheme.typography.bodyLarge, color = Color.Green)
    }
}

@Composable
fun ArduinoLedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Arduino LED", style = MaterialTheme.typography.titleMedium)
        Text("Off", style = MaterialTheme.typography.bodyLarge, color = Color.Red)
    }
}
// function to format the timestamp for last sensor update
@Composable
private fun formatTimestamp(timestamp: Long): String {
    val simpleDateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    return simpleDateFormat.format(timestamp)
}

// UI for the History screen
@Composable
fun HistoryScreenContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("History Screen")
    }
}

// UI for the Nodes screen
@Composable
fun NodesScreenContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nodes Screen")
    }
}

// UI for the Settings screen
@Composable
fun SettingsScreenContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings Screen")
    }
}
