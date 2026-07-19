package com.medminder.app.core.time

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TimeProvider {
    fun now(): Instant
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
}
