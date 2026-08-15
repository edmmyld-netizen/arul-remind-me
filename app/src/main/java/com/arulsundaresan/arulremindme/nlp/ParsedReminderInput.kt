package com.arulsundaresan.arulremindme.nlp

import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What the parser understood from one sentence.
 *
 * Everything is a real `java.time` type — the display strings are produced later by
 * `DateTimeFormatters`, never stored here.
 */
data class ParsedReminderInput(
    /** Exactly what the user typed. Goes into `Reminder.originalInput` untouched. */
    val originalInput: String,
    /** The sentence with the date and time expressions removed. */
    val reminderText: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    /**
     * Set when an hour was found but AM/PM could not be determined ("5 மணிக்கு").
     * The parser refuses to guess; the UI must ask.
     */
    val ambiguousTime: AmbiguousTime? = null,
    val dateConfidence: Confidence = Confidence.NONE,
    val timeConfidence: Confidence = Confidence.NONE,
    /** Session 4: NONE unless the sentence carried a recurrence phrase. */
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatInterval: Int = 1
) {
    val isRecurring: Boolean get() = repeatMode != RepeatMode.NONE

    val needsDate: Boolean get() = date == null
    val needsTime: Boolean get() = time == null && ambiguousTime == null
    val needsTimeClarification: Boolean get() = ambiguousTime != null
    val needsClarification: Boolean get() = needsDate || needsTime || needsTimeClarification
    val isComplete: Boolean get() = date != null && time != null

    val scheduledAt: LocalDateTime?
        get() = if (date != null && time != null) LocalDateTime.of(date, time) else null

    /** Applies the user's AM/PM choice from the clarification dialog. */
    fun withMeridiem(isPm: Boolean): ParsedReminderInput {
        val pending = ambiguousTime ?: return this
        return copy(
            time = pending.resolve(isPm),
            ambiguousTime = null,
            timeConfidence = Confidence.HIGH
        )
    }

    /** Applies a date the user supplied separately (picker or follow-up sentence). */
    fun withDate(value: LocalDate): ParsedReminderInput =
        copy(date = value, dateConfidence = Confidence.HIGH)

    /** Applies a time the user supplied separately. */
    fun withTime(value: LocalTime): ParsedReminderInput =
        copy(time = value, ambiguousTime = null, timeConfidence = Confidence.HIGH)
}

/**
 * An hour with no AM/PM signal. [hour12] is always 1..12 so the UI can offer both readings.
 */
data class AmbiguousTime(val hour12: Int, val minute: Int) {

    fun resolve(isPm: Boolean): LocalTime {
        val base = hour12 % 12
        return LocalTime.of(if (isPm) base + 12 else base, minute)
    }

    fun asAm(): LocalTime = resolve(isPm = false)

    fun asPm(): LocalTime = resolve(isPm = true)
}

/** How sure the parser is about one field. */
enum class Confidence {
    /** Nothing was found. */
    NONE,

    /** Inferred from weak context, e.g. an hour with no AM/PM and no period word. */
    LOW,

    /** Explicit in the input. */
    HIGH
}
