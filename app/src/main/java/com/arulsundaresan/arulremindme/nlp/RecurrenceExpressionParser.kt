package com.arulsundaresan.arulremindme.nlp

import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import com.arulsundaresan.arulremindme.nlp.Patterns.LB
import com.arulsundaresan.arulremindme.nlp.Patterns.RB
import com.arulsundaresan.arulremindme.nlp.Patterns.alternation
import com.arulsundaresan.arulremindme.nlp.Patterns.regex

internal data class RecurrenceMatch(
    val mode: RepeatMode,
    val interval: Int,
    val range: IntRange,
    /** Only for MONTHLY: "every month on the 15th" -> 15. */
    val dayOfMonth: Int? = null,
    /** Span of that day-of-month phrase, so it is cut from the reminder text too. */
    val dayOfMonthRange: IntRange? = null
)

/**
 * Session 4 addition to the Session 2 parser.
 *
 * It runs *before* the date parser and masks only its own marker words ("every", "daily",
 * "தினமும்", "ஒவ்வொரு … ும்"). The weekday and day number are deliberately left in place so
 * the existing [DateExpressionParser] still resolves "Every Monday" to a real Monday — the
 * Session 2 rules are extended, not replaced.
 *
 * When no marker is present this returns null and nothing downstream changes, which is why
 * the existing parser tests are unaffected.
 */
internal object RecurrenceExpressionParser {

    private val dailyTerms = listOf(
        "every day", "everyday", "each day", "daily",
        "தினமும்", "ஒவ்வொரு நாளும்", "தினசரி",
        "dinamum", "thinamum", "daily-ah"
    )

    private val weeklyTerms = listOf(
        "every week", "each week", "weekly",
        "வாரந்தோறும்", "ஒவ்வொரு வாரமும்",
        "every week-um", "weekly-ah"
    )

    private val monthlyTerms = listOf(
        "every month", "each month", "monthly",
        "மாதந்தோறும்", "ஒவ்வொரு மாதமும்",
        "monthly-ah"
    )

    /**
     * "every Monday" / "ஒவ்வொரு திங்கட்கிழமையும்" — weekly, but the weekday itself is left
     * for the date parser to resolve.
     */
    private val weekdayMarkers = listOf("every", "each", "ஒவ்வொரு", "every-um")

    private val weekdayAlt = alternation(
        TamilDateTerms.weekdays.keys + EnglishDateTerms.weekdays.keys +
            TanglishDateTerms.weekdays.keys
    )

    private val daily = regex("$LB(?:${alternation(dailyTerms)})$RB")
    private val weekly = regex("$LB(?:${alternation(weeklyTerms)})$RB")
    private val monthly = regex("$LB(?:${alternation(monthlyTerms)})$RB")

    /** Marker directly in front of a weekday, e.g. "every Monday". */
    private val everyWeekday = regex(
        "$LB(?:${alternation(weekdayMarkers)})\\s+(?=$weekdayAlt$RB)"
    )

    /** Tamil suffix form: "ஒவ்வொரு திங்கட்கிழமையும்". */
    private val tamilEveryWeekday = regex(
        "${LB}ஒவ்வொரு\\s+(?=[\\p{L}]*(?:கிழமை|திங்கள்|செவ்வாய்|புதன்|வியாழன்|வெள்ளி|சனி|ஞாயிறு))"
    )

    /** "on the 15th" / "15ஆம் தேதி" following a monthly marker. */
    private val dayOfMonth = regex(
        "$LB(?:on\\s+)?(?:the\\s+)?(\\d{1,2})\\s*(?:st|nd|rd|th|ஆம்|ஆவது)" +
            "(?:\\s*(?:தேதி|தேதிக்கு))?$RB"
    )

    fun parse(text: String): RecurrenceMatch? {
        monthly.find(text)?.let { m ->
            val dayMatch = dayOfMonth.find(text)
            val day = dayMatch?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }
            return RecurrenceMatch(
                mode = RepeatMode.MONTHLY,
                interval = 1,
                range = m.range,
                dayOfMonth = day,
                dayOfMonthRange = if (day != null) dayMatch.range else null
            )
        }
        daily.find(text)?.let { return RecurrenceMatch(RepeatMode.DAILY, 1, it.range) }
        weekly.find(text)?.let { return RecurrenceMatch(RepeatMode.WEEKLY, 1, it.range) }
        everyWeekday.find(text)?.let { return RecurrenceMatch(RepeatMode.WEEKLY, 1, it.range) }
        tamilEveryWeekday.find(text)?.let {
            return RecurrenceMatch(RepeatMode.WEEKLY, 1, it.range)
        }
        return null
    }
}
