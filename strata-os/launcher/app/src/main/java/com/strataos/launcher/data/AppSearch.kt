package com.strataos.launcher.data

import java.text.Collator
import java.util.Locale

/**
 * How well a query matches an app label. Lower ordinals rank first.
 */
enum class MatchKind {
    /** The label starts with the query: "cam" → "Camera". */
    LabelStart,

    /** The query starts a later word in the label: "maps" → "Google Maps", "tube" → "YouTube". */
    WordStart,

    /** The query only appears inside a word: "rome" → "Chrome". */
    Inside,
}

/**
 * App search for the drawer.
 *
 * Word-start matching is written to behave like the default All Apps search in AOSP
 * Launcher3 (`StringMatcherUtility` and `DefaultAppSearchAlgorithm`, Apache 2.0). This is a
 * fresh implementation, not a copy. `docs/app-search-trace.md` has the trace and the
 * evidence, and `tools/launcher3-diff` compares it with the upstream code.
 *
 * - A query matches where a word starts. Words begin after a space, at a capital in
 *   camelCase ("YouTube"), at the first digit of a number and at a symbol ("t-mobile").
 * - Comparison ignores case and accents ("cafe" finds "Café") and follows the device
 *   locale's collation rules.
 * - A query that contains a Han character matches anywhere in the label, because Chinese
 *   text has no spaces between words.
 *
 * Strata adds two things of its own: a query found only inside a word still matches,
 * ranked last, so nothing the old substring search found goes missing; and results are
 * ranked label start, then word start, then inside.
 *
 * Not thread-safe: use one instance per thread (the drawer only searches on the main thread).
 */
class AppSearch(private val locale: Locale = Locale.getDefault()) {

    private val collator: Collator = Collator.getInstance(locale).apply {
        // Primary strength: "a", "A" and "á" are all equal.
        strength = Collator.PRIMARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }

    /**
     * Returns the items that match [query], best first. Items with the same [MatchKind]
     * keep their order from [items]. A blank query returns no items.
     */
    fun <T> filter(query: String, items: List<T>, label: (T) -> String): List<T> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return items
            .mapNotNull { item -> match(q, label(item))?.let { kind -> kind to item } }
            .sortedBy { it.first.ordinal }
            .map { it.second }
    }

    /** How [query] matches [label], or null when it doesn't. */
    fun match(query: String, label: String): MatchKind? {
        if (query.isEmpty()) return null
        val at = wordStartMatch(query, label)
        return when {
            at == 0 -> MatchKind.LabelStart
            at > 0 -> MatchKind.WordStart
            label.contains(query, ignoreCase = true) -> MatchKind.Inside
            else -> null
        }
    }

    /** True when [query] matches the start of a word in [label], as Launcher3 decides it. */
    fun matchesWordStart(query: String, label: String): Boolean = wordStartMatch(query, label) >= 0

    /**
     * The index in [label] of the first word start where [query] matches, or -1.
     */
    private fun wordStartMatch(query: String, label: String): Int {
        val q = query.lowercase(locale)
        if (q.isEmpty() || label.length < q.length) return -1

        if (q.hasHan()) return label.lowercase(locale).indexOf(q)

        // One general category per UTF-16 index. At the second half of a surrogate pair this
        // is SURROGATE, which never starts a word, so emoji and other supplementary
        // characters can only start a match at their first half.
        val types = IntArray(label.length) { Character.getType(label.codePointAt(it)) }
        fun typeAt(index: Int) = if (index in types.indices) types[index] else UNASSIGNED

        val lastStart = label.length - q.length
        for (i in 0..lastStart) {
            if (startsWord(prev = typeAt(i - 1), cur = types[i], next = typeAt(i + 1)) &&
                isPrefixAtPrimaryStrength(q, label.substring(i, i + q.length))
            ) {
                return i
            }
        }
        return -1
    }

    /**
     * True when [window] equals [query] at primary strength, or [query] is a prefix of it.
     * Appending U+FFFF, which collates after every other character, turns the prefix test
     * into a single comparison.
     */
    private fun isPrefixAtPrimaryStrength(query: String, window: String): Boolean {
        val order = collator.compare(query, window)
        return order == 0 || (order < 0 && collator.compare(query + '￿', window) >= 0)
    }

    private fun String.hasHan(): Boolean =
        codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }

    private companion object {
        // Character's category constants are bytes; Character.getType returns an int.
        const val UNASSIGNED = Character.UNASSIGNED.toInt()
        const val UPPERCASE = Character.UPPERCASE_LETTER.toInt()
        const val LOWERCASE = Character.LOWERCASE_LETTER.toInt()
        const val TITLECASE = Character.TITLECASE_LETTER.toInt()
        const val MODIFIER_LETTER = Character.MODIFIER_LETTER.toInt()
        const val OTHER_LETTER = Character.OTHER_LETTER.toInt()
        const val DIGIT = Character.DECIMAL_DIGIT_NUMBER.toInt()
        const val LETTER_NUMBER = Character.LETTER_NUMBER.toInt()
        const val OTHER_NUMBER = Character.OTHER_NUMBER.toInt()
        const val OTHER_SYMBOL = Character.OTHER_SYMBOL.toInt()

        val SEPARATORS = setOf(
            UNASSIGNED, // the start of the label
            Character.SPACE_SEPARATOR.toInt(),
            Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt(),
        )
        val LETTERS = setOf(UPPERCASE, LOWERCASE, TITLECASE, MODIFIER_LETTER, OTHER_LETTER)
        val NUMBERS = setOf(DIGIT, LETTER_NUMBER, OTHER_NUMBER)
        val SYMBOLS = setOf(
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
        )

        /** What may follow a capital that is part of an all-caps run like "LEGO" or "HD2". */
        val CAPS_RUN_FOLLOWERS = setOf(UPPERCASE, OTHER_SYMBOL, DIGIT, UNASSIGNED)

        /**
         * Whether a word starts at a character of category [cur], between [prev] and [next]
         * (UNASSIGNED past either end of the label).
         */
        fun startsWord(prev: Int, cur: Int, next: Int): Boolean {
            if (prev in SEPARATORS) return true
            return when (cur) {
                // "YouTube" at T. In a caps run ("HTMLViewer") only the capital that
                // starts the run or comes before a lower-case letter counts.
                UPPERCASE -> next !in CAPS_RUN_FOLLOWERS || prev != UPPERCASE
                TITLECASE -> prev != UPPERCASE
                LOWERCASE -> prev !in LETTERS
                in NUMBERS -> prev !in NUMBERS
                in SYMBOLS -> true
                else -> false
            }
        }
    }
}
