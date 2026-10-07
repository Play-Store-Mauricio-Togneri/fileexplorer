package com.mauriciotogneri.fileexplorer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
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
        ErrorReporter.init(this)
        ErrorReporter.trackForegroundScreen(this)
        applySavedPreferences(PreferencesRepository(DataStorePreferencesSource(preferencesDataStore)))
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler).launch {
            AnalyticsTracker.setUserProperties(this@FileExplorerApplication)
        }
    }
}

/**
 * The only place the saved theme and sort order reach their process-wide holders on a cold start;
 * without it every launch shows the defaults until the user changes the setting again.
 */
internal fun applySavedPreferences(preferencesRepository: PreferencesRepository) {
    ThemeManager.setTheme(preferencesRepository.getInitialThemeMode())
    SortManager.setSortMode(preferencesRepository.getInitialSortMode())
}
