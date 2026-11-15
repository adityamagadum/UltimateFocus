package com.example.data.model

import com.example.service.ScheduleEvaluator
import java.util.Calendar

/**
 * A rule's schedule as the editor shows it.
 *
 * Rules store their schedule as readable text ("9:00 PM – 7:00 AM • Weekdays"), which
 * [ScheduleEvaluator] parses when deciding whether a rule is active. This type turns that
 * text into editor choices and back, always producing text the evaluator understands.
 */
data class ScheduleSpec(
    val allDay: Boolean = true,
    /** Minutes from midnight. Ignored when [allDay]. */
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    val days: Days = Days.EVERY_DAY
) {
    enum class Days(val label: String) {
        EVERY_DAY("Every day"),
        WEEKDAYS("Weekdays"),
        WEEKENDS("Weekends")
    }

    fun toText(): String {
        val time = if (allDay) "All day" else formatTime(startMinute) + " – " + formatTime(endMinute)
        return time + " • " + days.label
    }

    companion object {
        val ALWAYS = ScheduleSpec()

        fun fromText(text: String): ScheduleSpec {
            val window = ScheduleEvaluator().parse(text)
            val days = when (window.days) {
                setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> Days.WEEKENDS
                setOf(
                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                    Calendar.THURSDAY, Calendar.FRIDAY
                ) -> Days.WEEKDAYS
                else -> Days.EVERY_DAY
            }
            return if (window.isAllDay) {
                ScheduleSpec(allDay = true, days = days)
            } else {
                ScheduleSpec(
                    allDay = false,
                    startMinute = window.startMinuteOfDay ?: 0,
                    endMinute = window.endMinuteOfDay ?: 0,
                    days = days
                )
            }
        }

        /** "9:00 PM" style, which is the format [ScheduleEvaluator] reads. */
        fun formatTime(minuteOfDay: Int): String {
            val hour24 = (minuteOfDay / 60) % 24
            val minute = minuteOfDay % 60
            val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
            val suffix = if (hour24 < 12) "AM" else "PM"
            return hour12.toString() + ":" + minute.toString().padStart(2, '0') + " " + suffix
        }
    }
}
