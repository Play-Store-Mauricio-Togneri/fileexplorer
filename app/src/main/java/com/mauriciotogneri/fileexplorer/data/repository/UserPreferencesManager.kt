package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UserPreferencesManager {

    private const val PREFS_NAME = "user_preferences"
    private const val KEY_SHOW_HIDDEN = "show_hidden"

    private var prefs: SharedPreferences? = null

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    val isInitialized: Boolean
        get() = prefs != null

    fun initialize(context: Context) {
        if (prefs != null) return // Already initialized
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showHidden.value = prefs?.getBoolean(KEY_SHOW_HIDDEN, false) ?: false
    }

    fun toggleShowHidden() {
        val newValue = !_showHidden.value
        _showHidden.value = newValue
        prefs?.edit()?.putBoolean(KEY_SHOW_HIDDEN, newValue)?.apply()
    }
}
