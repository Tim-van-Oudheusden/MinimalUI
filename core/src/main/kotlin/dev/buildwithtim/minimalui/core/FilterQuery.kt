package dev.buildwithtim.minimalui.core

/**
 * The type-to-filter query after typing the character [codePoint]: letters and digits are appended, and so are
 * combining marks after them (the vowel sign `ि` in `हि`). Anything else (space, Enter, punctuation, a key with no
 * character, a mark with nothing before it) gives null, so the key keeps its usual job.
 */
fun String.plusTyped(codePoint: Int): String? {
    val accepted = Character.isLetterOrDigit(codePoint) || (isNotEmpty() && codePoint.isMark())
    return if (accepted) StringBuilder(this).appendCodePoint(codePoint).toString() else null
}

/** The type-to-filter query after Backspace: the last typed character removed, or null when there is none. */
fun String.minusLastTyped(): String? = if (isEmpty()) null else substring(0, offsetByCodePoints(length, -1))

/** A letter, a digit, or a mark that belongs to the letter before it: part of a word, not a break between words. */
internal fun Int.isWordPart(): Boolean = Character.isLetterOrDigit(this) || isMark()

private fun Int.isMark(): Boolean = Character.getType(this).toByte() in MARKS

private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)
