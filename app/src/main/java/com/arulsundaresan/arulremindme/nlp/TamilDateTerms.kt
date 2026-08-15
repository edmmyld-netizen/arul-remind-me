package com.arulsundaresan.arulremindme.nlp

import java.time.DayOfWeek
import java.time.Month

/**
 * Tamil vocabulary. Every Tamil word the parser understands lives here — nothing in the UI
 * layer or the ViewModel is allowed to hard-code these.
 */
internal object TamilDateTerms {

    /** term -> number of days from today */
    val relativeDays: Map<String, Long> = mapOf(
        "இன்று" to 0L,
        "இன்றைக்கு" to 0L,
        "இன்னைக்கு" to 0L,
        "நாளை" to 1L,
        "நாளைக்கு" to 1L,
        "நாளைக்கி" to 1L,
        "நாளை மறுநாள்" to 2L,
        "நாளைமறுநாள்" to 2L,
        "நாளன்று" to 2L
    )

    val weekdays: Map<String, DayOfWeek> = mapOf(
        "திங்கள்" to DayOfWeek.MONDAY,
        "திங்கட்கிழமை" to DayOfWeek.MONDAY,
        "திங்கள்கிழமை" to DayOfWeek.MONDAY,
        "செவ்வாய்" to DayOfWeek.TUESDAY,
        "செவ்வாய்க்கிழமை" to DayOfWeek.TUESDAY,
        "புதன்" to DayOfWeek.WEDNESDAY,
        "புதன்கிழமை" to DayOfWeek.WEDNESDAY,
        "வியாழன்" to DayOfWeek.THURSDAY,
        "வியாழக்கிழமை" to DayOfWeek.THURSDAY,
        "வியாழன்கிழமை" to DayOfWeek.THURSDAY,
        "வெள்ளி" to DayOfWeek.FRIDAY,
        "வெள்ளிக்கிழமை" to DayOfWeek.FRIDAY,
        "சனி" to DayOfWeek.SATURDAY,
        "சனிக்கிழமை" to DayOfWeek.SATURDAY,
        "ஞாயிறு" to DayOfWeek.SUNDAY,
        "ஞாயிற்றுக்கிழமை" to DayOfWeek.SUNDAY,
        "ஞாயிறுக்கிழமை" to DayOfWeek.SUNDAY,
        // "ஒவ்வொரு திங்கட்கிழமையும்" — the recurring form carries a trailing "ும்".
        "திங்கட்கிழமையும்" to DayOfWeek.MONDAY,
        "செவ்வாய்க்கிழமையும்" to DayOfWeek.TUESDAY,
        "புதன்கிழமையும்" to DayOfWeek.WEDNESDAY,
        "வியாழக்கிழமையும்" to DayOfWeek.THURSDAY,
        "வெள்ளிக்கிழமையும்" to DayOfWeek.FRIDAY,
        "சனிக்கிழமையும்" to DayOfWeek.SATURDAY,
        "ஞாயிற்றுக்கிழமையும்" to DayOfWeek.SUNDAY
    )

    val qualifiers: Map<String, WeekQualifier> = mapOf(
        "வரும்" to WeekQualifier.UPCOMING,
        "வர்ற" to WeekQualifier.UPCOMING,
        "இந்த" to WeekQualifier.UPCOMING,
        "அடுத்த" to WeekQualifier.NEXT
    )

    /** "அடுத்த வாரம்" — handled before the weekday rule so "வாரம்" is not read as a day. */
    val nextWeekTerms: List<String> = listOf("அடுத்த வாரம்", "வரும் வாரம்")

    val months: Map<String, Month> = mapOf(
        "ஜனவரி" to Month.JANUARY,
        "பிப்ரவரி" to Month.FEBRUARY,
        "பெப்ரவரி" to Month.FEBRUARY,
        "மார்ச்" to Month.MARCH,
        "ஏப்ரல்" to Month.APRIL,
        "மே" to Month.MAY,
        "ஜூன்" to Month.JUNE,
        "ஜூலை" to Month.JULY,
        "ஆகஸ்ட்" to Month.AUGUST,
        "ஆகஸ்டு" to Month.AUGUST,
        "செப்டம்பர்" to Month.SEPTEMBER,
        "அக்டோபர்" to Month.OCTOBER,
        "நவம்பர்" to Month.NOVEMBER,
        "டிசம்பர்" to Month.DECEMBER
    )

    val periods: Map<String, DayPeriod> = mapOf(
        "காலை" to DayPeriod.MORNING,
        "விடியற்காலை" to DayPeriod.MORNING,
        "நண்பகல்" to DayPeriod.NOON,
        "மதியம்" to DayPeriod.AFTERNOON,
        "பிற்பகல்" to DayPeriod.AFTERNOON,
        "மாலை" to DayPeriod.EVENING,
        "சாயங்காலம்" to DayPeriod.EVENING,
        "இரவு" to DayPeriod.NIGHT,
        "ராத்திரி" to DayPeriod.NIGHT
    )

    /** Words that mark an hour: "8 மணிக்கு". */
    val hourMarkers: List<String> = listOf("மணிக்கு", "மணி")

    /** Ordinal/date suffixes that may trail a day number: "15ஆம் தேதி". */
    val dateSuffixes: List<String> = listOf("ஆம் தேதி", "ஆம்தேதி", "ஆம்", "ஆவது", "தேதிக்கு", "தேதி")

    /** Words that carry no meaning once the date/time is extracted. */
    val fillers: List<String> = listOf(
        "அன்று", "அப்போது", "மணிக்கு", "மணி",
        "நினைவூட்டு", "நினைவூட்டவும்", "ஞாபகப்படுத்து", "ஞாபகப்படுத்தவும்"
    )
}
