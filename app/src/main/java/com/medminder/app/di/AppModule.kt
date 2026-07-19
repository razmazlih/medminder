package com.medminder.app.di

import com.medminder.app.core.time.DeviceZoneProvider
import com.medminder.app.core.time.SystemTimeProvider
import com.medminder.app.core.time.TimeProvider
import com.medminder.app.core.time.ZoneProvider
import com.medminder.app.data.repository.MedicationRepositoryImpl
import com.medminder.app.domain.repository.MedicationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindZoneProvider(impl: DeviceZoneProvider): ZoneProvider
}
