package com.arulsundaresan.arulremindme.nlp

import org.junit.Assert.assertEquals
import com.arulsundaresan.arulremindme.domain.model.RepeatMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The clock is fixed to Wednesday, 12 August 2026 so the relative rules are deterministic.
 * Nothing in the parser reads the system clock directly — that is what makes these tests
 * meaningful and what keeps "வரும் சனிக்கிழமை" correct in future years.
 */
class ReminderParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val wed12Aug2026: Clock = Clock.fixed(Instant.parse("2026-08-12T03:30:00Z"), zone)
    private val parser = ReminderParser(wed12Aug2026)

    private fun complete(input: String): ParsedReminderInput {
        val result = parser.parse(input)
        assertTrue("expected Complete but was $result", result is ParserResult.Complete)
        return (result as ParserResult.Complete).parsed
    }

    private fun incomplete(input: String): ParserResult.Incomplete {
        val result = parser.parse(input)
        assertTrue("expected Incomplete but was $result", result is ParserResult.Incomplete)
        return result as ParserResult.Incomplete
    }

    private fun anyParsed(input: String): ParsedReminderInput? = when (val r = parser.parse(input)) {
        is ParserResult.Complete -> r.parsed
        is ParserResult.Incomplete -> r.parsed
        is ParserResult.Failure -> null
    }

    // ---- Tamil -------------------------------------------------------------

    @Test
    fun `tamil - naalaikku kaalai 8 manikku EB bill`() {
        val parsed = complete("நாளைக்கு காலை 8 மணிக்கு EB bill கட்டணும்")

        assertEquals("EB bill கட்டணும்", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
        assertEquals("நாளைக்கு காலை 8 மணிக்கு EB bill கட்டணும்", parsed.originalInput)
    }

    @Test
    fun `tamil - varum sanikkizhamai maalai 5 30 amma phone`() {
        val parsed = complete("வரும் சனிக்கிழமை மாலை 5.30 மணிக்கு அம்மாவுக்கு phone பண்ணணும்")

        assertEquals("அம்மாவுக்கு phone பண்ணணும்", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `tamil - adutha thingatkizhamai kaalai 10`() {
        val parsed = complete("அடுத்த திங்கட்கிழமை காலை 10 மணிக்கு Collector meeting")

        assertEquals("Collector meeting", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 17), parsed.date)
        assertEquals(LocalTime.of(10, 0), parsed.time)
    }

    @Test
    fun `tamil - month name with date suffix`() {
        val parsed = complete("ஆகஸ்ட் 15ஆம் தேதி மாலை 5.30 மணிக்கு meeting")

        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `tamil - periods map onto the right half of the clock`() {
        assertEquals(LocalTime.of(21, 0), complete("நாளை இரவு 9 மணி meeting").time)
        assertEquals(LocalTime.of(13, 0), complete("நாளை மதியம் 1 மணி meeting").time)
        assertEquals(LocalTime.of(8, 30), complete("நாளை காலை 8.30 meeting").time)
    }

    // ---- English -----------------------------------------------------------

    @Test
    fun `english - tomorrow at 8 AM pay EB bill`() {
        val parsed = complete("Tomorrow at 8 AM pay EB bill")

        assertEquals("pay EB bill", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
    }

    @Test
    fun `english - August 15 at 5 30 PM meeting`() {
        val parsed = complete("August 15 at 5:30 PM meeting")

        assertEquals("meeting", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `english - weekday qualifiers`() {
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("coming Saturday meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("this Saturday meeting")?.date)
        // "next" always skips past today
        assertEquals(LocalDate.of(2026, 8, 17), anyParsed("next Monday meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 19), anyParsed("next week meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 14), anyParsed("day after tomorrow meeting")?.date)
    }

    @Test
    fun `english - numeric and spelled dates`() {
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("15/08/2026 5:30 PM meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("15-08-2026 5:30 PM meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("15 August 5:30 PM meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("Aug 15 5.30 pm meeting")?.date)
    }

    @Test
    fun `english - 24 hour clock is taken literally`() {
        assertEquals(LocalTime.of(17, 30), complete("tomorrow 17:30 meeting").time)
    }

    @Test
    fun `english - remind me to is stripped from the reminder text`() {
        assertEquals("pay EB bill", complete("Remind me to pay EB bill tomorrow at 8 AM").reminderText)
    }

    // ---- Tanglish ----------------------------------------------------------

    @Test
    fun `tanglish - naalaikku morning 8 manikku`() {
        val parsed = complete("Naalaikku morning 8 manikku EB bill katta num")

        assertEquals("EB bill katta num", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 13), parsed.date)
        assertEquals(LocalTime.of(8, 0), parsed.time)
    }

    @Test
    fun `tanglish - varra saturday 5 30 PM`() {
        val parsed = complete("Varra Saturday 5.30 PM amma ku call panna")

        assertEquals("amma ku call panna", parsed.reminderText)
        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `tanglish - remind pannu is stripped but panna is kept`() {
        val parsed = complete("Naalaiku 5.30 PM amma ku call panna remind pannu")

        assertEquals("amma ku call panna", parsed.reminderText)
        assertEquals(LocalTime.of(17, 30), parsed.time)
    }

    @Test
    fun `tanglish - adutha monday`() {
        assertEquals(
            LocalDate.of(2026, 8, 17),
            complete("Adutha Monday morning 10 manikku Collector meeting").date
        )
    }

    // ---- Ambiguity and missing information ---------------------------------

    @Test
    fun `ambiguous hour asks instead of guessing`() {
        val result = incomplete("Naalaikku 5 manikku EB bill")

        assertEquals(MissingInfo.TIME_MERIDIEM, result.missing)
        assertTrue(result.parsed.needsTimeClarification)
        assertEquals(5, result.parsed.ambiguousTime?.hour12)
        assertEquals(LocalTime.of(5, 0), result.parsed.ambiguousTime?.asAm())
        assertEquals(LocalTime.of(17, 0), result.parsed.ambiguousTime?.asPm())
        // The date is still known, so only AM/PM has to be asked.
        assertEquals(LocalDate.of(2026, 8, 13), result.parsed.date)
    }

    @Test
    fun `clarified meridiem completes the reminder`() {
        val parsed = incomplete("Naalaikku 5 manikku EB bill").parsed.withMeridiem(isPm = true)

        assertEquals(LocalDateTime.of(2026, 8, 13, 17, 0), parsed.scheduledAt)
        assertTrue(parsed.isComplete)
    }

    @Test
    fun `a period word elsewhere in the sentence resolves the hour`() {
        // "மாலை" is nowhere near "5.30" but still settles AM/PM.
        assertEquals(LocalTime.of(17, 30), complete("நாளைக்கு மாலை EB bill 5.30 மணிக்கு").time)
    }

    @Test
    fun `missing time is reported`() {
        val result = incomplete("Naalaikku EB bill katta num")

        assertEquals(MissingInfo.TIME, result.missing)
        assertTrue(result.parsed.needsTime)
        assertEquals("EB bill katta num", result.parsed.reminderText)
    }

    @Test
    fun `missing date is reported`() {
        val result = incomplete("5 PM EB bill katta num")

        assertEquals(MissingInfo.DATE, result.missing)
        assertTrue(result.parsed.needsDate)
        assertEquals(LocalTime.of(17, 0), result.parsed.time)
    }

    @Test
    fun `missing date and time is reported`() {
        assertEquals(MissingInfo.DATE_AND_TIME, incomplete("EB bill கட்டணும்").missing)
    }

    @Test
    fun `empty input fails cleanly`() {
        val result = parser.parse("   ")

        assertTrue(result is ParserResult.Failure)
        assertEquals(ParseFailure.EMPTY_INPUT, (result as ParserResult.Failure).reason)
    }

    @Test
    fun `schedule with no reminder text fails cleanly`() {
        val result = parser.parse("நாளைக்கு காலை 8 மணிக்கு")

        assertTrue(result is ParserResult.Failure)
        assertEquals(ParseFailure.NO_REMINDER_TEXT, (result as ParserResult.Failure).reason)
    }

    @Test
    fun `unparseable input does not crash`() {
        val result = parser.parse("asdfgh qwerty")

        assertTrue(result is ParserResult.Incomplete)
        assertEquals(MissingInfo.DATE_AND_TIME, (result as ParserResult.Incomplete).missing)
    }

    // ---- Year handling -----------------------------------------------------

    @Test
    fun `a month-day already past rolls into next year`() {
        assertEquals(LocalDate.of(2027, 1, 5), anyParsed("January 5 at 9 AM meeting")?.date)
    }

    @Test
    fun `an explicit year is respected`() {
        assertEquals(LocalDate.of(2027, 8, 15), anyParsed("15 August 2027 at 9 AM meeting")?.date)
        assertEquals(LocalDate.of(2026, 8, 15), anyParsed("August 15 2026 at 9 AM meeting")?.date)
    }

    @Test
    fun `relative rules still work in a future year`() {
        // Wednesday, 5 March 2031
        val future = ReminderParser(Clock.fixed(Instant.parse("2031-03-05T04:00:00Z"), zone))
        val result = future.parse("வரும் சனிக்கிழமை மாலை 5.30 மணிக்கு meeting")

        assertTrue(result is ParserResult.Complete)
        assertEquals(LocalDate.of(2031, 3, 8), (result as ParserResult.Complete).parsed.date)
    }

    @Test
    fun `original input is always preserved`() {
        val input = "Naalaikku 5 manikku EB bill"
        assertNotNull(anyParsed(input))
        assertEquals(input, anyParsed(input)?.originalInput)
    }

    // ---- Session 4: recurring phrases --------------------------------------

    @Test
    fun `english recurring phrases set the repeat mode`() {
        assertEquals(RepeatMode.DAILY, complete("Every day at 8 AM pay EB bill").repeatMode)
        assertEquals(RepeatMode.DAILY, complete("Daily at 8 AM pay EB bill").repeatMode)
        assertEquals(
            RepeatMode.WEEKLY,
            complete("Every Monday at 10 AM Collector meeting").repeatMode
        )
        assertEquals(
            RepeatMode.MONTHLY,
            complete("Every month on the 15th at 5:30 PM meeting").repeatMode
        )
    }

    @Test
    fun `tamil recurring phrases set the repeat mode`() {
        assertEquals(
            RepeatMode.DAILY,
            complete("தினமும் காலை 8 மணிக்கு EB bill கட்டணும்").repeatMode
        )
        assertEquals(
            RepeatMode.DAILY,
            complete("ஒவ்வொரு நாளும் காலை 8 மணிக்கு EB bill கட்டணும்").repeatMode
        )
        assertEquals(
            RepeatMode.WEEKLY,
            complete("ஒவ்வொரு திங்கட்கிழமையும் காலை 10 மணிக்கு Collector meeting").repeatMode
        )
        assertEquals(
            RepeatMode.MONTHLY,
            complete("ஒவ்வொரு மாதமும் 15ஆம் தேதி மாலை 5.30 மணிக்கு meeting").repeatMode
        )
    }

    @Test
    fun `tanglish recurring phrases set the repeat mode`() {
        assertEquals(
            RepeatMode.DAILY,
            complete("Daily morning 8 manikku EB bill katta num").repeatMode
        )
        assertEquals(RepeatMode.WEEKLY, complete("Every Monday 10 AM Collector meeting").repeatMode)
        assertEquals(RepeatMode.MONTHLY, complete("Every month 15th 5.30 PM meeting").repeatMode)
    }

    @Test
    fun `the recurrence phrase is stripped from the reminder text`() {
        assertEquals("pay EB bill", complete("Every day at 8 AM pay EB bill").reminderText)
        assertEquals("meeting", complete("Every month on the 15th at 5:30 PM meeting").reminderText)
        assertEquals(
            "Collector meeting",
            complete("ஒவ்வொரு திங்கட்கிழமையும் காலை 10 மணிக்கு Collector meeting").reminderText
        )
    }

    @Test
    fun `every weekday resolves to a real date the date parser produced`() {
        // 12 Aug 2026 is a Wednesday, so "every Monday" anchors on the 17th.
        assertEquals(
            LocalDate.of(2026, 8, 17),
            complete("Every Monday at 10 AM Collector meeting").date
        )
    }

    @Test
    fun `a monthly phrase anchors on the stated day of month`() {
        assertEquals(
            LocalDate.of(2026, 8, 15),
            complete("Every month on the 15th at 5:30 PM meeting").date
        )
        // The 5th has already passed this month, so it anchors on next month's.
        assertEquals(
            LocalDate.of(2026, 9, 5),
            complete("Every month on the 5th at 9 AM meeting").date
        )
    }

    @Test
    fun `sentences without a recurrence phrase stay non-recurring`() {
        listOf(
            "நாளைக்கு காலை 8 மணிக்கு EB bill கட்டணும்",
            "வரும் சனிக்கிழமை மாலை 5.30 மணிக்கு meeting",
            "Tomorrow at 8 AM pay EB bill",
            "August 15 at 5:30 PM meeting"
        ).forEach { input ->
            assertEquals(input, RepeatMode.NONE, complete(input).repeatMode)
        }
    }
}
