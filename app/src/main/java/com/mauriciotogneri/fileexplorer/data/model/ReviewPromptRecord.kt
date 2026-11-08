package com.mauriciotogneri.fileexplorer.data.model

/**
 * What the device remembers about asking for a Play Store rating. Nothing here leaves the device.
 *
 * [activeDays] and [actionCount] measure engagement since the last ask (or since tracking began,
 * before the first one): asking resets both, so a retry has to be earned again rather than being
 * due the moment its waiting period ends.
 *
 * @param askCount how many times the review flow has been launched.
 * @param firstAskAt when the first ask happened, or [NEVER] before it.
 * @param activeDays distinct local calendar days the app was in the foreground.
 * @param lastActiveDay the local epoch day last counted in [activeDays], or [NEVER].
 * @param actionCount clean successes of the actions that count as using the app.
 */
data class ReviewPromptRecord(
    val askCount: Int = 0,
    val firstAskAt: Long = NEVER,
    val activeDays: Int = 0,
    val lastActiveDay: Long = NEVER,
    val actionCount: Int = 0
) {
    companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
