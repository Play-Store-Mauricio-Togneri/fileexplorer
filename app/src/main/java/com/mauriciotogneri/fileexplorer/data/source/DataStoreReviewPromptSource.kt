package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataStoreReviewPromptSource(
    private val dataStore: DataStore<Preferences>
) : ReviewPromptSource {

    override suspend fun getRecord(): ReviewPromptRecord? = withContext(Dispatchers.IO) {
        dataStore.readSafely("read_review_prompt", null) { preferences ->
            preferences.toRecord()
        }
    }

    // A record equal to the stored one costs no disk write: DataStore skips the flush when a
    // transform leaves the contents unchanged, which is what keeps saturated counters free.
    override suspend fun updateRecord(
        transform: (ReviewPromptRecord) -> ReviewPromptRecord
    ): ReviewPromptRecord? = withContext(Dispatchers.IO) {
        var written: ReviewPromptRecord? = null
        val stored = dataStore.editSafely("write_review_prompt") { preferences ->
            val updated = transform(preferences.toRecord())
            preferences.write(updated)
            written = updated
        }

        if (stored) written else null
    }

    private fun Preferences.toRecord(): ReviewPromptRecord = ReviewPromptRecord(
        askCount = this[KEY_ASK_COUNT] ?: 0,
        firstAskAt = this[KEY_FIRST_ASK_AT] ?: ReviewPromptRecord.NEVER,
        activeDays = this[KEY_ACTIVE_DAYS] ?: 0,
        lastActiveDay = this[KEY_LAST_ACTIVE_DAY] ?: ReviewPromptRecord.NEVER,
        actionCount = this[KEY_ACTION_COUNT] ?: 0
    )

    private fun MutablePreferences.write(record: ReviewPromptRecord) {
        this[KEY_ASK_COUNT] = record.askCount
        this[KEY_FIRST_ASK_AT] = record.firstAskAt
        this[KEY_ACTIVE_DAYS] = record.activeDays
        this[KEY_LAST_ACTIVE_DAY] = record.lastActiveDay
        this[KEY_ACTION_COUNT] = record.actionCount
    }

    companion object {
        private val KEY_ASK_COUNT = intPreferencesKey("ask_count")
        private val KEY_FIRST_ASK_AT = longPreferencesKey("first_ask_at")
        private val KEY_ACTIVE_DAYS = intPreferencesKey("active_days")
        private val KEY_LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        private val KEY_ACTION_COUNT = intPreferencesKey("action_count")
    }
}
