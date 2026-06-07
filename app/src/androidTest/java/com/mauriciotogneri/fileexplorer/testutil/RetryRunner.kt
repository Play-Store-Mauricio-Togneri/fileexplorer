package com.mauriciotogneri.fileexplorer.testutil

import android.util.Log
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.internal.AssumptionViolatedException
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/**
 * Drop-in replacement for `AndroidJUnit4` that retries tests annotated with [Retry].
 *
 * Why a runner and not a `TestRule`: the Compose test rule sets up a one-shot coroutine test
 * environment ("Only a single call to `runTest` can be performed during one test"), so an outer
 * rule cannot simply re-`evaluate()` it. This runner instead retries at the [methodBlock] level,
 * which builds a fresh test instance — and therefore a fresh Compose environment and activity — for
 * every attempt.
 *
 * Only methods (or classes) marked [Retry] are retried; everything else runs exactly once, so a
 * genuine regression in a non-flaky test still surfaces immediately. Use [Retry] sparingly, only
 * for tests whose flakiness is a confirmed emulator-load/timeout artifact.
 *
 * Apply with `@RunWith(RetryRunner::class)`.
 */
class RetryRunner(klass: Class<*>) : AndroidJUnit4ClassRunner(klass) {

    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        if (isIgnored(method)) {
            notifier.fireTestIgnored(describeChild(method))
            return
        }
        val attempts = retryAttempts(method)
        if (attempts > 1) {
            val description = describeChild(method)
            runLeaf(retryStatement(method, description, attempts), description, notifier)
        } else {
            // No @Retry: run exactly once so a genuine regression surfaces immediately.
            super.runChild(method, notifier)
        }
    }

    private fun retryAttempts(method: FrameworkMethod): Int {
        val retry = method.getAnnotation(Retry::class.java)
            ?: getTestClass().getAnnotation(Retry::class.java)
        return retry?.attempts ?: 1
    }

    private fun retryStatement(method: FrameworkMethod, description: Description, attempts: Int) =
        object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 1..attempts) {
                    try {
                        // A fresh test instance (and Compose environment) is built per attempt.
                        methodBlock(method).evaluate()
                        return
                    } catch (assumption: AssumptionViolatedException) {
                        // A skipped test is not a flake — surface it immediately.
                        throw assumption
                    } catch (error: Throwable) {
                        lastError = error
                        Log.w(TAG, "${description.displayName} failed on attempt $attempt/$attempts", error)
                    }
                }
                throw lastError ?: IllegalStateException("Retry requires attempts >= 1")
            }
        }

    private companion object {
        const val TAG = "RetryRunner"
    }
}
