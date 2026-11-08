package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import com.mauriciotogneri.fileexplorer.data.repository.ReviewPromptRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ReviewEligibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides when to ask for a Play Store rating, for the life of one process. [ReviewPrompt] is the
 * app-wide entry point; this holds the logic so it can be tested without an Application.
 *
 * A success that leaves the user eligible arms [pending]; the home screen or a folder list then
 * calls [ask] once it is in front and idle. Any failure blocks asking for the rest of the process,
 * so a user is never sent to the Play Store moments after the app let them down. At most one ask
 * per process, whatever happens to it.
 *
 * @param installedAt when the app was first installed, or null when that cannot be read — which
 * means never ask, since the install age is one of the conditions.
 * @param isEnabled the owner's remote switch. Read again before launching, so switching it off also
 * drops an ask already armed. It blocks only the ask: engagement is still counted, so switching it
 * back on needs nothing re-earned.
 */
internal class ReviewPromptGate(
    private val repository: ReviewPromptRepository,
    private val launcher: ReviewLauncher,
    private val installedAt: suspend () -> Long?,
    private val isEnabled: suspend () -> Boolean,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault
) {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val failedThisSession = AtomicBoolean(false)
    private val askClaimed = AtomicBoolean(false)

    // Spares the store a read on every screen resume: a day already counted by this process needs
    // nothing more.
    @Volatile
    private var lastCountedDay: Long? = null

    fun onScreenResumed() {
        scope.launch {
            val today = ReviewEligibility.localEpochDay(now(), timeZone())
            if (today == lastCountedDay) return@launch

            if (repository.recordActiveDay(today) != null) {
                lastCountedDay = today
            }
        }
    }

    // Counted even when this process can no longer ask: a failure or a spent ask blocks the ask,
    // not the engagement it measures, which the next process — or the retry — still needs.
    fun onActionSucceeded() {
        scope.launch {
            val record = repository.recordAction() ?: return@launch
            if (failedThisSession.get() || askClaimed.get()) return@launch

            val installed = installedAt() ?: return@launch

            if (ReviewEligibility.dueAttempt(record, now(), installed) != null && isEnabled()) {
                _pending.value = true
            }
        }
    }

    fun onActionFailed() {
        failedThisSession.set(true)
        _pending.value = false
    }

    /** Drops an armed ask without spending it, so a fresh launch never opens with the dialog. */
    fun disarm() {
        _pending.value = false
    }

    /**
     * Launches the review flow if the user is still eligible. The record is read again rather than
     * trusted from when [pending] was armed, and [failedThisSession] is checked here too: a success
     * still being recorded can arm [pending] just after a failure cleared it.
     *
     * The ask is saved before the flow is launched, and nothing is launched if the save fails: an
     * ask the store forgot would be repeated on the next eligible success. A refusal from Play
     * spends nothing, since no flow was launched.
     *
     * [pending] is cleared only once the ask has settled. The screen calling this is collecting
     * [pending] and cancels its call when the value changes, so clearing it up front would cancel
     * every ask partway through. A call cancelled before it settles — the screen paused, or got
     * busy, or a failure arrived — releases the claim and leaves [pending] as it was, so the ask can
     * still happen once the screen is calm again.
     */
    suspend fun ask(activity: Activity) {
        if (failedThisSession.get() || !askClaimed.compareAndSet(false, true)) {
            _pending.value = false
            return
        }

        var settled = false
        try {
            val due = prepareDueReview()
            settled = true

            if (due != null) {
                AnalyticsTracker.trackReviewPromptRequested(due.attempt)
                due.review.launch(activity)
            }
        } finally {
            if (settled) {
                _pending.value = false
            } else {
                askClaimed.set(false)
            }
        }
    }

    /** The review to launch, already saved as asked, or null when there is nothing to ask. */
    private suspend fun prepareDueReview(): DueReview? {
        val record = repository.getRecord() ?: return null
        val installed = installedAt() ?: return null
        val attempt = ReviewEligibility.dueAttempt(record, now(), installed) ?: return null
        if (!isEnabled()) return null
        val review = launcher.prepare() ?: return null
        repository.recordAsked(now()) ?: return null

        return DueReview(attempt, review)
    }

    private class DueReview(val attempt: Int, val review: PreparedReview)
}
