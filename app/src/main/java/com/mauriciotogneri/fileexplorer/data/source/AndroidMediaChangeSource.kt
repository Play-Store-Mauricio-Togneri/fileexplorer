package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Watches the Files collection, which covers every public directory the home screen measures
 * rather than only images or video.
 *
 * Registered with `notifyForDescendants` because a provider notifies on the URI of the row that
 * changed rather than on the collection: without it a new photo's `.../file/1234` notification
 * would not reach an observer registered on the collection itself.
 */
class AndroidMediaChangeSource(private val context: Context) : MediaChangeSource {

    override fun changes(): Flow<Unit> = callbackFlow {
        // A null Handler leaves the framework to call onChange on the binder thread it arrived on.
        // Any app on the device can publish these, one per file during a bulk copy, and nothing
        // here needs the main thread — so posting them to it would put an unmetered, externally
        // driven stream in front of the UI for no benefit.
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // The URI names the row that changed and is deliberately ignored: nothing here
                // opens, queries or resolves it, so a notification cannot steer this app at a path.
                //
                // trySend because onChange cannot suspend and has no way to apply back pressure. A
                // send that finds the buffer full is dropped, which costs nothing: every emission
                // carries the same "something changed", and one already queued says it too.
                trySend(Unit)
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri(VOLUME_EXTERNAL),
                true,
                observer
            )
        } catch (e: Exception) {
            // A provider that rejects the registration costs freshness, not correctness: sizes
            // still expire on the cache TTL and are still cleared by this app's own file
            // operations. Completed rather than rethrown, so the collector simply stops watching.
            ErrorReporter.warning(e, "register_media_change_observer")
            close()
            return@callbackFlow
        }

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        // Both register and unregister are synchronous binder calls to ContentService, and the
        // collector runs on the main thread, so the producer is dispatched the way every other
        // source in this package dispatches itself.
        .flowOn(Dispatchers.IO)

    companion object {
        private const val VOLUME_EXTERNAL = "external"
    }
}
