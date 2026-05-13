package com.mauriciotogneri.fileexplorer.ui.screens.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AboutViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = ThemeManager.themeMode

    val showOtherAppsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_ABOUT_OTHER_APPS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissOtherAppsBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_ABOUT_OTHER_APPS)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AboutViewModel(
                preferencesRepository = PreferencesRepository(context.preferencesDataStore)
            ) as T
        }
    }
}
