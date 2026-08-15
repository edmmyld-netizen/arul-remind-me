package com.arulsundaresan.arulremindme.nlp

import java.time.DayOfWeek
import java.time.Month

internal object EnglishDateTerms {

    val relativeDays: Map<String, Long> = mapOf(
        "today" to 0L,
        "tonight" to 0L,
        "tomorrow" to 1L,
        "tomorow" to 1L,
        "tmrw" to 1L,
        "day after tomorrow" to 2L,
        "the day after tomorrow" to 2L,
        "overmorrow" to 2L
    )

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
    )

    val qualifiers: Map<String, WeekQualifier> = mapOf(
        "coming" to WeekQualifier.UPCOMING,
        "this" to WeekQualifier.UPCOMING,
        "upcoming" to WeekQualifier.UPCOMING,
        "next" to WeekQualifier.NEXT
    )

    val nextWeekTerms: List<String> = listOf("next week", "coming week")

    val months: Map<String, Month> = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY,
        "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "march" to Month.MARCH, "mar" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL,
        "may" to Month.MAY,
        "june" to Month.JUNE, "jun" to Month.JUNE,
        "july" to Month.JULY, "jul" to Month.JULY,
        "august" to Month.AUGUST, "aug" to Month.AUGUST,
        "september" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER,
        "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER
    )

    val periods: Map<String, DayPeriod> = mapOf(
        "morning" to DayPeriod.MORNING,
        "noon" to DayPeriod.NOON,
        "midday" to DayPeriod.NOON,
        "afternoon" to DayPeriod.AFTERNOON,
        "evening" to DayPeriod.EVENING,
        "night" to DayPeriod.NIGHT,
        "tonight" to DayPeriod.NIGHT,
        "midnight" to DayPeriod.MIDNIGHT
    )

    val hourMarkers: List<String> = listOf("o'clock", "oclock", "hrs", "hours")

    val dateSuffixes: List<String> = listOf("st", "nd", "rd", "th")

    val fillers: List<String> = listOf("at", "on", "by", "in the", "sharp", "please", "pls")

    /** Removed as a phrase, e.g. "remind me to pay EB bill". */
    val reminderVerbPhrases: List<String> = listOf(
        "remind me to", "remind me about", "remind me", "reminder for", "reminder to", "remind"
    )
}
