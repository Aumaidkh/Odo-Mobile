package com.hopcape.odo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform