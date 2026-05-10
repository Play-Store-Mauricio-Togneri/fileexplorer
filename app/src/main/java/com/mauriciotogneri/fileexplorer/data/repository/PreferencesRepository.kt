package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val showHidden: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_HIDDEN_KEY] ?: false
    }

    suspend fun setShowHidden(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_KEY] = show
        }
    }

    companion object {
        private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden")
    }
}
