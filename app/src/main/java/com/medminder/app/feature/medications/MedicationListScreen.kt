package com.medminder.app.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.ScheduleMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val medicationTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun MedicationListScreen(
    state: MedicationUiState,
    onAddClicked: () -> Unit,
    onEditClicked: (Medication) -> Unit,
    onDisableClicked: (String) -> Unit,
    onRestoreClicked: (String) -> Unit,
    onDeleteConfirmed: (String) -> Unit,
    onShowInactiveChanged: (Boolean) -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val addMedicationLabel = stringResource(R.string.medications_add)
    var medicationPendingDelete by remember { mutableStateOf<Medication?>(null) }
    var medicationPendingDisable by remember { mutableStateOf<Medication?>(null) }

    val allRegularMedications = remember(state.allMedications) {
        state.allMedications.filter { it.times.isNotEmpty() }
    }
    val activeRegularCount = allRegularMedications.count { it.isActive }
    val regularCount = allRegularMedications.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MedicationScreenDefaults.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(MedicationScreenDefaults.SectionSpacing)
    ) {
        MedicationHeaderCard(
            title = stringResource(R.string.tab_medications),
            subtitle = stringResource(
                R.string.medications_active_summary,
                activeRegularCount,
                regularCount
            ),
            showInactiveMedications = state.showInactiveMedications,
            showInactiveLabel = stringResource(R.string.medications_show_inactive),
            onShowInactiveChanged = onShowInactiveChanged,
            primaryActionLabel = addMedicationLabel,
            onPrimaryAction = onAddClicked
        )

        state.error?.let {
            MedicationStatusCard(
                message = it,
                isError = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.exactAlarmWarning) {
            MedicationStatusCard(
                message = stringResource(R.string.medications_exact_alarm_warning),
                isError = true,
                actionLabel = stringResource(R.string.medications_exact_alarm_action),
                onAction = onOpenExactAlarmSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.medications.isEmpty()) {
            EmptyMedicationsState(
                onAddClicked = onAddClicked,
                addMedicationLabel = addMedicationLabel
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MedicationScreenDefaults.ItemSpacing),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(state.medications, key = { it.id }) { medication ->
                    MedicationCard(
                        medication = medication,
                        onEditClicked = onEditClicked,
                        onDisableClicked = { medicationPendingDisable = medication },
                        onRestoreClicked = onRestoreClicked,
                        onDeleteClicked = { medicationPendingDelete = medication }
                    )
                }
            }
        }
    }

    medicationPendingDelete?.let { medication ->
        AlertDialog(
            onDismissRequest = { medicationPendingDelete = null },
            title = { Text(text = stringResource(R.string.medications_delete_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.medications_delete_dialog_message,
                        medication.name
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConfirmed(medication.id)
                        medicationPendingDelete = null
                    }
                ) {
                    Text(text = stringResource(R.string.medications_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDelete = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    medicationPendingDisable?.let { medication ->
        AlertDialog(
            onDismissRequest = { medicationPendingDisable = null },
            title = { Text(text = stringResource(R.string.medications_disable_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.medications_disable_dialog_message,
                        medication.name
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDisableClicked(medication.id)
                        medicationPendingDisable = null
                    }
                ) {
                    Text(text = stringResource(R.string.medications_disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDisable = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyMedicationsState(
    onAddClicked: () -> Unit,
    addMedicationLabel: String
) {
    MedicationEmptyStateCard(
        title = stringResource(R.string.medications_empty_active),
        description = stringResource(R.string.medications_empty_hint),
        actionLabel = addMedicationLabel,
        onAction = onAddClicked,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MedicationCard(
    medication: Medication,
    onEditClicked: (Medication) -> Unit,
    onDisableClicked: (String) -> Unit,
    onRestoreClicked: (String) -> Unit,
    onDeleteClicked: (String) -> Unit
) {
    val scheduleSummary = when (medication.scheduleMode) {
        ScheduleMode.FIXED_TIMES -> {
            val times = medication.times
                .sorted()
                .joinToString(" • ") { it.format(medicationTimeFormatter) }
                .ifBlank { "-" }
            "${stringResource(R.string.schedule_fixed)}: $times"
        }

        ScheduleMode.EVERY_X_HOURS -> {
            val interval = medication.intervalHours ?: 8
            val start = formatTimeOrDash(medication.windowStartTime)
            val end = formatTimeOrDash(medication.windowEndTime)
            stringResource(R.string.schedule_every_x_summary, interval, start, end)
        }

        ScheduleMode.EVERY_X_DAYS -> {
            val interval = medication.intervalHours ?: 2
            val times = medication.times
                .sorted()
                .joinToString(" • ") { it.format(medicationTimeFormatter) }
                .ifBlank { "-" }
            stringResource(R.string.schedule_every_x_days_summary, interval, times)
        }
    }

    val scheduleModeLabel = when (medication.scheduleMode) {
        ScheduleMode.FIXED_TIMES -> stringResource(R.string.schedule_fixed)
        ScheduleMode.EVERY_X_HOURS -> stringResource(R.string.schedule_every_x)
        ScheduleMode.EVERY_X_DAYS -> stringResource(R.string.schedule_every_x_days)
    }

    val actions = if (medication.isActive) {
        listOf(
            MedicationActionItem(
                label = stringResource(R.string.medications_edit),
                style = MedicationActionStyle.Secondary,
                onClick = { onEditClicked(medication) }
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_disable),
                style = MedicationActionStyle.Secondary,
                onClick = { onDisableClicked(medication.id) }
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_delete),
                style = MedicationActionStyle.Danger,
                onClick = { onDeleteClicked(medication.id) }
            )
        )
    } else {
        listOf(
            MedicationActionItem(
                label = stringResource(R.string.medications_edit),
                style = MedicationActionStyle.Secondary,
                onClick = { onEditClicked(medication) }
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_restore),
                style = MedicationActionStyle.Primary,
                onClick = { onRestoreClicked(medication.id) }
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_delete),
                style = MedicationActionStyle.Danger,
                onClick = { onDeleteClicked(medication.id) }
            )
        )
    }

    MedicationCardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${medication.doseText} ${medication.doseUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!medication.isActive) {
                MedicationBadge(
                    text = stringResource(R.string.medications_status_inactive),
                    isWarning = true
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MedicationMetaPill(text = formatWeekdays(medication.weekdays))
            MedicationMetaPill(text = scheduleModeLabel)
            if (medication.notes.isNotBlank()) {
                MedicationMetaPill(text = stringResource(R.string.editor_notes))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MedicationScreenDefaults.CardShape
        ) {
            Text(
                text = scheduleSummary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        MedicationAdaptiveActions(actions = actions)
    }
}

private fun formatTimeOrDash(time: LocalTime?): String {
    return time?.format(medicationTimeFormatter) ?: "-"
}

private fun formatWeekdays(weekdays: List<Int>): String {
    val labels = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")
    val selected = weekdays
        .distinct()
        .sorted()
        .mapNotNull { day -> labels.getOrNull(day - 1) }
    return if (selected.isEmpty()) "כל יום" else selected.joinToString(" ")
}
