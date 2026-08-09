package com.mauriciotogneri.fileexplorer.testutil

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.mauriciotogneri.fileexplorer.ui.components.BADGE_DOT_TEST_TAG

/**
 * Matchers shared across instrumentation tests.
 *
 * These exist so assertions stay pinned to production semantics (a test tag the component itself
 * declares, a Material role) rather than to a literal that would silently stop matching.
 */

/**
 * Matches the new-feature dot rendered by `BadgeDot`, which has no text to match on.
 *
 * The dot carries nothing but its tag, so a clickable row wrapping it absorbs the tag into its
 * merged node: match this with `useUnmergedTree = true` whenever the badge sits inside one.
 */
fun hasBadgeDot(): SemanticsMatcher = hasTestTag(BADGE_DOT_TEST_TAG)

/** Matches a node with the given [role], e.g. to disambiguate a dialog title from its button. */
fun hasRole(role: Role): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/**
 * Matches the *button* carrying [text].
 *
 * Several dialogs use the same string for both their title and their confirm button
 * (`action_compress`, `delete_confirm_title`), so a bare text match is ambiguous.
 */
fun buttonWithText(text: String): SemanticsMatcher = hasText(text) and hasRole(Role.Button)

/** Matches a clickable node carrying [text], for rows that are not Material buttons. */
fun clickableWithText(text: String): SemanticsMatcher = hasText(text) and hasClickAction()

/**
 * Matches the node whose click action is labelled [label], i.e. `clickable(onClickLabel = ...)`.
 *
 * For targets that describe themselves by what tapping them does rather than by their content — an
 * icon whose `contentDescription` is the file name, or one behind an image whose description
 * depends on whether the image finished loading.
 */
fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action is labelled '$label'") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
