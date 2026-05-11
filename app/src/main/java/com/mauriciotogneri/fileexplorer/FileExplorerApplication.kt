package com.mauriciotogneri.fileexplorer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager

class FileExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val preferencesRepository = PreferencesRepository(preferencesDataStore)
        ThemeManager.setTheme(preferencesRepository.getThemeModeSync())
    }
}
