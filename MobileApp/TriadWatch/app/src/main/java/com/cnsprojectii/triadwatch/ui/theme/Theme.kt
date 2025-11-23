package com.cnsprojectii.triadwatch.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cnsprojectii.triadwatch.ui.viewmodels.ThemeRepository
import com.cnsprojectii.triadwatch.data.dataStore // If prompted by the IDE


//-------------------------------------------------------------------
// 1. DEFINE YOUR COLOR PALETTE
//-------------------------------------------------------------------
// These colors are chosen to look good in both light and dark themes.
// They are based on a modern, professional blue/teal and gray scheme.

// Core Palette
val PrimaryBlue = Color(0xFF00668B)
val OnPrimaryWhite = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFC3E8FF)
val OnPrimaryContainerDark = Color(0xFF001E2D)

val SecondaryTeal = Color(0xFF4D616C)
val OnSecondaryWhite = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD0E6F3)
// --- FIX 1: Corrected this line ---
val OnSecondaryContainerDark = Color(0xFF0A1F28)

val TertiaryGray = Color(0xFF5E5C71)
val OnTertiaryWhite = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE4DFF9)
val OnTertiaryContainerDark = Color(0xFF1B1A2C)

val ErrorRed = Color(0xFFBA1A1A)
val OnErrorWhite = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerDark = Color(0xFF410002)

val BackgroundLight = Color(0xFFF6FAFE)
val OnBackgroundDark = Color(0xFF181C1F)
val SurfaceLight = Color(0xFFF6FAFE) // Can be same as background
val OnSurfaceDark = Color(0xFF181C1F)

val SurfaceVariantLight = Color(0xFFDCE4E9)
val OnSurfaceVariantDark = Color(0xFF40484D)
val OutlineGray = Color(0xFF70787D)


//-------------------------------------------------------------------
// 2. CREATE THE LIGHT AND DARK COLOR SCHEMES
//-------------------------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue, // A slightly lighter blue for dark mode contrast
    onPrimary = OnPrimaryWhite,
    primaryContainer = Color(0xFF004C68), // Darker container for primary elements
    onPrimaryContainer = PrimaryContainerLight,

    secondary = SecondaryTeal, // A lighter teal
    onSecondary = OnSecondaryWhite,
    secondaryContainer = Color(0xFF364954),
    onSecondaryContainer = SecondaryContainerLight,

    tertiary = TertiaryGray,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = Color(0xFF464459),
    onTertiaryContainer = TertiaryContainerLight,

    error = ErrorRed,
    onError = OnErrorWhite,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorContainerLight,

    background = OnBackgroundDark,
    onBackground = Color(0xFFE0E2E6), // Light gray text on dark background
    surface = OnBackgroundDark,
    onSurface = Color(0xFFE0E2E6),

    surfaceVariant = OnSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC0C8CD),
    outline = Color(0xFF8A9297)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerDark,

    secondary = SecondaryTeal,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerDark,

    tertiary = TertiaryGray,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerDark,

    error = ErrorRed,
    onError = OnErrorWhite,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerDark,

    background = BackgroundLight,
    onBackground = OnBackgroundDark,
    surface = SurfaceLight,
    onSurface = OnBackgroundDark,

    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineGray
)


//-------------------------------------------------------------------
// 3. DEFINE YOUR TYPOGRAPHY
//-------------------------------------------------------------------

// --- FIX 2: Renamed this to avoid conflict ---
val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    // You can define other text styles here (bodySmall, labelSmall, etc.)
)


//-------------------------------------------------------------------
// 4. THE MAIN THEME COMPOSABLE
//-------------------------------------------------------------------

@Composable
fun TriadWatchTheme(
    // REMOVED: darkTheme: Boolean = isSystemInDarkTheme(),
    // We will now get this from our ViewModel.
    content: @Composable () -> Unit
) {
    // --- START: NEW LOGIC TO GET THEME FROM SAVED PREFERENCE ---
    val context = LocalContext.current
    val themeRepository = remember { ThemeRepository(context.applicationContext.dataStore) }
    val isDark by themeRepository.isDarkMode.collectAsState()
    // --- END: NEW LOGIC ---

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark // Use our 'isDark' variable
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
