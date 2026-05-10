package com.mauriciotogneri.fileexplorer

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.repository.UserPreferencesManager

class FileExplorerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UserPreferencesManager.initialize(this)
    }
}
