package com.mauriciotogneri.fileexplorer.data.source

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.mediaIndexSyncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "media_index_sync",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * The app-wide [MediaIndexSync]: one queue for the whole process, independent of any screen.
 *
 * Like the telemetry objects, nothing here may surface as a failure of the operation that called
 * it, and [afterOperation] is a no-op until [init] runs — which unit tests never do.
 */
object SharedMediaIndexSync : MediaIndexSync {

    @Volatile
    private var sync: AndroidMediaIndexSync? = null

    fun init(application: Application) {
        if (sync != null) return

        try {
            val created = AndroidMediaIndexSync(
                sdkInt = Build.VERSION.SDK_INT,
                dataStore = application.mediaIndexSyncDataStore,
                scope = CoroutineScope(
                    SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> }
                ),
                scan = { paths -> AndroidMediaIndex.scan(application, paths) },
                indexedRows = { root, afterId, limit ->
                    AndroidMediaIndex.rowsAtAndUnder(application.contentResolver, root, afterId, limit)
                },
                isMounted = AndroidMediaIndex::isMounted
            )
            created.start()
            sync = created
        } catch (_: Exception) {
        }
    }

    override fun afterOperation(created: List<String>, removed: List<String>) {
        try {
            sync?.afterOperation(created, removed)
        } catch (_: Exception) {
        }
    }
}
