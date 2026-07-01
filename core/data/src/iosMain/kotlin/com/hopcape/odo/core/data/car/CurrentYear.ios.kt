package com.hopcape.odo.core.data.car

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

/** iOS: the year component of "now" from the current calendar. */
internal actual fun currentYear(): Int =
    NSCalendar.currentCalendar.component(NSCalendarUnitYear, fromDate = NSDate()).toInt()
