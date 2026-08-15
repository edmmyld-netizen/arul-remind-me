package com.arulsundaresan.arulremindme.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting only. Never used for parsing and never persisted.
 */
object DateTimeFormatters {

    /** 15 August 2026 */
    private val LONG_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    /** 15 Aug 2026 */
    private val SHORT_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** 5:30 PM */
    private val TIME_12H: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun longDate(date: LocalDate): String = date.format(LONG_DATE)

    fun shortDate(date: LocalDate): String = date.format(SHORT_DATE)

    fun time(time: LocalTime): String = time.format(TIME_12H)

    fun dateTime(value: LocalDateTime): String =
        shortDate(value.toLocalDate()) + "  •  " + time(value.toLocalTime())
}
