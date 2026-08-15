package com.arulsundaresan.arulremindme.nlp

/**
 * Shared building blocks for the parser rules.
 *
 * Why not `\b`: Java's word boundary is defined against the ASCII `\w` class, so it behaves
 * incorrectly around Tamil text (`\bநாளை\b` does not do what it looks like it does). These
 * lookaround boundaries are Unicode-aware and work identically for Tamil, Latin and digits.
 */
internal object Patterns {

    /** Left boundary — the previous character must not be a letter or digit. */
    const val LB = "(?<![\\p{L}\\p{N}])"

    /** Right boundary — the next character must not be a letter or digit. */
    const val RB = "(?![\\p{L}\\p{N}])"

    /**
     * Builds a regex alternation from [terms], longest first.
     *
     * Longest-first matters: without it "நாளை" would win over "நாளைக்கு" and the parser
     * would leave a stray "க்கு" in the reminder text.
     */
    fun alternation(terms: Iterable<String>): String =
        terms.distinct()
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

    fun regex(pattern: String): Regex = Regex(pattern, RegexOption.IGNORE_CASE)
}

/**
 * Lowercases without ever changing the string length.
 *
 * [String.lowercase] can change length for a few characters (U+0130 becomes two code units),
 * which would break the index ranges the parser uses to cut the date/time expressions out of
 * the *original* input. Mapping char-by-char keeps indices aligned.
 */
internal fun String.lowercaseKeepingLength(): String =
    buildString(length) { this@lowercaseKeepingLength.forEach { append(it.lowercaseChar()) } }

/** Replaces [range] with spaces so later rules cannot match text already consumed. */
internal fun String.maskRange(range: IntRange?): String {
    if (range == null || range.isEmpty()) return this
    val chars = toCharArray()
    for (i in range) if (i in chars.indices) chars[i] = ' '
    return String(chars)
}
