### [b/resource-and-configuration-parity/feedback-screen/placeholder-contrast-below-floor] The feedback placeholder drops below the legibility floor in the light theme

- **Location:**
  `app/src/main/java/com/mauriciotogneri/fileexplorer/activities/FeedbackActivity.kt:357`
  (related: `app/src/main/java/com/mauriciotogneri/fileexplorer/ui/theme/Color.kt:26`
  `onSurfaceLight`,
  `:37` `surfaceLight`, `:43` `onSurfaceVariantLight`)
- **Severity:** Low
- **Confidence:** High
- **Likelihood:** High — the placeholder is shown whenever the field is empty, which is the screen's
  initial state, so every first visit to Feedback in the light theme renders it.
- **Defect:** The placeholder was given an explicit
  `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`, overriding M3's
  `unfocusedPlaceholderColor` (`onSurfaceVariant`, full alpha). Composited over the screen's
  container (`MaterialTheme.colorScheme.surface` = `#FBFBFB`, set at `FeedbackActivity.kt:341`),
  `#202020` at 60% yields `#787878`, a contrast ratio of **4.27:1** against the container — under
  the 4.5:1 WCAG AA floor for body-size text. The baseline resolved to `onSurfaceVariantLight`
  `#404040` for **10.02:1**. The dark theme is unaffected (5.56:1, passing). Pinning the color also
  defeats M3's *disabled* placeholder color, so the placeholder still renders at full enabled weight
  while `isSubmitting` has the field disabled.
- **Trigger:** Light theme (or SYSTEM on a light device) → Home drawer → Feedback → the text field
  is empty.
- **Evidence / verification:** Traced Home drawer → `FeedbackActivity.onCreate` → `FeedbackScreen` →
  `OutlinedTextField(placeholder = …)`. Both ratios were recomputed independently from the sRGB
  relative-luminance formula: composite `0.6·0x20 + 0.4·0xFB = #787878`, luminance `0.18784` against
  `#FBFBFB` at `0.96469`, giving `(0.96469 + 0.05) / (0.18784 + 0.05) = 4.266`; the baseline
  `#404040` gives luminance `0.05127` and `10.02`. Refutation attempts, all failed: the container is
  not darker than `surface` (`OutlinedTextField`'s own container is transparent); the placeholder
  inherits `bodyLarge` (~16sp), below the 18pt/24sp large-text threshold that would lower the floor
  to 3:1; `OutlinedTextFieldDefaults.colors()` does not already dim the placeholder, so the override
  is not a no-op, and an explicit `color` on the inner `Text` wins over the provided content color;
  the dark theme passes. `git show 9e87306:…/FeedbackActivity.kt` line 357 reads
  `placeholder = { Text(stringResource(R.string.feedback_hint)) }` with no color override — the
  override is added by this branch. Remaining assumption: that the change had no deliberate motive
  beyond visual separation; `onSurfaceVariantLight` `#404040` is close to the entered text's
  `#202020`, so a legitimate motive plausibly existed.
- **Suggested fix:** Keep the two properties the override gave up. Rather than lowering the alpha on
  `onSurface`, leave the placeholder to M3's default and, if the placeholder needs to read as
  lighter than entered text, introduce a lighter `onSurfaceVariant` token for the light scheme that
  still clears 4.5:1 against `surfaceLight`. That also restores the disabled-state color.