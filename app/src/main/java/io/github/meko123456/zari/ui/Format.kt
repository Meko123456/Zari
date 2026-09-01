package io.github.meko123456.zari.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Display formatting, kept pure so it can be tested without a device. */
object Format {

    /** `0:07`, `1:23`, `1:02:03` — hours only appear when there are any. */
    fun duration(millis: Long): String {
        val total = (millis / 1_000).coerceAtLeast(0)
        val hours = total / 3_600
        val minutes = (total % 3_600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "$hours:${minutes.pad()}:${seconds.pad()}"
        } else {
            "$minutes:${seconds.pad()}"
        }
    }

    /**
     * Heading for a day's group of recordings. Relative for the two days a person thinks of
     * relatively, absolute after that — "3 days ago" makes you do arithmetic.
     */
    fun dayLabel(millis: Long, today: LocalDate, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"
        }
    }

    /** Groups recordings under day headings, newest first, without a locale-dependent sort. */
    fun dayKey(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun Long.pad(): String = if (this < 10) "0$this" else toString()

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
}
