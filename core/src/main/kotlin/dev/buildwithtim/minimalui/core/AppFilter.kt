package dev.buildwithtim.minimalui.core

import java.text.Normalizer
import java.util.Locale

/**
 * The apps a type-to-filter [query] keeps: those with a word in their label that starts with it, ignoring case and
 * accents (`ecl` finds `Éclair`). Words break at anything but a letter or digit, so `9` finds `K-9 Mail`, but `ma`
 * does not find `Camera`: matching inside a word would keep most of the list after the first key or two.
 * Apps whose label starts with the query come first, so Enter on the top row opens `Maps` rather than `Google Maps`;
 * otherwise the order is kept. An empty query keeps every app.
 */
fun List<LauncherApp>.filteredBy(query: String): List<LauncherApp> {
    if (query.isEmpty()) return this
    val wanted = query.folded()
    val (labelStarts, wordStarts) = filter { it.label.folded().hasWordStartingWith(wanted) }
        .partition { it.label.folded().startsWith(wanted) }
    return labelStarts + wordStarts
}

private fun String.hasWordStartingWith(prefix: String): Boolean =
    indices.any { (it == 0 || !this[it - 1].isLetterOrDigit()) && startsWith(prefix, startIndex = it) }

// Lower case without accents: `É` decomposes into `E` plus a combining accent, which is then dropped.
private fun String.folded(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

private val COMBINING_MARKS = Regex("\\p{Mn}+")
