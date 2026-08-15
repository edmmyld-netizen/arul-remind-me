package com.arulsundaresan.arulremindme.nlp

import com.arulsundaresan.arulremindme.nlp.Patterns.LB
import com.arulsundaresan.arulremindme.nlp.Patterns.RB
import com.arulsundaresan.arulremindme.nlp.Patterns.alternation
import com.arulsundaresan.arulremindme.nlp.Patterns.regex
import java.time.LocalTime

/** Part of the day named in the sentence: காலை / மாலை / morning / evening ... */
internal enum class DayPeriod {
    MORNING, NOON, AFTERNOON, EVENING, NIGHT, MIDNIGHT;

    /** Maps a 12-hour clock reading onto the 24-hour clock for this part of the day. */
    fun toHour24(hour12: Int): Int = when (this) {
        MORNING -> if (hour12 == 12) 0 else hour12
        MIDNIGHT -> if (hour12 == 12) 0 else hour12
        NOON -> if (hour12 == 12) 12 else hour12 + 12
        AFTERNOON, EVENING -> if (hour12 == 12) 12 else hour12 + 12
        NIGHT -> if (hour12 == 12) 0 else hour12 + 12
    }
}

internal data class TimeMatch(
    /** Null when [ambiguous] is set. */
    val time: LocalTime?,
    val ambiguous: AmbiguousTime?,
    val range: IntRange,
    val confidence: Confidence
)

/**
 * Extracts a time of day.
 *
 * The important rule: if an hour is found with no AM/PM and no part-of-day word anywhere in
 * the sentence, this returns an [AmbiguousTime] instead of guessing. "5 மணிக்கு" could
 * equally be 5 AM or 5 PM, and silently picking one is how reminders get missed.
 */
internal object TimeExpressionParser {

    private val periods: Map<String, DayPeriod> =
        TamilDateTerms.periods + EnglishDateTerms.periods + TanglishDateTerms.periods

    private val hourMarkers: List<String> =
        TamilDateTerms.hourMarkers + EnglishDateTerms.hourMarkers + TanglishDateTerms.hourMarkers

    private val periodAlt = alternation(periods.keys)
    private val markerAlt = alternation(hourMarkers)

    /** "5:30 PM", "8 am", "5.30 p.m." */
    private val explicitMeridiem = regex(
        "$LB(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)$RB"
    )

    /** "காலை 8 மணிக்கு", "morning 8", "மாலை 5.30" */
    private val periodThenHour = regex(
        "$LB($periodAlt)\\s*(?:at\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:$markerAlt))?$RB"
    )

    /** "8 மணி காலை", "5.30 evening" */
    private val hourThenPeriod = regex(
        "$LB(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:$markerAlt))?\\s*($periodAlt)$RB"
    )

    /** "17:30" — a colon reading that may or may not already be 24-hour. */
    private val colonTime = regex("$LB(\\d{1,2}):(\\d{2})$RB")

    /** "8 மணிக்கு", "8 manikku", "8 o'clock" */
    private val hourWithMarker = regex(
        "$LB(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:$markerAlt)$RB"
    )

    /** "at 8", "at 8.30" */
    private val atHour = regex("${LB}at\\s+(\\d{1,2})(?:[:.](\\d{2}))?$RB")

    private val anyPeriod = regex("$LB($periodAlt)$RB")

    /**
     * @param text lowercased input with the date expression already masked out, so that the
     *   "15" of "August 15" can never be mistaken for an hour.
     * @param periodContext the full (unmasked) lowercased sentence, used to find a
     *   part-of-day word that sits away from the number — or inside the date expression,
     *   as in "tonight".
     */
    fun parse(text: String, periodContext: String = text): TimeMatch? {
        explicitMeridiem.find(text)?.let { m ->
            val hour12 = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val isPm = m.groupValues[3].startsWith("p")
            val time = build24(hour12, minute, isPm) ?: return@let
            return TimeMatch(time, null, m.range, Confidence.HIGH)
        }

        periodThenHour.find(text)?.let { m ->
            val period = periods[m.groupValues[1]] ?: return@let
            val hour12 = m.groupValues[2].toIntOrNull() ?: return@let
            val minute = m.groupValues[3].toIntOrNull() ?: 0
            val time = fromPeriod(period, hour12, minute) ?: return@let
            return TimeMatch(time, null, m.range, Confidence.HIGH)
        }

        hourThenPeriod.find(text)?.let { m ->
            val period = periods[m.groupValues[3]] ?: return@let
            val hour12 = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val time = fromPeriod(period, hour12, minute) ?: return@let
            return TimeMatch(time, null, m.range, Confidence.HIGH)
        }

        colonTime.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: return@let
            if (minute > 59) return@let
            // 17:30 or 00:30 can only be 24-hour, so there is nothing to disambiguate.
            if (hour == 0 || hour > 12) {
                val time = runCatching { LocalTime.of(hour, minute) }.getOrNull() ?: return@let
                return TimeMatch(time, null, m.range, Confidence.HIGH)
            }
            return resolveOrAsk(hour, minute, m.range, periodContext)
        }

        hourWithMarker.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (hour > 23 || minute > 59) return@let
            if (hour > 12) {
                val time = runCatching { LocalTime.of(hour, minute) }.getOrNull() ?: return@let
                return TimeMatch(time, null, m.range, Confidence.HIGH)
            }
            return resolveOrAsk(hour, minute, m.range, periodContext)
        }

        atHour.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (hour > 23 || minute > 59) return@let
            if (hour > 12) {
                val time = runCatching { LocalTime.of(hour, minute) }.getOrNull() ?: return@let
                return TimeMatch(time, null, m.range, Confidence.HIGH)
            }
            return resolveOrAsk(hour, minute, m.range, periodContext)
        }

        return null
    }

    /**
     * Last chance before asking the user: a part-of-day word somewhere else in the sentence
     * ("மாலை ... 5.30 மணிக்கு") still settles AM/PM.
     */
    private fun resolveOrAsk(
        hour: Int,
        minute: Int,
        range: IntRange,
        periodContext: String
    ): TimeMatch {
        val period = anyPeriod.find(periodContext)?.let { periods[it.groupValues[1]] }
        if (period != null) {
            val time = fromPeriod(period, hour, minute)
            if (time != null) return TimeMatch(time, null, range, Confidence.HIGH)
        }
        val hour12 = if (hour == 0) 12 else hour
        return TimeMatch(null, AmbiguousTime(hour12, minute), range, Confidence.LOW)
    }

    private fun fromPeriod(period: DayPeriod, hour12: Int, minute: Int): LocalTime? {
        if (hour12 > 23 || minute > 59) return null
        // "இரவு 19 மணி" — already a 24-hour reading, leave it alone.
        val hour24 = if (hour12 > 12) hour12 else period.toHour24(hour12)
        return runCatching { LocalTime.of(hour24, minute) }.getOrNull()
    }

    private fun build24(hour12: Int, minute: Int, isPm: Boolean): LocalTime? {
        if (hour12 > 12 || minute > 59) return null
        val base = hour12 % 12
        return runCatching { LocalTime.of(if (isPm) base + 12 else base, minute) }.getOrNull()
    }
}
