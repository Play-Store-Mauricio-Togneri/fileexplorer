package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import com.mauriciotogneri.fileexplorer.data.source.ReviewPromptSource
import com.mauriciotogneri.fileexplorer.data.util.ReviewEligibility

val Context.reviewPromptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "review_prompt",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Every method returns the record as stored afterwards, or null when the store failed — which
 * callers treat as "never ask", since a record that cannot be trusted cannot say the user is
 * engaged.
 *
 * Counters stop at the threshold [ReviewEligibility] needs and nothing changes once the last ask
 * is spent, so a user who keeps using the app costs no further writes.
 */
class ReviewPromptRepository(private val source: ReviewPromptSource) {

    suspend fun getRecord(): ReviewPromptRecord? = source.getRecord()

    /**
     * Counts [epochDay] as a day the app was used, unless it is not later than the last one
     * counted: the same day seen twice, or a clock moved backwards, adds nothing.
     */
    suspend fun recordActiveDay(epochDay: Long): ReviewPromptRecord? = source.updateRecord { record ->
        if (record.askCount >= ReviewEligibility.MAX_ASKS || epochDay <= record.lastActiveDay) {
            record
        } else {
            record.copy(
                activeDays = minOf(record.activeDays + 1, ReviewEligibility.MIN_ACTIVE_DAYS),
                lastActiveDay = epochDay
            )
        }
    }

    suspend fun recordAction(): ReviewPromptRecord? = source.updateRecord { record ->
        if (record.askCount >= ReviewEligibility.MAX_ASKS) {
            record
        } else {
            record.copy(actionCount = minOf(record.actionCount + 1, ReviewEligibility.MIN_ACTIONS))
        }
    }

    /** Spends one ask and resets the engagement counters, so the retry has to be earned again. */
    suspend fun recordAsked(nowMillis: Long): ReviewPromptRecord? = source.updateRecord { record ->
        record.copy(
            askCount = record.askCount + 1,
            firstAskAt = if (record.askCount == 0) nowMillis else record.firstAskAt,
            activeDays = 0,
            actionCount = 0
        )
    }
}
