package com.example.reminders

import org.junit.Assert.*
import org.junit.Test

class QuietHoursTest {
    @Test fun quietHoursStartAtMidnightAndEndAtSeven() {
        (0..23).forEach { hour -> assertEquals("Local hour: $hour", hour in 0..6, ReviewReminderPolicy.isQuietHour(hour)) }
    }
}
