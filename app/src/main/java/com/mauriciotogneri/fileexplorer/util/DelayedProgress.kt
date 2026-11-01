package com.mauriciotogneri.fileexplorer.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * How long an operation runs before its progress dialog appears. One that is over sooner never
 * shows it: a dialog that opens and closes at once reads as a glitch, and most operations — a
 * rename, a delete of a few files — end well inside this.
 */
const val PROGRESS_DIALOG_DELAY_MS = 500L

/**
 * Launches an operation whose progress dialog appears only if the operation is still running
 * [PROGRESS_DIALOG_DELAY_MS] after it started.
 *
 * The timer is a child of the operation's own job, started with it, so it covers whatever the
 * operation does before its first progress report — a pre-flight check, a pre-count — as well as
 * the work itself. It is cancelled as soon as [block] returns or throws, so it never keeps the
 * operation's job alive past its work, and cancelling the operation cancels it with everything
 * else. A dialog left up by a path that did not call [DelayedProgress.clear] is closed then too.
 *
 * @param placeholder what the dialog shows if it appears before the operation reported anything.
 * @param owner reads the handle the caller keeps the returned job in, the one its Cancel button
 * cancels. Read from whichever thread reports progress, so a handle written on one thread and
 * reported to from another has to be `@Volatile`. See [DelayedProgress] for what it decides.
 * @param publish writes the dialog's state; null closes it. Called under the dialog's lock, so it
 * must not block.
 */
fun <T : Any> CoroutineScope.launchWithDelayedProgress(
    placeholder: T,
    owner: () -> Job?,
    publish: (T?) -> Unit,
    block: suspend CoroutineScope.(dialog: DelayedProgress<T>) -> Unit
): Job = launch {
    val dialog = DelayedProgress(placeholder, coroutineContext.job, owner, publish)
    val timer = launch(start = CoroutineStart.UNDISPATCHED) {
        delay(PROGRESS_DIALOG_DELAY_MS)
        dialog.reveal()
    }

    try {
        block(dialog)
    } finally {
        timer.cancel()
        dialog.clear()
    }
}

/**
 * The progress dialog of one operation started by [launchWithDelayedProgress]: what the operation
 * reports through [update], held back until the timer reveals it.
 *
 * Every publish and every clear checks that the operation still owns the dialog. While it is
 * hidden the screen stays usable, so a second operation of the same kind can start and take over
 * the handle — and with it the dialog, which carries that operation's only Cancel button. An
 * operation that has lost the handle never shows its dialog, never updates it, and never closes the
 * newer one's. A handle the Cancel button has cleared still lets the cancelled operation close the
 * dialog it showed once it has finished unwinding, and nothing else.
 *
 * Thread-safe: progress may be reported from any thread, as a move's rename step does from IO.
 */
class DelayedProgress<T : Any> internal constructor(
    private val placeholder: T,
    private val ownJob: Job,
    private val owner: () -> Job?,
    private val publish: (T?) -> Unit
) {
    private val lock = Any()
    private var latest: T? = null
    private var isShown = false
    private var isFinished = false

    /** Keeps [progress] as the latest, and shows it if the dialog is up and still this one's. */
    fun update(progress: T) {
        synchronized(lock) {
            if (isFinished) return
            latest = progress
            if (isShown && owner() === ownJob) publish(progress)
        }
    }

    /**
     * Closes the dialog if this operation showed it and no newer operation has taken it over, and
     * ignores every later [update]. Safe to call more than once.
     */
    fun clear() {
        synchronized(lock) {
            if (isFinished) return
            isFinished = true
            if (!isShown) return
            val current = owner()
            if (current == null || current === ownJob) publish(null)
        }
    }

    /** Shows the dialog, with the latest progress or the placeholder, if this operation owns it. */
    internal fun reveal() {
        synchronized(lock) {
            if (isFinished || owner() !== ownJob) return
            isShown = true
            publish(latest ?: placeholder)
        }
    }
}
