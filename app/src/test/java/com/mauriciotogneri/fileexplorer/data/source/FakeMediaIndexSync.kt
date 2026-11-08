package com.mauriciotogneri.fileexplorer.data.source

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [MediaIndexSync] that records what each operation reported, oldest first, so a test can assert
 * the roots an operation hands over without a media index behind it.
 */
class FakeMediaIndexSync : MediaIndexSync {

    // Operations report from whatever thread they ran on, so the record must take writes off the
    // test's own thread.
    private val recorded = CopyOnWriteArrayList<Call>()

    val calls: List<Call> get() = recorded.toList()

    override fun afterOperation(created: List<String>, removed: List<String>) {
        recorded += Call(created.toList(), removed.toList())
    }

    data class Call(val created: List<String>, val removed: List<String>)
}
