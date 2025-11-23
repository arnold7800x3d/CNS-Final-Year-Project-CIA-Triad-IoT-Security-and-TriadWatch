package com.cnsprojectii.triadwatch.ui.viewmodels

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cnsprojectii.triadwatch.data.dataStore // <-- IMPORT THE NEW DATASTORE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Define a key for storing the dark mode preference
private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")

// The DataStore definition is REMOVED from this file.

/**
 * A repository to handle the logic of saving and retrieving theme settings.
 */
class ThemeRepository(private val dataStore: DataStore<Preferences>) {
    // A flow that emits true if dark mode is enabled, false otherwise.
    val isDarkMode: StateFlow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[IS_DARK_MODE] ?: false // Default to light mode (false)
        }
        .stateIn(
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO),
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    // Function to update the theme setting.
    suspend fun setDarkMode(isDark: Boolean) {
        dataStore.edit { settings ->
            settings[IS_DARK_MODE] = isDark
        }
    }
}

/**
 * ViewModel for the Settings Screen.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    // We now get the dataStore instance from the application's context.
    private val themeRepository = ThemeRepository(application.applicationContext.dataStore)

    // Expose the theme state to the UI
    val isDarkMode: StateFlow<Boolean> = themeRepository.isDarkMode

    // Function for the UI to call when the theme toggle is clicked
    fun toggleTheme() {
        viewModelScope.launch {
            val currentMode = isDarkMode.value
            themeRepository.setDarkMode(!currentMode)
        }
    }
}
