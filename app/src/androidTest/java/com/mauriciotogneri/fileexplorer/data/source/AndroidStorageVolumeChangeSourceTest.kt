package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [AndroidStorageVolumeChangeSource]'s registration against the real platform.
 *
 * Registration is the one line of this source no JVM test can reach, and it is the line most likely
 * to fail silently: [AndroidStorageVolumeChangeSource] catches whatever the platform raises,
 * reports it and closes the flow, so a rejected filter, a flag the platform will not accept, or a
 * permission the registration turns out to need all look identical to a device where no volume
 * happened to change. Every unit test around this source drives a fake, so nothing else would
 * notice — the home screen would simply stop refreshing when a card is inserted, on whichever API
 * levels the rejection applies to, with the whole suite green.
 *
 * What this asserts is therefore that the flow is still *open* after registration has had time to
 * run. A source that failed to register completes without emitting, which `first()` reports as a
 * [NoSuchElementException]; a source that registered and simply has nothing to say keeps the
 * collector waiting, which is the timeout.
 *
 * Delivery of an actual volume broadcast is deliberately not asserted. The honest ways to produce
 * one are physically mounting a volume, which no automated run can do, or injecting it from the
 * shell — and an injected broadcast is not sent by the system uid, so it is not established that it
 * reaches a receiver registered `RECEIVER_NOT_EXPORTED`. A test written on that assumption would
 * fail for a reason that has nothing to do with this app. The filter's five actions and its `file`
 * data scheme are consequently still unproven on device; that gap is real and is the reason this
 * file says so rather than implying otherwise.
 */
@RunWith(AndroidJUnit4::class)
class AndroidStorageVolumeChangeSourceTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun changes_registersWithoutBeingRejectedByThePlatform() = runBlocking {
        val source = AndroidStorageVolumeChangeSource(context)

        try {
            withTimeout(REGISTRATION_SETTLE_MS) { source.changes().first() }
            // A volume genuinely changed while the test ran. Unlikely, and still proof that the
            // registration was accepted, which is all this asserts.
        } catch (_: TimeoutCancellationException) {
            // Registered, and nothing happened to the device's volumes. The expected outcome.
        } catch (e: NoSuchElementException) {
            throw AssertionError(
                "Registering the volume-change receiver was rejected by the platform, so the home " +
                    "screen would never notice a card being inserted or removed on this API level",
                e
            )
        }
    }

    private companion object {
        // Long enough that a registration which is going to fail has already failed, since the
        // failure path closes the flow immediately rather than after any wait.
        const val REGISTRATION_SETTLE_MS = 2_000L
    }
}
