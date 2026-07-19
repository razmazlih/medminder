package com.medminder.app.feature.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorScreen(
    existing: Medication?,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: (MedicationFormState) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var doseText by remember(existing?.id) { mutableStateOf(existing?.doseText ?: "1") }
    var doseUnit by remember(existing?.id) { mutableStateOf(existing?.doseUnit ?: "כדור") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var startDate by remember(existing?.id) { mutableStateOf(existing?.startDate ?: LocalDate.now()) }
    var endDate by remember(existing?.id) { mutableStateOf(existing?.endDate) }
    val foodRelation = existing?.foodRelation ?: com.medminder.app.domain.model.FoodRelation.NONE
    var weekdays by remember(existing?.id) { mutableStateOf(existing?.weekdays ?: (1..7).toList()) }

    var scheduleMode by remember(existing?.id) {
        mutableStateOf(existing?.scheduleMode ?: ScheduleMode.FIXED_TIMES)
    }
    var fixedTimes by remember(existing?.id) {
        mutableStateOf(existing?.times?.ifEmpty { listOf(LocalTime.of(8, 0)) } ?: listOf(LocalTime.of(8, 0)))
    }
    var intervalHoursInput by remember(existing?.id) {
        mutableStateOf((existing?.intervalHours ?: 8).toString())
    }
    var intervalDaysInput by remember(existing?.id) {
        mutableStateOf((if (existing?.scheduleMode == ScheduleMode.EVERY_X_DAYS) existing.intervalHours else 2).toString())
    }
    var windowStartTime by remember(existing?.id) {
        mutableStateOf(existing?.windowStartTime ?: LocalTime.of(8, 0))
    }
    var windowEndTime by remember(existing?.id) {
        mutableStateOf(existing?.windowEndTime ?: LocalTime.of(22, 0))
    }

    var showAddTimeDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showWindowStartDialog by remember { mutableStateOf(false) }
    var showWindowEndDialog by remember { mutableStateOf(false) }
    val isEveryXDays = scheduleMode == ScheduleMode.EVERY_X_DAYS

    val intervalHours = intervalHoursInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val intervalPreview = remember(scheduleMode, intervalHours, windowStartTime, windowEndTime) {
        if (scheduleMode == ScheduleMode.EVERY_X_HOURS && !windowEndTime.isBefore(windowStartTime)) {
            buildList {
                var current = windowStartTime
                while (!current.isAfter(windowEndTime)) {
                    add(current)
                    val next = current.plusHours(intervalHours.toLong())
                    if (!next.isAfter(current)) break
                    current = next
                }
            }
        } else {
            emptyList()
        }
    }

    val saveForm: () -> Unit = {
        onSave(
            MedicationFormState(
                id = existing?.id,
                name = name,
                doseText = doseText,
                doseUnit = doseUnit,
                foodRelation = foodRelation,
                scheduleMode = scheduleMode,
                fixedTimes = fixedTimes,
                intervalHours = intervalHours,
                intervalDays = intervalDaysInput.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                windowStartTime = windowStartTime,
                windowEndTime = windowEndTime,
                startDate = startDate,
                endDate = endDate,
                notes = notes,
                weekdays = weekdays,
                isActive = existing?.isActive ?: true
            )
        )
    }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                EditorActionButtons(
                    onBack = onBack,
                    onSave = saveForm,
                    modifier = Modifier.padding(
                        horizontal = MedicationScreenDefaults.ScreenPadding,
                        vertical = 12.dp
                    )
                )
            }
                }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = MedicationScreenDefaults.ScreenPadding,
                end = MedicationScreenDefaults.ScreenPadding,
                top = MedicationScreenDefaults.ScreenPadding,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(MedicationScreenDefaults.SectionSpacing)
        ) {
            item {
                MedicationCardSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (existing == null) {
                            stringResource(R.string.editor_new_title)
                        } else {
                            stringResource(R.string.editor_edit_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.editor_form_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                MedicationSectionCard(
                    title = stringResource(R.string.editor_section_details),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.editor_medication_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = doseText,
                            onValueChange = { doseText = it },
                            label = { Text(stringResource(R.string.editor_dose)) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = doseUnit,
                            onValueChange = { doseUnit = it },
                            label = { Text(stringResource(R.string.editor_unit)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                MedicationSectionCard(
                    title = stringResource(R.string.editor_section_schedule),
                    subtitle = if (isEveryXDays) {
                        stringResource(R.string.editor_every_x_days_schedule_help)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScheduleMode.entries.forEach { mode ->
                            FilterChip(
                                selected = scheduleMode == mode,
                                onClick = { scheduleMode = mode },
                                label = {
                                    Text(
                                        when (mode) {
                                            ScheduleMode.FIXED_TIMES -> stringResource(R.string.schedule_fixed)
                                            ScheduleMode.EVERY_X_HOURS -> stringResource(R.string.schedule_every_x)
                                            ScheduleMode.EVERY_X_DAYS -> stringResource(R.string.schedule_every_x_days)
                                        }
                                    )
                                }
                            )
                        }
                    }

                    if (scheduleMode == ScheduleMode.FIXED_TIMES || isEveryXDays) {
                        if (isEveryXDays) {
                            OutlinedTextField(
                                value = intervalDaysInput,
                                onValueChange = { value ->
                                    intervalDaysInput = value.filter { it.isDigit() }.take(2)
                                },
                                label = { Text(stringResource(R.string.editor_interval_days)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { showStartDateDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(
                                        R.string.editor_every_x_days_anchor_date,
                                        startDate.format(dateFormatter)
                                    )
                                )
                            }
                        }

                        Text(
                            text = stringResource(
                                if (isEveryXDays) {
                                    R.string.editor_every_x_days_times_title
                                } else {
                                    R.string.editor_fixed_times
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fixedTimes.sorted().forEach { time ->
                                FilterChip(
                                    selected = true,
                                    onClick = { fixedTimes = fixedTimes - time },
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.editor_remove_time,
                                                time.format(timeFormatter)
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { showAddTimeDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (isEveryXDays) {
                                        R.string.editor_every_x_days_add_time
                                    } else {
                                        R.string.editor_add_time
                                    }
                                )
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = intervalHoursInput,
                            onValueChange = { value ->
                                intervalHoursInput = value.filter { it.isDigit() }.take(2)
                            },
                            label = { Text(stringResource(R.string.editor_interval_hours)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showWindowStartDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.editor_start_time,
                                        windowStartTime.format(timeFormatter)
                                    )
                                )
                            }
                            Button(
                                onClick = { showWindowEndDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.editor_end_time,
                                        windowEndTime.format(timeFormatter)
                                    )
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MedicationScreenDefaults.CardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.editor_generated_times,
                                    intervalPreview
                                        .joinToString(", ") { it.format(timeFormatter) }
                                        .ifBlank { "-" }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            item {
                MedicationSectionCard(
                    title = stringResource(R.string.editor_section_weekdays),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (scheduleMode == ScheduleMode.EVERY_X_DAYS) {
                        MedicationMetaPill(text = stringResource(R.string.editor_every_x_days_hint))
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val labels = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = weekdays.contains(day),
                                    onClick = {
                                        weekdays = if (weekdays.contains(day)) {
                                            weekdays - day
                                        } else {
                                            weekdays + day
                                        }
                                    },
                                    label = { Text(labels[day - 1]) }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { weekdays = (1..7).toList() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.editor_select_all_weekdays))
                        }
                    }
                }
            }

            item {
                MedicationSectionCard(
                    title = stringResource(R.string.editor_section_notes),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        label = { Text(stringResource(R.string.editor_notes)) }
                    )
                }
            }

            if (errorMessage != null) {
                item {
                    MedicationStatusCard(
                        message = errorMessage,
                        isError = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showAddTimeDialog) {
        TimePickerDialog(
            title = stringResource(R.string.editor_pick_time),
            initialTime = LocalTime.of(8, 0),
            onDismiss = { showAddTimeDialog = false },
            onConfirm = { selected ->
                fixedTimes = (fixedTimes + selected).distinct().sorted()
                showAddTimeDialog = false
            }
        )
    }

    if (showStartDateDialog) {
        MedicationDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartDateDialog = false },
            onDateSelected = {
                startDate = it
                showStartDateDialog = false
            }
        )
    }

    if (showWindowStartDialog) {
        TimePickerDialog(
            title = stringResource(R.string.editor_pick_start_time),
            initialTime = windowStartTime,
            onDismiss = { showWindowStartDialog = false },
            onConfirm = {
                windowStartTime = it
                showWindowStartDialog = false
            }
        )
    }

    if (showWindowEndDialog) {
        TimePickerDialog(
            title = stringResource(R.string.editor_pick_end_time),
            initialTime = windowEndTime,
            onDismiss = { showWindowEndDialog = false },
            onConfirm = {
                windowEndTime = it
                showWindowEndDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedDateMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    onDateSelected(selectedDateMillis.toLocalDate())
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun EditorActionButtons(
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_cancel))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    )
}

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
