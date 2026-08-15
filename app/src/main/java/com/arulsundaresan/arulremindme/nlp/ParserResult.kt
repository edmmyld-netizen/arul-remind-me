package com.arulsundaresan.arulremindme.nlp

/**
 * Outcome of one parse. The ViewModel maps these onto UI state; it never re-derives what
 * is missing by inspecting fields itself.
 */
sealed interface ParserResult {

    /** Reminder text, date and time are all present — go straight to the confirmation card. */
    data class Complete(val parsed: ParsedReminderInput) : ParserResult

    /** Understood something, but the schedule is not usable yet. Nothing may be saved. */
    data class Incomplete(
        val parsed: ParsedReminderInput,
        val missing: MissingInfo
    ) : ParserResult

    /** Nothing usable at all. */
    data class Failure(
        val originalInput: String,
        val reason: ParseFailure
    ) : ParserResult
}

enum class MissingInfo {
    /** Neither a date nor a time was found. */
    DATE_AND_TIME,
    DATE,
    TIME,

    /** An hour was found but AM/PM is genuinely ambiguous — ask, do not guess. */
    TIME_MERIDIEM
}

enum class ParseFailure {
    EMPTY_INPUT,

    /** A date/time was understood but nothing was left to be reminded about. */
    NO_REMINDER_TEXT
}
