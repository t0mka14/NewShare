package org.example.app.domain

import java.time.Instant

interface Clock {
    fun now(): Instant
}

class RealClock : Clock {
    override fun now(): Instant = Instant.now()
}
