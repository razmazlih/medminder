package com.medminder.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.usecase.ConfirmDoseActionUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var confirmDoseActionUseCase: ConfirmDoseActionUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(ReminderConstants.EXTRA_DOSE_EVENT_ID) ?: return
        val action = intent.getStringExtra(ReminderConstants.EXTRA_ACTION) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val intakeAction = action.toIntakeAction() ?: return@launch
                confirmDoseActionUseCase(doseId, intakeAction, ActionSource.NOTIFICATION)
            } finally {
                pending.finish()
            }
        }
    }

    private fun String.toIntakeAction(): IntakeAction? {
        return when (this) {
            ReminderConstants.ACTION_TAKEN -> IntakeAction.TAKEN
            else -> null
        }
    }
}
