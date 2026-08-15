package com.arulsundaresan.arulremindme.nlp

import com.arulsundaresan.arulremindme.nlp.Patterns.LB
import com.arulsundaresan.arulremindme.nlp.Patterns.RB
import com.arulsundaresan.arulremindme.nlp.Patterns.alternation
import com.arulsundaresan.arulremindme.nlp.Patterns.regex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** How a weekday name should be resolved relative to today. */
internal enum class WeekQualifier {
    /** "coming Saturday" / "வரும் சனிக்கிழமை" — today counts if today is that weekday. */
    UPCOMING,

    /** "next Saturday" / "அடுத்த சனிக்கிழமை" — strictly after today. */
    NEXT
}

internal data class DateMatch(
    val date: LocalDate,
    val range: IntRange,
    val confidence: Confidence
)

/**
 * Turns a date expression into a real [LocalDate], relative to the date passed in.
 *
 * Nothing here reads the system clock — the caller supplies `today`, which is what makes the
 * relative rules ("வரும் சனிக்கிழமை") testable and keeps them correct in any future year.
 */
internal object DateExpressionParser {

    private val relativeDays: Map<String, Long> =
        TamilDateTerms.relativeDays + EnglishDateTerms.relativeDays + TanglishDateTerms.relativeDays

    private val weekdays: Map<String, DayOfWeek> =
        TamilDateTerms.weekdays + EnglishDateTerms.weekdays + TanglishDateTerms.weekdays

    private val qualifiers: Map<String, WeekQualifier> =
        TamilDateTerms.qualifiers + EnglishDateTerms.qualifiers + TanglishDateTerms.qualifiers

    private val months: Map<String, Month> =
        TamilDateTerms.months + EnglishDateTerms.months

    private val nextWeekTerms: List<String> =
        TamilDateTerms.nextWeekTerms + EnglishDateTerms.nextWeekTerms + TanglishDateTerms.nextWeekTerms

    private val dateSuffixes: List<String> =
        TamilDateTerms.dateSuffixes + EnglishDateTerms.dateSuffixes

    // ---- compiled patterns -------------------------------------------------

    private val numericDate = regex("$LB(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})$RB")

    private val monthThenDay = regex(
        "$LB(${alternation(months.keys)})\\s*(\\d{1,2})" +
            "(?:\\s*(?:${alternation(dateSuffixes)}))?" +
            "(?:\\s*,?\\s*(\\d{4}))?$RB"
    )

    private val dayThenMonth = regex(
        "$LB(\\d{1,2})(?:\\s*(?:${alternation(dateSuffixes)}))?" +
            "\\s*(${alternation(months.keys)})" +
            "(?:\\s*,?\\s*(\\d{4}))?$RB"
    )

    private val nextWeek = regex("$LB(?:${alternation(nextWeekTerms)})$RB")

    private val relativeDay = regex("$LB(${alternation(relativeDays.keys)})$RB")

    private val qualifiedWeekday = regex(
        "$LB(?:(${alternation(qualifiers.keys)})\\s+)?(${alternation(weekdays.keys)})$RB"
    )

    /**
     * @param text lowercased input, same length as the original (see [lowercaseKeepingLength]).
     * @param today the device's current local date.
     */
    fun parse(text: String, today: LocalDate): DateMatch? {
        parseNumeric(text)?.let { return it }
        parseMonthAndDay(text, today)?.let { return it }
        parseNextWeek(text, today)?.let { return it }
        parseRelativeDay(text, today)?.let { return it }
        parseWeekday(text, today)?.let { return it }
        return null
    }

    private fun parseNumeric(text: String): DateMatch? {
        val m = numericDate.find(text) ?: return null
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val rawYear = m.groupValues[3].toInt()
        val year = if (rawYear < 100) 2000 + rawYear else rawYear
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        return DateMatch(date, m.range, Confidence.HIGH)
    }

    private fun parseMonthAndDay(text: String, today: LocalDate): DateMatch? {
        val match = listOfNotNull(monthThenDay.find(text), dayThenMonth.find(text))
            .minByOrNull { it.range.first } ?: return null

        val isMonthFirst = match.groupValues[1].toIntOrNull() == null
        val monthName = if (isMonthFirst) match.groupValues[1] else match.groupValues[2]
        val dayText = if (isMonthFirst) match.groupValues[2] else match.groupValues[1]
        val month = months[monthName] ?: return null
        val day = dayText.toIntOrNull() ?: return null
        val explicitYear = match.groupValues[3].toIntOrNull()

        val date = resolveMonthDay(month, day, explicitYear, today) ?: return null
        return DateMatch(date, match.range, Confidence.HIGH)
    }

    /**
     * "August 15" with no year: this year if it is still ahead, otherwise next year — a
     * reminder is never something the user wanted in the past.
     */
    private fun resolveMonthDay(
        month: Month,
        day: Int,
        explicitYear: Int?,
        today: LocalDate
    ): LocalDate? {
        if (explicitYear != null) {
            return runCatching { LocalDate.of(explicitYear, month, day) }.getOrNull()
        }
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isBefore(today)) return thisYear
        return runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: thisYear
    }

    private fun parseNextWeek(text: String, today: LocalDate): DateMatch? {
        val m = nextWeek.find(text) ?: return null
        return DateMatch(today.plusWeeks(1), m.range, Confidence.HIGH)
    }

    private fun parseRelativeDay(text: String, today: LocalDate): DateMatch? {
        val m = relativeDay.find(text) ?: return null
        val offset = relativeDays[m.groupValues[1]] ?: return null
        return DateMatch(today.plusDays(offset), m.range, Confidence.HIGH)
    }

    private fun parseWeekday(text: String, today: LocalDate): DateMatch? {
        val m = qualifiedWeekday.find(text) ?: return null
        val target = weekdays[m.groupValues[2]] ?: return null
        val qualifier = qualifiers[m.groupValues[1]]
        val date = resolveWeekday(today, target, qualifier)
        // A bare weekday ("Saturday meeting") is a weaker signal than "coming Saturday".
        val confidence = if (qualifier == null) Confidence.LOW else Confidence.HIGH
        return DateMatch(date, m.range, confidence)
    }

    /**
     * UPCOMING (and a bare weekday) resolve to the next occurrence including today;
     * NEXT always skips past today.
     */
    fun resolveWeekday(today: LocalDate, target: DayOfWeek, qualifier: WeekQualifier?): LocalDate =
        when (qualifier) {
            WeekQualifier.NEXT -> today.with(TemporalAdjusters.next(target))
            else -> today.with(TemporalAdjusters.nextOrSame(target))
        }
}
