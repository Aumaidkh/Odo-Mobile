package com.hopcape.odo.core.domain.shared

/**
 * The body class a car is priced as.
 *
 * The axis every reference price is keyed by, because a Swift, an i20 and a Baleno are the
 * same car for pricing purposes. Keying prices by model instead would need a table nobody
 * would ever finish, and would still leave the tail empty.
 *
 * The constants mirror the `vehicle_segment` labels the reference tables use.
 */
enum class VehicleSegment { HATCHBACK, SEDAN, SUV, MUV }
