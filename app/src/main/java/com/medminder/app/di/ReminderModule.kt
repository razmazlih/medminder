package com.medminder.app.di

import android.app.AlarmManager
import android.content.Context
import com.medminder.app.reminder.NotificationCanceller
import com.medminder.app.reminder.NotificationFactory
import com.medminder.app.reminder.ReminderScheduler
import com.medminder.app.reminder.ReminderSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: ReminderSchedulerImpl): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindNotificationCanceller(impl: NotificationFactory): NotificationCanceller

    companion object {
        @Provides
        @Singleton
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
            return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }
    }
}
