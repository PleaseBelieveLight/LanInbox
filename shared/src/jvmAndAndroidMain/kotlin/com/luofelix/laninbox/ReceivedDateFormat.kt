package com.luofelix.laninbox

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formats a locally recorded receive time using the current device locale and time zone by default. */
fun formatReceivedDateTime(
    epochMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).apply {
    this.timeZone = timeZone
}.format(Date(epochMillis))
