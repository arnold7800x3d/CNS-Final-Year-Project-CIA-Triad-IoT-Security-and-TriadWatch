package com.cnsprojectii.triadwatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Creates a singleton instance of DataStore for the entire application.
 *
 * By defining this as a top-level extension on Context, we can access it
 * from any Composable, ViewModel, or other class that has access to a Context.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
