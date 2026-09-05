package com.mauriciotogneri.fileexplorer.data.util

/** The two characters that turn a search query into a pattern rather than a substring. */
private const val WILDCARDS = "*?"

private const val STAR = '*'.code
private const val QUESTION = '?'.code

/** No character: the pattern is spent while the name still has more to match. */
private const val NONE = -1

/** Whether [query] asks to be matched as a pattern rather than as the substring it would otherwise be. */
fun queryHasWildcard(query: String): Boolean = query.any { it in WILDCARDS }

/**
 * [query] read as a filename pattern, or null when it holds no wildcard and should be matched as the
 * plain substring it has always been.
 *
 * `*` stands for any run of characters and `?` for exactly one; everything else is literal, so a
 * query like `report (1).pdf` keeps working. Matching is against a whole name rather than within
 * one, so `*.txt` does not match `notes.txt.bak`, and it folds case over the whole Unicode range,
 * as the substring match it replaces does.
 */
fun globPatternOrNull(query: String): GlobPattern? =
    if (queryHasWildcard(query)) GlobPattern(query) else null

/**
 * A filename pattern, matched by walking the name and the pattern together.
 *
 * Deliberately not a compiled [Regex]. A glob translated to a regex becomes `.*literal.*literal…`,
 * which is the textbook shape for catastrophic backtracking: measured on this project's JVM, a
 * ten-star query against a merely 36-character non-matching name took five seconds and grew about
 * fivefold per four characters added. `java.util.regex` polls no interrupt, so the search walk's
 * `ensureActive()` — which runs between names, never inside a match — cannot cancel one already
 * running, and the coroutine would be cancelled while its IO thread stayed pinned. The walk below
 * takes a single backtracking point per `*` instead, which is O(name x pattern) at worst.
 *
 * Working on code points rather than chars is what lets `?` stand for one character the user can
 * see: an emoji is two chars and must not need two `?`.
 */
class GlobPattern internal constructor(private val pattern: String) {

    fun matches(name: String): Boolean {
        var patternIndex = 0
        var nameIndex = 0

        // Where to resume from if the rest of the pattern turns out not to fit: the most recent
        // `*` gives back one more character of the name each time the tail fails.
        var starIndex = NONE
        var nameAfterStar = NONE

        while (nameIndex < name.length) {
            val patternChar = if (patternIndex < pattern.length) pattern.codePointAt(patternIndex) else NONE
            val nameChar = name.codePointAt(nameIndex)

            when {
                patternChar == STAR -> {
                    starIndex = patternIndex
                    nameAfterStar = nameIndex
                    patternIndex++
                }

                patternChar == QUESTION || equalsIgnoreCase(patternChar, nameChar) -> {
                    patternIndex += Character.charCount(patternChar)
                    nameIndex += Character.charCount(nameChar)
                }

                starIndex != NONE -> {
                    patternIndex = starIndex + 1
                    nameAfterStar += Character.charCount(name.codePointAt(nameAfterStar))
                    nameIndex = nameAfterStar
                }

                else -> return false
            }
        }

        // Trailing stars are the only thing allowed to match nothing at all.
        while (patternIndex < pattern.length && pattern.codePointAt(patternIndex) == STAR) {
            patternIndex++
        }

        return patternIndex == pattern.length
    }

    /** Folds the way `String.contains(ignoreCase = true)` does, over the whole Unicode range. */
    private fun equalsIgnoreCase(first: Int, second: Int): Boolean {
        if (first == second) return true
        if (first == NONE || second == NONE) return false

        val firstUpper = Character.toUpperCase(first)
        val secondUpper = Character.toUpperCase(second)

        return firstUpper == secondUpper ||
            Character.toLowerCase(firstUpper) == Character.toLowerCase(secondUpper)
    }
}
