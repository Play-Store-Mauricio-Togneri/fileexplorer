package com.mauriciotogneri.fileexplorer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FileExplorerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnalyticsTracker.init(this)
        val preferencesRepository = PreferencesRepository(preferencesDataStore)
        ThemeManager.setTheme(preferencesRepository.getInitialThemeMode())
        SortManager.setSortMode(preferencesRepository.getInitialSortMode())
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler).launch {
            AnalyticsTracker.setUserProperties(this@FileExplorerApplication)
        }
    }
}
