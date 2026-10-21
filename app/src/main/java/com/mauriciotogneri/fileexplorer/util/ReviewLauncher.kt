package com.mauriciotogneri.fileexplorer.util

import android.app.Activity
import android.content.Context
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Google Play's in-app review flow, behind an interface so the rules around it can be tested. */
interface ReviewLauncher {
    /**
     * Asks Play for a review flow to show, or null when it refused or could not be reached — a
     * device without the Play Store, or an install that did not come from it.
     */
    suspend fun prepare(): PreparedReview?
}

fun interface PreparedReview {
    /** Hands the flow to Play, which alone decides whether a dialog actually appears. */
    suspend fun launch(activity: Activity)
}

/**
 * @param createManager builds Play's review manager, on first use rather than during app startup.
 * Injectable so tests can hand in a manager whose requests fail.
 */
class PlayReviewLauncher(
    context: Context,
    createManager: (Context) -> ReviewManager = ReviewManagerFactory::create
) : ReviewLauncher {
    private val appContext = context.applicationContext

    private val manager: ReviewManager by lazy { createManager(appContext) }

    override suspend fun prepare(): PreparedReview? {
        return try {
            val reviewInfo = manager.requestReview()

            PreparedReview { activity ->
                try {
                    manager.launchReview(activity, reviewInfo)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
