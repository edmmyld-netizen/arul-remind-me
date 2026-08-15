package com.arulsundaresan.arulremindme.nlp

import com.arulsundaresan.arulremindme.nlp.Patterns.LB
import com.arulsundaresan.arulremindme.nlp.Patterns.RB
import com.arulsundaresan.arulremindme.nlp.Patterns.alternation
import com.arulsundaresan.arulremindme.nlp.Patterns.regex
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Turns one free-form Tamil / English / Tanglish sentence into a [ParsedReminderInput].
 *
 * Entirely offline and deterministic: regex rules plus `java.time`. No network call, no
 * model, nothing that can fail because the phone has no signal.
 *
 * The clock is injected so the relative rules can be tested against a fixed date; in the app
 * it is the device clock, so "வரும் சனிக்கிழமை" keeps working in any future year.
 */
class ReminderParser(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val fillerPhrases: List<String> =
        EnglishDateTerms.reminderVerbPhrases +
            TanglishDateTerms.reminderVerbPhrases +
            TamilDateTerms.fillers +
            EnglishDateTerms.fillers

    private val fillerRegex = regex("$LB(?:${alternation(fillerPhrases)})$RB")

    private val leftoverMarkers = regex(
        "$LB(?:${alternation(
            TamilDateTerms.hourMarkers + TanglishDateTerms.hourMarkers +
                EnglishDateTerms.hourMarkers + TamilDateTerms.dateSuffixes
        )})$RB"
    )

    private val edgePunctuation = Regex("^[\\s,.;:\\-–—!?]+|[\\s,.;:\\-–—]+$")
    private val whitespace = Regex("\\s+")

    fun today(): LocalDate = LocalDate.now(clock)

    fun parse(rawInput: String): ParserResult {
        val original = rawInput.trim()
        if (original.isBlank()) {
            return ParserResult.Failure(rawInput, ParseFailure.EMPTY_INPUT)
        }

        val lower = original.lowercaseKeepingLength()
        val today = today()

        // Session 4: recurrence runs first and masks only its own marker words, so the
        // Session 2 date rules still see "Monday" in "every Monday".
        val recurrence = RecurrenceExpressionParser.parse(lower)
        val withoutMarkers = lower
            .maskRange(recurrence?.range)
            .maskRange(recurrence?.dayOfMonthRange)

        val dateMatch = DateExpressionParser.parse(withoutMarkers, today)
        // Mask the date span before looking for a time, otherwise the "15" in "August 15"
        // reads as an hour.
        val withoutDate = withoutMarkers.maskRange(dateMatch?.range)
        val timeMatch = TimeExpressionParser.parse(withoutDate, periodContext = lower)

        val reminderText = extractReminderText(
            original = original,
            recurrenceRange = recurrence?.range,
            recurrenceDayRange = recurrence?.dayOfMonthRange,
            dateRange = dateMatch?.range,
            timeRange = timeMatch?.range
        )

        // A recurrence phrase implies its own anchor date when none was written out:
        // "every day at 8 AM" starts today, "every month on the 15th" starts on the next
        // 15th. RecurrenceRules rolls it forward if that instant has already passed.
        val resolvedDate = dateMatch?.date ?: recurrence?.let { anchorDateFor(it, today) }

        val parsed = ParsedReminderInput(
            originalInput = original,
            reminderText = reminderText,
            date = resolvedDate,
            time = timeMatch?.time,
            ambiguousTime = timeMatch?.ambiguous,
            dateConfidence = when {
                dateMatch != null -> dateMatch.confidence
                resolvedDate != null -> Confidence.LOW
                else -> Confidence.NONE
            },
            timeConfidence = timeMatch?.confidence ?: Confidence.NONE,
            repeatMode = recurrence?.mode ?: RepeatMode.NONE,
            repeatInterval = recurrence?.interval ?: 1
        )

        if (reminderText.isBlank()) {
            return ParserResult.Failure(original, ParseFailure.NO_REMINDER_TEXT)
        }

        return when {
            parsed.needsTimeClarification ->
                ParserResult.Incomplete(parsed, MissingInfo.TIME_MERIDIEM)

            parsed.needsDate && parsed.needsTime ->
                ParserResult.Incomplete(parsed, MissingInfo.DATE_AND_TIME)

            parsed.needsDate -> ParserResult.Incomplete(parsed, MissingInfo.DATE)
            parsed.needsTime -> ParserResult.Incomplete(parsed, MissingInfo.TIME)
            else -> ParserResult.Complete(parsed)
        }
    }

    /**
     * Cuts the date and time spans out of the *original* input (so Tamil script and the
     * user's capitalisation survive), then clears the connective words they leave behind.
     */
    /**
     * The anchor date a recurrence starts from when the sentence gave no explicit date.
     * MONTHLY uses the stated day of the month; DAILY and WEEKLY start today.
     */
    private fun anchorDateFor(recurrence: RecurrenceMatch, today: LocalDate): LocalDate {
        val day = recurrence.dayOfMonth
        if (recurrence.mode != RepeatMode.MONTHLY || day == null) return today
        val thisMonth = YearMonth.from(today)
        val candidate = LocalDate.of(
            thisMonth.year,
            thisMonth.month,
            day.coerceAtMost(thisMonth.lengthOfMonth())
        )
        if (!candidate.isBefore(today)) return candidate
        val next = thisMonth.plusMonths(1)
        return LocalDate.of(next.year, next.month, day.coerceAtMost(next.lengthOfMonth()))
    }

    private fun extractReminderText(
        original: String,
        recurrenceRange: IntRange?,
        recurrenceDayRange: IntRange?,
        dateRange: IntRange?,
        timeRange: IntRange?
    ): String {
        var text = original
            .maskRange(recurrenceRange)
            .maskRange(recurrenceDayRange)
            .maskRange(dateRange)
            .maskRange(timeRange)
        text = fillerRegex.replace(text, " ")
        text = leftoverMarkers.replace(text, " ")
        text = whitespace.replace(text, " ")
        return edgePunctuation.replace(text, "").trim()
    }
}
