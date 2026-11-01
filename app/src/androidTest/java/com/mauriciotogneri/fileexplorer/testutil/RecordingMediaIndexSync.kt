package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.source.MediaIndexSync
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [MediaIndexSync] that keeps what each operation reported, oldest first, instead of syncing
 * anything. Operations report from the thread they ran on while the test reads from its own, hence
 * the concurrent list.
 */
class RecordingMediaIndexSync : MediaIndexSync {

    private val recorded = CopyOnWriteArrayList<Call>()

    val calls: List<Call> get() = recorded.toList()

    override fun afterOperation(created: List<String>, removed: List<String>) {
        recorded += Call(created.toList(), removed.toList())
    }

    data class Call(val created: List<String>, val removed: List<String>)
}
