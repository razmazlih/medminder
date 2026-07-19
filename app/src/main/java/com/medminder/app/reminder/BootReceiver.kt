package com.medminder.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medminder.app.core.Defaults
import com.medminder.app.domain.usecase.GenerateDailyDoseEventsUseCase
import com.medminder.app.domain.usecase.MarkMissedDosesUseCase
import com.medminder.app.domain.usecase.SchedulePendingDosesUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var generateDailyDoseEventsUseCase: GenerateDailyDoseEventsUseCase

    @Inject
    lateinit var schedulePendingDosesUseCase: SchedulePendingDosesUseCase

    @Inject
    lateinit var markMissedDosesUseCase: MarkMissedDosesUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val owner = Defaults.OWNER_PROFILE_ID
                    generateDailyDoseEventsUseCase(owner, LocalDate.now(), days = 3)
                    markMissedDosesUseCase()
                    schedulePendingDosesUseCase(owner)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
