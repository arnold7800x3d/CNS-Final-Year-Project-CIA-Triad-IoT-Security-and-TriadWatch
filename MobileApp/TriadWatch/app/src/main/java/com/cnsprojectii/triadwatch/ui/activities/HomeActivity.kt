package com.cnsprojectii.triadwatch.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.graphics.BlendMode.Companion.Screen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
// import androidx.wear.compose.foundation.size
import com.cnsprojectii.triadwatch.ui.navigation.Screen
import com.cnsprojectii.triadwatch.ui.navigation.bottomBarScreens
import com.google.common.math.LinearTransformation.horizontal
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.copyOfRange
import kotlin.jvm.java
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import com.cnsprojectii.triadwatch.viewmodels.EspLedViewModel
import com.google.type.Date

// ADDED: CryptoUtils object. Recommendation: Move to its own file (e.g., utils/CryptoUtils.kt)
object CryptoUtils {
    private const val AES_ALGORITHM = "AES/CBC/PKCS7Padding"
    private const val HASH_ALGORITHM = "SHA-256"
    private const val IV_LENGTH = 16 // AES block size for CBC

    fun getDecryptionKey(): SecretKey {
        // Replace with your actual key bytes derived from the ESP32 key
        val keyInts = intArrayOf(
            21, 42, 63, 84, 105, 126, 147, 168,
            189, 210, 231, 252, 17, 34, 51, 68,
            85, 102, 119, 136, 153, 170, 187, 204,
            221, 238, 255, 1, 18, 35, 52, 69
        )
        val keyBytes = ByteArray(keyInts.size) { i -> keyInts[i].toByte() }
        return SecretKeySpec(keyBytes, "AES")
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun decrypt(encryptedBase64DataWithIv: String, key: SecretKey): String? {
        return try {
            val decodedData = Base64.decode(
                encryptedBase64DataWithIv,
                Base64.DEFAULT// This is the problematic part
            )
            if (decodedData.size < 16) {
                Log.e("CryptoUtils", "Decoded data too short for IV.")
                return null
            }
            val iv = decodedData.copyOfRange(0, 16)
            val encryptedData = decodedData.copyOfRange(16, decodedData.size)

            Log.d("CryptoUtils", "IV: ${bytesToHex(iv)}")
            Log.d("CryptoUtils", "Ciphertext: ${bytesToHex(encryptedData)}")

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedData)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Decryption failed for data: $encryptedBase64DataWithIv", e)
            null
        }
    }

    fun verifyHash(data: String, expectedHashHex: String): Boolean {
        return try {
            val calculatedHashBytes = MessageDigest.getInstance(HASH_ALGORITHM)
                .digest(data.toByteArray(StandardCharsets.UTF_8))
            val calculatedHashHex = bytesToHex(calculatedHashBytes)
            val result = calculatedHashHex.equals(expectedHashHex, ignoreCase = true)
            if (!result) {
                Log.w(
                    "CryptoUtils",
                    "Hash mismatch! Data: \"$data\", Calculated: $calculatedHashHex, Expected: $expectedHashHex"
                )
            }
            result
        } catch (e: Exception) {
            Log.e("CryptoUtils", "Hash verification failed", e)
            false
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}

// ADDED: Firebase Data Model. Recommendation: Move to a models package.
@com.google.firebase.database.IgnoreExtraProperties
data class SensorTypeData(
    val latest: String? = null,
    val hash: String? = null,
    val timestamp: Long? = null // Assuming ESP32 sends UNIX timestamp in seconds
)

// ADDED: UI State for Temperature and Humidity. Recommendation: Move to a ui_state package.
data class TempHumidityUiState(
    val temperature: String = "Loading...",
    val humidity: String = "Loading...",
    val isTemperatureVerified: Boolean = false,
    val isHumidityVerified: Boolean = false,
    val lastUpdateTimestamp: Long = 0L,
    val statusMessage: String? = null
)

// ADDED: ViewModel. Recommendation: Move to a viewmodels package.
class SensorViewModel : ViewModel() {
    private val database =
        com.google.firebase.database.FirebaseDatabase.getInstance() // Get instance

    // Make sure this path matches your Firebase structure EXACTLY
    private val sensorsRef = database.getReference("bank_monitoring/sensors")

    private val _tempHumidityState = mutableStateOf(TempHumidityUiState())
    val tempHumidityState: State<TempHumidityUiState> = _tempHumidityState

    private val decryptionKey: SecretKey by lazy { CryptoUtils.getDecryptionKey() }

    private val temperatureListener: com.google.firebase.database.ValueEventListener
    private val humidityListener: com.google.firebase.database.ValueEventListener

    init {
        Log.d("SensorViewModel", "Initializing and attaching listeners.")

        temperatureListener = createSensorValueListener(isTemperature = true)
        humidityListener = createSensorValueListener(isTemperature = false)

        sensorsRef.child("temperature").addValueEventListener(temperatureListener)
        sensorsRef.child("humidity").addValueEventListener(humidityListener)
    }

    private fun createSensorValueListener(isTemperature: Boolean): com.google.firebase.database.ValueEventListener {
        return object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                Log.d(
                    "SensorViewModel",
                    "Data changed for ${if (isTemperature) "temperature" else "humidity"}: ${snapshot.value}"
                )
                val sensorData = snapshot.getValue(SensorTypeData::class.java)
                processSensorValue(sensorData, isTemperature, decryptionKey)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(
                    "SensorViewModel",
                    "Firebase listener cancelled for ${if (isTemperature) "temperature" else "humidity"}",
                    error.toException()
                )
                val currentStatus = _tempHumidityState.value.statusMessage
                val newError =
                    "Failed to load ${if (isTemperature) "temperature" else "humidity"}: ${error.message}"
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    statusMessage = if (currentStatus != null) "$currentStatus\n$newError" else newError,
                    temperature = if (isTemperature && _tempHumidityState.value.temperature == "Loading...") "Error" else _tempHumidityState.value.temperature,
                    humidity = if (!isTemperature && _tempHumidityState.value.humidity == "Loading...") "Error" else _tempHumidityState.value.humidity
                )
            }
        }
    }

    private fun processSensorValue(
        sensorData: SensorTypeData?,
        isTemperature: Boolean,
        key: SecretKey
    ) {
        if (sensorData?.latest != null && sensorData.hash != null) {
            Log.i(
                "SensorViewModel",
                "Processing ${if (isTemperature) "Temp" else "Hum"}: Encrypted='${sensorData.latest}', Hash='${sensorData.hash}'"
            )
            val decryptedValue = CryptoUtils.decrypt(sensorData.latest, key)

            if (decryptedValue != null) {
                Log.i(
                    "SensorViewModel",
                    "Decrypted ${if (isTemperature) "Temp" else "Hum"}: '$decryptedValue'"
                )
                val isVerified = CryptoUtils.verifyHash(decryptedValue, sensorData.hash)
                Log.i(
                    "SensorViewModel",
                    "Verification for ${if (isTemperature) "Temp" else "Hum"}: $isVerified"
                )

                val displayValue = if (isVerified) {
                    // Attempt to extract value, assuming format "Type:ValueUnit"
                    // e.g., "Temp:25.5°C" or "Humidity:60.0%"
                    if (isTemperature) {
                        decryptedValue.substringAfter("Temp:").trim().ifEmpty { decryptedValue }
                    } else {
                        decryptedValue.substringAfter("Humidity:").trim().ifEmpty { decryptedValue }
                    }
                } else {
                    "Verify Failed"
                }

                if (isTemperature) {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        temperature = displayValue,
                        isTemperatureVerified = isVerified,
                        lastUpdateTimestamp = sensorData.timestamp
                            ?: _tempHumidityState.value.lastUpdateTimestamp,
                        statusMessage = if (!isVerified && displayValue == "Verify Failed") "Temp data integrity fail." else _tempHumidityState.value.statusMessage?.replace(
                            "Temp data integrity fail.",
                            ""
                        )?.trim()
                    )
                } else {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        humidity = displayValue,
                        isHumidityVerified = isVerified,
                        lastUpdateTimestamp = sensorData.timestamp
                            ?: _tempHumidityState.value.lastUpdateTimestamp,
                        statusMessage = if (!isVerified && displayValue == "Verify Failed") "Hum data integrity fail." else _tempHumidityState.value.statusMessage?.replace(
                            "Hum data integrity fail.",
                            ""
                        )?.trim()
                    )
                }
            } else {
                Log.w(
                    "SensorViewModel",
                    "Decryption failed for ${if (isTemperature) "Temp" else "Hum"}"
                )
                if (isTemperature) {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        temperature = "Decrypt Error",
                        isTemperatureVerified = false
                    )
                } else {
                    _tempHumidityState.value = _tempHumidityState.value.copy(
                        humidity = "Decrypt Error",
                        isHumidityVerified = false
                    )
                }
            }
        } else {
            Log.w(
                "SensorViewModel",
                "${if (isTemperature) "Temp" else "Hum"} data is missing 'latest' or 'hash', or is null. Data: $sensorData"
            )
            val placeholder = if (isTemperature) "Temp N/A" else "Hum N/A"
            if (isTemperature) {
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    temperature = placeholder,
                    isTemperatureVerified = false
                )
            } else {
                _tempHumidityState.value = _tempHumidityState.value.copy(
                    humidity = placeholder,
                    isHumidityVerified = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("SensorViewModel", "ViewModel cleared. Removing Firebase listeners.")
        sensorsRef.child("temperature").removeEventListener(temperatureListener)
        sensorsRef.child("humidity").removeEventListener(humidityListener)
    }
}

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

// UI for the Home Screen
@Composable
fun HomeScreenContent(userEmail: String?) { // pass the userEmail
    val sensorViewModel: SensorViewModel = viewModel() // ADDED: Get SensorViewModel instance
    val tempHumidityState by sensorViewModel.tempHumidityState // ADDED: Observe state

    // ViewModel for ESP LED Control (NEWLY ADDED)
    val espLedViewModel: EspLedViewModel = viewModel()
    val espLedUiState by espLedViewModel.espLedUiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        val displayEmail =
            userEmail ?: "Not logged in" // defaults to Not logged in if email is null

        Text(
            text = "Welcome, $displayEmail!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // MODIFIED: Display last update from ViewModel, or a general message
        if (tempHumidityState.lastUpdateTimestamp > 0) {
            Text(
                // text = "Last Sensor Update: ${formatTimestamp(tempHumidityState.lastUpdateTimestamp)}", // Using your original formatTimestamp
                text = "Last Sensor Update: ${formatTimestampForDisplay(tempHumidityState.lastUpdateTimestamp)}", // Using new formatting function
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "Waiting for sensor data...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

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
        ) {
            // first row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    TemperatureHumidityContent(uiState = tempHumidityState)
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
                LargeRoundedBox(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (espLedUiState.error != null && !espLedUiState.isLoading) {
                            // Handle retry logic for the error state, e.g.,
                            // espLedViewModel.retryConnection()
                            // espLedViewModel.listenToLedState() // To re-fetch or re-listen
                            println("Retry LED state or connection") // Placeholder
                        } else if (!espLedUiState.isLoading) {
                            // Only toggle if not loading and no error to retry
                            val newState = !espLedUiState.isEspLedOn
                            espLedViewModel.setEspLedState(newState)
                        }
                    }
                ) {
                    // --- MODIFIED ESPLedContent CALL ---
                    if (espLedUiState.error != null && !espLedUiState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "LED Error", // More concise error for small box
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "(Tap to retry)", // Example
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        ESPLedContent(
                            currentLEDIsOn = espLedUiState.isEspLedOn,
                            isLoading = espLedUiState.isLoading,
                            onLEDStateChange = { newState ->
                                espLedViewModel.setEspLedState(newState)
                            }
                        )
                    }
                    // --- END OF MODIFIED ESPLedContent CALL -
                }
                LargeRoundedBox(modifier = Modifier.weight(1f)) {
                    ArduinoLedContent()
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // MODIFIED: Display verification status from ViewModel
        val integrityMessage = when {
            tempHumidityState.statusMessage != null -> tempHumidityState.statusMessage
            tempHumidityState.isTemperatureVerified && tempHumidityState.isHumidityVerified -> "Sensor Data Integrity: Verified"
            tempHumidityState.temperature == "Loading..." || tempHumidityState.humidity == "Loading..." -> "Sensor Data Integrity: Checking..."
            else -> "Sensor Data Integrity: Issues Detected"
        }
        Text(
            text = integrityMessage ?: "Sensor Data Integrity: Status Unavailable",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (tempHumidityState.isTemperatureVerified && tempHumidityState.isHumidityVerified && tempHumidityState.statusMessage == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun LargeRoundedBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, // Optional click handler
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            //.aspectRatio(1.5f) // square
            .height(150.dp)
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f)) // transparent dark gray color
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(16.dp), // padding for content in the box
        contentAlignment = Alignment.Center // Center content
    ) {
        content()
    }
}

/*
    the section below below defines the content for the various boxes in the grid
*/
// MODIFIED: TemperatureHumidityContent now takes UIState
@Composable
fun TemperatureHumidityContent(uiState: TempHumidityUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Temperature", style = MaterialTheme.typography.titleMedium)
        Text(
            text = uiState.temperature,
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isTemperatureVerified &&
                !uiState.temperature.contains("Error", ignoreCase = true) &&
                !uiState.temperature.contains("Failed", ignoreCase = true) &&
                !uiState.temperature.contains("N/A")
            )
                Color.Red // Or MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Muted color
        )
        if (uiState.temperature == "Verify Failed" || uiState.temperature == "Decrypt Error") {
            Text(
                uiState.temperature,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Humidity", style = MaterialTheme.typography.titleMedium)
        Text(
            text = uiState.humidity,
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isHumidityVerified &&
                !uiState.humidity.contains("Error", ignoreCase = true) &&
                !uiState.humidity.contains("Failed", ignoreCase = true) &&
                !uiState.humidity.contains("N/A")
            )
                Color.Blue // Or MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (uiState.humidity == "Verify Failed" || uiState.humidity == "Decrypt Error") {
            Text(
                uiState.humidity,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Display overall status message if any, specific to this sensor box
        uiState.statusMessage?.let {
            if (it.contains("Temp", ignoreCase = true) || it.contains(
                    "Hum",
                    ignoreCase = true
                )
            ) { // Filter for relevant messages
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
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
fun ESPLedContent(
    modifier: Modifier = Modifier,
    currentLEDIsOn: Boolean = false, // OFF,
    isLoading: Boolean,
    onLEDStateChange: (Boolean) -> Unit
) {
    // val currentLEDIsOn = initialLEDState

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ESP LED", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (currentLEDIsOn) "On" else "Off",
            style = MaterialTheme.typography.bodyLarge,
            color = if (currentLEDIsOn) Color.Green else Color.Red
        )
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

// UI for the History screen
@Composable
fun HistoryScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp)
            .padding(16.dp)
    ) {
        // History screen heading
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .align(Alignment.Start)
        )

        // Time series placeholder
        GraphPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Graph data controls or summary here",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun GraphPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                MaterialTheme.shapes.medium
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Time Series Graph Area",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// UI for the Nodes screen
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

// UI for the Settings screen
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