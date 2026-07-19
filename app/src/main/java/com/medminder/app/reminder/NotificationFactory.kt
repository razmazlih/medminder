package com.medminder.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medminder.app.MainActivity
import com.medminder.app.R
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class NotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MedicationRepository
) : NotificationCanceller {

    fun showDoseReminder(doseEventId: String, escalated: Boolean = false): Boolean {
        if (!canPostNotifications()) return false

        val event = runBlocking { repository.getDoseEvent(doseEventId) }
        val title = event?.medicationName ?: context.getString(R.string.reminder_default_title)
        val content = event?.let { context.getString(R.string.reminder_time_prefix, it.localTime.toString()) }
            ?: context.getString(R.string.reminder_default_content)
        val channelId = if (escalated) {
            ReminderConstants.CHANNEL_MEDICATION_ESCALATED
        } else {
            ReminderConstants.CHANNEL_MEDICATION
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(if (escalated) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (escalated) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(if (escalated) longArrayOf(0, 700, 250, 700, 250, 1000) else longArrayOf(0, 250, 250, 250))
            .setAutoCancel(true)
            .setContentIntent(appOpenIntent())
            .addAction(0, context.getString(R.string.taken), actionIntent(doseEventId, ReminderConstants.ACTION_TAKEN))
            .build()

        NotificationManagerCompat.from(context).notify(doseEventId.hashCode(), notification)
        return true
    }

    override fun cancelDoseNotification(doseEventId: String) {
        NotificationManagerCompat.from(context).cancel(doseEventId.hashCode())
    }

    private fun actionIntent(doseEventId: String, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .putExtra(ReminderConstants.EXTRA_DOSE_EVENT_ID, doseEventId)
            .putExtra(ReminderConstants.EXTRA_ACTION, action)
        return PendingIntent.getBroadcast(
            context,
            (doseEventId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun appOpenIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            5000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) return false
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
