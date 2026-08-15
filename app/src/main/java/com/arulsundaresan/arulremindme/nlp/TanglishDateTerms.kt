package com.arulsundaresan.arulremindme.nlp

import java.time.DayOfWeek

/**
 * Romanised Tamil. There is no standard spelling for Tanglish, so each concept lists the
 * spellings people actually type. Add variants here rather than loosening the regexes.
 */
internal object TanglishDateTerms {

    val relativeDays: Map<String, Long> = mapOf(
        "innaikku" to 0L, "innaiku" to 0L, "inniku" to 0L, "innikku" to 0L, "indha naal" to 0L,
        "naalaikku" to 1L, "naalaiku" to 1L, "nalaikku" to 1L, "nalaiku" to 1L,
        "naalike" to 1L, "naalai" to 1L, "nalai" to 1L, "naaliku" to 1L,
        "naalai marunaal" to 2L, "naalaikku marunaal" to 2L, "marunaal" to 2L
    )

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "thingal" to DayOfWeek.MONDAY, "thingatkizhamai" to DayOfWeek.MONDAY,
        "sevvai" to DayOfWeek.TUESDAY, "sevvaai" to DayOfWeek.TUESDAY,
        "budhan" to DayOfWeek.WEDNESDAY, "puthan" to DayOfWeek.WEDNESDAY,
        "viyazhan" to DayOfWeek.THURSDAY, "viyaazhan" to DayOfWeek.THURSDAY,
        "velli" to DayOfWeek.FRIDAY, "velly" to DayOfWeek.FRIDAY,
        "sani" to DayOfWeek.SATURDAY, "sanikizhamai" to DayOfWeek.SATURDAY,
        "gnayiru" to DayOfWeek.SUNDAY, "nyayiru" to DayOfWeek.SUNDAY
    )

    val qualifiers: Map<String, WeekQualifier> = mapOf(
        "varra" to WeekQualifier.UPCOMING,
        "vara" to WeekQualifier.UPCOMING,
        "varum" to WeekQualifier.UPCOMING,
        "varuthu" to WeekQualifier.UPCOMING,
        "indha" to WeekQualifier.UPCOMING,
        "inda" to WeekQualifier.UPCOMING,
        "adutha" to WeekQualifier.NEXT,
        "aduttha" to WeekQualifier.NEXT
    )

    val nextWeekTerms: List<String> = listOf("adutha varam", "adutha vaaram", "varra varam")

    val periods: Map<String, DayPeriod> = mapOf(
        "kaalai" to DayPeriod.MORNING,
        "kalai" to DayPeriod.MORNING,
        "madhiyam" to DayPeriod.AFTERNOON,
        "mathiyam" to DayPeriod.AFTERNOON,
        "maalai" to DayPeriod.EVENING,
        "malai" to DayPeriod.EVENING,
        "saayangaalam" to DayPeriod.EVENING,
        "iravu" to DayPeriod.NIGHT,
        "raathiri" to DayPeriod.NIGHT,
        "rathiri" to DayPeriod.NIGHT
    )

    val hourMarkers: List<String> = listOf("manikku", "manikki", "maniku", "mani")

    /**
     * Only phrases built around "remind" are stripped. Things like "panna" or "ku" on their
     * own are part of what the user wants to be reminded about ("amma ku call panna").
     */
    val reminderVerbPhrases: List<String> = listOf(
        "remind pannu", "remind panna", "remind pannunga", "remind pannungo",
        "ninaivupaduthu", "gnabagapaduthu"
    )
}
