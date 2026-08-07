package com.hopcape.odo.infrastructure.database.car

import java.time.Year

/** Android: `java.time.Year` (available from minSdk 26). */
internal actual fun currentYear(): Int = Year.now().value
