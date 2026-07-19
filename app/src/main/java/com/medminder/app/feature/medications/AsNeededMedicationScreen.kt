package com.medminder.app.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.domain.model.Medication

@Composable
fun AsNeededMedicationScreen(
    state: MedicationUiState,
    onAddMedication: (String) -> Unit,
    onRenameMedication: (String, String) -> Unit,
    onDeleteMedication: (String) -> Unit,
    onDisableMedication: (String) -> Unit,
    onRestoreMedication: (String) -> Unit,
    onShowInactiveChanged: (Boolean) -> Unit
) {
    val medications = state.asNeededMedications

    var name by remember { mutableStateOf("") }
    var medicationPendingRename by remember { mutableStateOf<Medication?>(null) }
    var renameText by remember { mutableStateOf("") }
    var medicationPendingDelete by remember { mutableStateOf<Medication?>(null) }
    var medicationPendingDisable by remember { mutableStateOf<Medication?>(null) }
    var isAddFormExpanded by remember(medications.isEmpty()) { mutableStateOf(medications.isEmpty()) }

    val allAsNeeded = remember(state.allMedications) {
        state.allMedications.filter { it.times.isEmpty() }
    }

    LaunchedEffect(medications.isEmpty()) {
        if (medications.isEmpty()) {
            isAddFormExpanded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MedicationScreenDefaults.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(MedicationScreenDefaults.SectionSpacing)
    ) {
        MedicationHeaderCard(
            title = stringResource(R.string.tab_as_needed_medications),
            subtitle = stringResource(
                R.string.medications_active_summary,
                allAsNeeded.count { it.isActive },
                allAsNeeded.size
            ),
            showInactiveMedications = state.showInactiveMedications,
            showInactiveLabel = stringResource(R.string.medications_show_inactive),
            onShowInactiveChanged = onShowInactiveChanged
        )

        AsNeededAddMedicationCard(
            name = name,
            isExpanded = isAddFormExpanded,
            onNameChanged = { name = it },
            onToggleExpanded = { isAddFormExpanded = !isAddFormExpanded },
            onSubmit = {
                onAddMedication(name)
                name = ""
                isAddFormExpanded = false
            }
        )

        state.error?.let {
            MedicationStatusCard(
                message = it,
                isError = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (medications.isEmpty()) {
            MedicationEmptyStateCard(
                title = stringResource(R.string.as_needed_empty),
                description = stringResource(R.string.medications_empty_hint),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MedicationScreenDefaults.ItemSpacing),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(medications, key = { it.id }) { medication ->
                    AsNeededMedicationCard(
                        medication = medication,
                        onRenameClicked = {
                            medicationPendingRename = medication
                            renameText = medication.name
                        },
                        onDeleteClicked = { medicationPendingDelete = medication },
                        onDisableClicked = { medicationPendingDisable = medication },
                        onRestoreClicked = { onRestoreMedication(medication.id) }
                    )
                }
            }
        }
    }

    medicationPendingRename?.let { medication ->
        AlertDialog(
            onDismissRequest = {
                medicationPendingRename = null
                renameText = ""
            },
            title = { Text(text = stringResource(R.string.medications_edit)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.editor_medication_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameMedication(medication.id, renameText)
                        medicationPendingRename = null
                        renameText = ""
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    medicationPendingRename = null
                    renameText = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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
                        onDeleteMedication(medication.id)
                        medicationPendingDelete = null
                    }
                ) { Text(stringResource(R.string.medications_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
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
                        onDisableMedication(medication.id)
                        medicationPendingDisable = null
                    }
                ) { Text(stringResource(R.string.medications_disable)) }
            },
            dismissButton = {
                TextButton(onClick = { medicationPendingDisable = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun AsNeededAddMedicationCard(
    name: String,
    isExpanded: Boolean,
    onNameChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onSubmit: () -> Unit
) {
    MedicationCardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.as_needed_add),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleExpanded) {
                Text(
                    if (isExpanded) {
                        stringResource(R.string.common_cancel)
                    } else {
                        stringResource(R.string.common_add)
                    }
                )
            }
        }

        if (isExpanded) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useStackedLayout = maxWidth < 420.dp
                if (useStackedLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = onNameChanged,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.editor_medication_name)) },
                            singleLine = true
                        )
                        Button(
                            onClick = onSubmit,
                            enabled = name.trim().isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.common_add))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = onNameChanged,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.editor_medication_name)) },
                            singleLine = true
                        )
                        Button(
                            onClick = onSubmit,
                            enabled = name.trim().isNotEmpty()
                        ) {
                            Text(stringResource(R.string.common_add))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AsNeededMedicationCard(
    medication: Medication,
    onRenameClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onDisableClicked: () -> Unit,
    onRestoreClicked: () -> Unit
) {
    val actions = if (medication.isActive) {
        listOf(
            MedicationActionItem(
                label = stringResource(R.string.medications_edit),
                onClick = onRenameClicked,
                style = MedicationActionStyle.Secondary
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_disable),
                onClick = onDisableClicked,
                style = MedicationActionStyle.Secondary
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_delete),
                onClick = onDeleteClicked,
                style = MedicationActionStyle.Danger
            )
        )
    } else {
        listOf(
            MedicationActionItem(
                label = stringResource(R.string.medications_edit),
                onClick = onRenameClicked,
                style = MedicationActionStyle.Secondary
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_restore),
                onClick = onRestoreClicked,
                style = MedicationActionStyle.Primary
            ),
            MedicationActionItem(
                label = stringResource(R.string.medications_delete),
                onClick = onDeleteClicked,
                style = MedicationActionStyle.Danger
            )
        )
    }

    MedicationCardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

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
            MedicationMetaPill(text = stringResource(R.string.tab_as_needed_medications))
            MedicationMetaPill(text = stringResource(R.string.as_needed_mark_taken_now))
        }

        MedicationAdaptiveActions(actions = actions)
    }
}
