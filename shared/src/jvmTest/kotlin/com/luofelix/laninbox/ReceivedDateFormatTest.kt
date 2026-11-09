package com.luofelix.laninbox

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReceivedDateFormatTest {
    @Test
    fun includesTheLocalCalendarDateAndTimeAcrossTimeZoneBoundaries() {
        val instant = Instant.parse("2026-11-09T23:30:00Z").toEpochMilli()
        val utc = formatReceivedDateTime(instant, Locale.US, TimeZone.getTimeZone("UTC"))
        val china = formatReceivedDateTime(instant, Locale.US, TimeZone.getTimeZone("Asia/Shanghai"))
        assertTrue(utc.contains("11/9/26"), utc)
        assertTrue(china.contains("11/10/26"), china)
        assertNotEquals(utc, china)
    }

    @Test
    fun honorsLocaleWithoutLeakingFormatterTimeZoneStateBetweenCalls() {
        val instant = Instant.parse("2026-11-09T23:30:00Z").toEpochMilli()
        val utc = TimeZone.getTimeZone("UTC")
        val first = formatReceivedDateTime(instant, Locale.US, utc)
        val chinese = formatReceivedDateTime(instant, Locale.SIMPLIFIED_CHINESE, TimeZone.getTimeZone("Asia/Shanghai"))
        assertNotEquals(first, chinese)
        assertEquals(first, formatReceivedDateTime(instant, Locale.US, utc))
    }
}
