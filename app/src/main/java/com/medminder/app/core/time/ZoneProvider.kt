package com.medminder.app.core.time

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface ZoneProvider {
    fun currentZone(): ZoneId
}

@Singleton
class DeviceZoneProvider @Inject constructor() : ZoneProvider {
    override fun currentZone(): ZoneId = ZoneId.systemDefault()
}
