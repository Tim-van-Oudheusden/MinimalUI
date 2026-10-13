package dev.buildwithtim.minimalui.core

/**
 * The type-to-filter query after typing the character [codePoint]: letters and digits are appended. Anything else
 * (space, Enter, punctuation, a key with no character) gives null, so the key keeps its usual job.
 */
fun String.plusTyped(codePoint: Int): String? =
    if (Character.isLetterOrDigit(codePoint)) StringBuilder(this).appendCodePoint(codePoint).toString() else null

/** The type-to-filter query after Backspace: the last typed character removed, or null when there is none. */
fun String.minusLastTyped(): String? = if (isEmpty()) null else substring(0, offsetByCodePoints(length, -1))
