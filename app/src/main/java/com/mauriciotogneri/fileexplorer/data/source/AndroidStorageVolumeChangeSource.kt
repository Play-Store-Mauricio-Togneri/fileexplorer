package com.mauriciotogneri.fileexplorer.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Watches the platform's volume broadcasts, which is the only notification a removable volume
 * appearing or disappearing produces: the media provider observes files on the volumes that are
 * mounted and says nothing about the set of volumes itself.
 *
 * Every removal action is registered alongside the mount, not just the orderly ones. An eject and a
 * card pulled out of the slot leave the same stale card on screen, pointing at a path that no
 * longer answers.
 */
class AndroidStorageVolumeChangeSource(private val context: Context) : StorageVolumeChangeSource {

    override fun changes(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // The intent names the volume that changed and is deliberately ignored: nothing
                // here opens or resolves it, so a broadcast cannot steer this app at a path. The
                // collector re-enumerates from the framework instead.
                //
                // trySend because onReceive cannot suspend and has no way to apply back pressure.
                // A send that finds the buffer full is dropped, which costs nothing: every emission
                // carries the same "the volumes changed", and one already queued says it too.
                trySend(Unit)
            }
        }

        // Every one of these actions carries the volume as a file: URI, and a filter without the
        // scheme matches none of them.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }

        try {
            // Registered at runtime rather than in the manifest, which has not delivered implicit
            // broadcasts to a declared receiver since Android 8. NOT_EXPORTED because every action
            // above is a protected system broadcast: the system still delivers them, and no other
            // app can reach this receiver to fake one.
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            // A registration the platform rejects costs freshness, not correctness: the screen
            // still re-enumerates every time it is returned to. Completed rather than rethrown, so
            // the collector simply stops watching.
            ErrorReporter.warning(e, "register_storage_volume_receiver")
            close()
            return@callbackFlow
        }

        awaitClose {
            // Guarded, unlike the sibling's unregisterContentObserver, which tolerates being handed
            // a receiver it does not know: unregisterReceiver raises IllegalArgumentException for
            // one that is not registered, and this body runs during cancellation, where a raise
            // reaches viewModelScope rather than any caller that could act on it. Nothing here is
            // recoverable — the receiver is on its way out either way.
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                ErrorReporter.warning(e, "unregister_storage_volume_receiver")
            }
        }
    }
        // Both register and unregister are synchronous binder calls to ActivityManager, and the
        // collector runs on the main thread, so the producer is dispatched the way every other
        // source in this package dispatches itself.
        .flowOn(Dispatchers.IO)
}
