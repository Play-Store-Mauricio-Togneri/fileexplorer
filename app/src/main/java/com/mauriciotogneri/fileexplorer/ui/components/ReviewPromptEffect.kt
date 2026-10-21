package com.mauriciotogneri.fileexplorer.ui.components

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How long the screen has to stay in front and idle before an armed ask fires. Opening a file in
 * another app counts as a success before this screen has even paused, and a just-finished
 * operation is still settling its list; asking at once would land on top of either.
 */
internal const val REVIEW_PROMPT_SETTLE_MILLIS = 1_500L

/**
 * Asks for a Play Store rating once a success has made one due, this screen is resumed — the top
 * destination of its Activity, in front — and [isCalm] says nothing is on top of or happening in
 * the list. Losing any of the three before the settle delay ends cancels the ask.
 *
 * Draws nothing. [pending] and [ask] are parameters so tests can drive the effect without Play.
 */
@Composable
fun ReviewPromptEffect(
    isCalm: Boolean,
    pending: StateFlow<Boolean> = ReviewPrompt.pending,
    ask: suspend (Activity) -> Unit = ReviewPrompt::ask
) {
    val activity = LocalActivity.current ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsCalm by rememberUpdatedState(isCalm)
    val currentAsk by rememberUpdatedState(ask)

    LaunchedEffect(lifecycleOwner, pending, activity) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            combine(pending, snapshotFlow { currentIsCalm }) { isPending, calm -> isPending && calm }
                .distinctUntilChanged()
                .collectLatest { ready ->
                    if (ready) {
                        delay(REVIEW_PROMPT_SETTLE_MILLIS)
                        currentAsk(activity)
                    }
                }
        }
    }
}
