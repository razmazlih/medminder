package com.medminder.app.feature.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.core.accessibility.AccessibilityDefaults
import com.medminder.app.domain.model.DoseEvent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onReportDownload: (HistoryReportRequest) -> Unit,
    onReportShare: (HistoryReportRequest) -> Unit,
    onMedicationFilterChanged: (String?) -> Unit,
    onMedicationSearchQueryChanged: (String) -> Unit,
    onDateRangeFilterChanged: (LocalDate, LocalDate) -> Unit,
    onUpdateEventTime: (String, LocalTime) -> Unit,
    onDeleteEvent: (String) -> Unit
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var activeDatePicker by remember { mutableStateOf<DateRangePickerField?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    
    // Group items by date and 5-minute windows, sorted descending
    val groupedItems = remember(state.items) {
        state.items
            .groupBy { effectiveEventDate(it) }
            .toSortedMap(compareByDescending { it })
            .mapValues { (_, dateEvents) ->
                groupEventsByFiveMinutes(dateEvents)
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HistoryTopBar(
            onReportsClick = { showReportDialog = true }
        )

        Text(
            text = if (showFilters) {
                stringResource(R.string.history_filters_hide)
            } else {
                stringResource(R.string.history_filters_show)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { showFilters = !showFilters }
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            HistoryFilters(
                state = state,
                onFromDateClick = { activeDatePicker = DateRangePickerField.FROM },
                onToDateClick = { activeDatePicker = DateRangePickerField.TO },
                onMedicationFilterChanged = onMedicationFilterChanged,
                onMedicationSearchQueryChanged = onMedicationSearchQueryChanged
            )
        }

        if (state.items.isEmpty()) {
            EmptyHistoryState(
                startDate = state.selectedFromDate,
                endDate = state.selectedToDate
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                groupedItems.forEach { (date, timeGroups) ->
                    stickyHeader {
                        DateHeader(date = date)
                    }

                    items(
                        items = timeGroups,
                        key = { group ->
                            "${date}_${group.startTime}_${group.endTime}_${group.events.firstOrNull()?.id ?: "empty"}_${group.events.size}"
                        }
                    ) { group ->
                        HistoryEventCard(
                            events = group.events,
                            startTime = group.startTime,
                            endTime = group.endTime,
                            asNeededOrOneTimeMedicationIds = state.asNeededOrOneTimeMedicationIds,
                            takenTodayCountByMedicationId = state.takenTodayCountByMedicationId,
                            cloudDeletionNoticeByMedicationId = state.cloudDeletionNoticeByMedicationId,
                            onUpdateEventTime = onUpdateEventTime,
                            onDeleteEvent = onDeleteEvent,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showReportDialog) {
        ReportDialog(
            state = state,
            onDismiss = { showReportDialog = false },
            onDownload = { request ->
                onReportDownload(request)
                showReportDialog = false
            },
            onShare = { request ->
                onReportShare(request)
                showReportDialog = false
            }
        )
    }

    activeDatePicker?.let { field ->
        HistoryDatePickerDialog(
            initialDate = if (field == DateRangePickerField.FROM) state.selectedFromDate else state.selectedToDate,
            onDismiss = { activeDatePicker = null },
            onDateSelected = { selected ->
                val from = if (field == DateRangePickerField.FROM) selected else state.selectedFromDate
                val to = if (field == DateRangePickerField.TO) selected else state.selectedToDate
                // Ensure from <= to, or swap if creating a valid range logic is preferred,
                // but here we just pass as is or let the ViewModel handle normalization.
                // The ViewModel logic normalizes it.
                onDateRangeFilterChanged(from, to)
                
                // If we just selected FROM, maybe we want to select TO next? 
                // For simplicity, we just close. The user can click again if needed
                // or we could implement a range picker logic match original.
                activeDatePicker = null
            }
        )
    }
}

private data class HistoryTimeGroup(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val events: List<DoseEvent>
)

private fun groupEventsByFiveMinutes(events: List<DoseEvent>): List<HistoryTimeGroup> {
    if (events.isEmpty()) return emptyList()

    val sorted = events.sortedByDescending { effectiveEventTime(it).toSecondOfDay() }
    val groups = mutableListOf<HistoryTimeGroup>()
    var currentEvents = mutableListOf(sorted.first())
    var currentStart = effectiveEventTime(sorted.first())

    for (index in 1 until sorted.size) {
        val event = sorted[index]
        val eventTime = effectiveEventTime(event)
        val minuteDiff = (currentStart.toSecondOfDay() - eventTime.toSecondOfDay()) / 60
        if (minuteDiff <= 5) {
            currentEvents.add(event)
        } else {
            groups.add(
                HistoryTimeGroup(
                    startTime = currentStart,
                    endTime = effectiveEventTime(currentEvents.last()),
                    events = currentEvents.toList()
                )
            )
            currentEvents = mutableListOf(event)
            currentStart = eventTime
        }
    }

    groups.add(
        HistoryTimeGroup(
            startTime = currentStart,
            endTime = effectiveEventTime(currentEvents.last()),
            events = currentEvents.toList()
        )
    )
    return groups
}

private fun effectiveEventTime(event: DoseEvent): LocalTime {
    val time = event.respondedAt
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalTime()
        ?: event.localTime
    return LocalTime.of(time.hour, time.minute)
}

private fun effectiveEventDateTime(event: DoseEvent): LocalDateTime {
    return event.respondedAt
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDateTime()
        ?: LocalDateTime.of(event.localDate, event.localTime)
}

private fun effectiveEventDate(event: DoseEvent): LocalDate {
    return effectiveEventDateTime(event).toLocalDate()
}

private fun formatTimeAgo(event: DoseEvent, now: LocalDateTime = LocalDateTime.now()): String {
    val eventTime = effectiveEventDateTime(event)
    val diffMinutes = Duration.between(eventTime, now).toMinutes()
    val absMinutes = kotlin.math.abs(diffMinutes)

    if (absMinutes < 1) return "ממש עכשיו"

    if (absMinutes in 60..1_439) {
        val hours = absMinutes / 60
        val minutes = absMinutes % 60
        val hhmm = "%02d:%02d".format(hours, minutes)
        return if (diffMinutes >= 0) "לפני $hhmm שעות" else "בעוד $hhmm שעות"
    }

    val (value, unit) = when {
        absMinutes < 60 -> absMinutes to if (absMinutes == 1L) "דקה" else "דקות"
        else -> {
            val days = absMinutes / 1_440
            days to if (days == 1L) "יום" else "ימים"
        }
    }

    if (diffMinutes >= 0 && absMinutes in 1_440..2_879) {
        return "לפני יותר מ-24 שעות"
    }

    return if (diffMinutes >= 0) "לפני $value $unit" else "בעוד $value $unit"
}

private fun canDeleteEvent(event: DoseEvent, now: LocalDateTime = LocalDateTime.now()): Boolean {
    val lastEditedAt = event.updatedAt
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDateTime()
        ?: effectiveEventDateTime(event)
    return Duration.between(lastEditedAt, now) <= Duration.ofHours(2)
}

@Composable
private fun HistoryTopBar(
    onReportsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Button(
            onClick = onReportsClick,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.history_reports),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HistoryFilters(
    state: HistoryUiState,
    onFromDateClick: () -> Unit,
    onToDateClick: () -> Unit,
    onMedicationFilterChanged: (String?) -> Unit,
    onMedicationSearchQueryChanged: (String) -> Unit
) {
    var showMedicationPicker by remember { mutableStateOf(false) }
    val selectedMedicationName = state.medicationFilters
        .firstOrNull { it.id == state.selectedMedicationId }
        ?.name
        ?: stringResource(R.string.history_filter_all_medications)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = true,
                onClick = onFromDateClick,
                label = {
                    Text(
                        text = stringResource(
                            R.string.history_filter_from_date,
                            state.selectedFromDate.format(shortDateFormatter)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = true,
                onClick = onToDateClick,
                label = {
                    Text(
                        text = stringResource(
                            R.string.history_filter_to_date,
                            state.selectedToDate.format(shortDateFormatter)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
        }

        FilterChip(
            selected = state.selectedMedicationId != null,
            onClick = { showMedicationPicker = true },
            label = {
                Text(
                    text = "${stringResource(R.string.history_filter_medication)}: $selectedMedicationName",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.medicationSearchQuery,
            onValueChange = onMedicationSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.history_search_medication_label)) },
            placeholder = { Text(stringResource(R.string.history_search_medication_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (state.medicationSearchQuery.isNotBlank()) {
                    IconButton(onClick = { onMedicationSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.history_search_clear)
                        )
                    }
                }
            }
        )

        if (state.medicationSearchQuery.isNotBlank()) {
            Text(
                text = stringResource(
                    R.string.history_search_results_count,
                    state.matchedMedicationCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    }

    if (showMedicationPicker) {
        AlertDialog(
            onDismissRequest = { showMedicationPicker = false },
            title = { Text(stringResource(R.string.history_filter_medication)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedMedicationId == null,
                            onClick = {
                                onMedicationFilterChanged(null)
                                showMedicationPicker = false
                            },
                            label = { Text(stringResource(R.string.history_filter_all_medications)) }
                        )
                    }
                    items(state.medicationFilters, key = { it.id }) { medication ->
                        FilterChip(
                            selected = state.selectedMedicationId == medication.id,
                            onClick = {
                                onMedicationFilterChanged(medication.id)
                                showMedicationPicker = false
                            },
                            label = { Text(medication.name) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMedicationPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val dateText = when (date) {
        LocalDate.now() -> stringResource(R.string.tab_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.tab_yesterday)
        else -> date.format(headerDateFormatter)
    }
    
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEventCard(
    events: List<DoseEvent>,
    startTime: LocalTime,
    endTime: LocalTime,
    asNeededOrOneTimeMedicationIds: Set<String>,
    takenTodayCountByMedicationId: Map<String, Int>,
    cloudDeletionNoticeByMedicationId: Map<String, CloudDeletionNoticeUi>,
    onUpdateEventTime: (String, LocalTime) -> Unit,
    onDeleteEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) return

    var eventToEdit by remember(events) { mutableStateOf<DoseEvent?>(null) }
    var eventToDelete by remember(events) { mutableStateOf<DoseEvent?>(null) }

    val startTimeText = startTime.format(historyTimeFormatter)
    val endTimeText = endTime.format(historyTimeFormatter)
    val displayTime = if (startTime == endTime) startTimeText else "$startTimeText - $endTimeText"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (events.size == 1) events.first().medicationName else "${events.size} תרופות",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = displayTime,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "\u23F3 ${formatTimeAgo(events.first())}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (events.size == 1) {
                    val event = events.first()
                    val canDelete = canDeleteEvent(event)
                    val isAsNeededOrOneTime = event.medicationId in asNeededOrOneTimeMedicationIds
                    val medicationTypeColor = if (isAsNeededOrOneTime) Color(0xFF1565C0) else Color(0xFF2E7D32)
                    val medicationTypeLabel = if (isAsNeededOrOneTime) {
                        stringResource(R.string.history_type_as_needed_or_one_time)
                    } else {
                        stringResource(R.string.history_type_fixed)
                    }
                    val takenTodayCount = takenTodayCountByMedicationId[event.medicationId] ?: 0
                    val cloudDeletionNotice = cloudDeletionNoticeByMedicationId[event.medicationId]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = medicationTypeColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = medicationTypeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = medicationTypeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_taken_today_count, takenTodayCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            cloudDeletionNotice?.let { notice ->
                                CloudDeletedBadge(notice = notice)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = { eventToEdit = event },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.history_edit_time),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (canDelete) {
                            FilledTonalIconButton(
                                onClick = { eventToDelete = event },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.history_delete_event),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        events.forEachIndexed { index, event ->
                            val canDelete = canDeleteEvent(event)
                            val isAsNeededOrOneTime = event.medicationId in asNeededOrOneTimeMedicationIds
                            val medicationTypeColor = if (isAsNeededOrOneTime) Color(0xFF1565C0) else Color(0xFF2E7D32)
                            val medicationTypeLabel = if (isAsNeededOrOneTime) {
                                stringResource(R.string.history_type_as_needed_or_one_time)
                            } else {
                                stringResource(R.string.history_type_fixed)
                            }
                            val takenTodayCount = takenTodayCountByMedicationId[event.medicationId] ?: 0
                            val cloudDeletionNotice = cloudDeletionNoticeByMedicationId[event.medicationId]
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = event.medicationName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = medicationTypeColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = medicationTypeLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = medicationTypeColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.history_taken_today_count, takenTodayCount),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            cloudDeletionNotice?.let { notice ->
                                                CloudDeletedBadge(notice = notice)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalIconButton(
                                        onClick = { eventToEdit = event },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.history_edit_time),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (canDelete) {
                                        FilledTonalIconButton(
                                            onClick = { eventToDelete = event },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.history_delete_event),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < events.lastIndex) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    eventToEdit?.let { event ->
        val initialTime = event.respondedAt
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalTime()
            ?: event.localTime
        TimeEditorDialog(
            title = stringResource(R.string.history_edit_time),
            initialTime = initialTime,
            onDismiss = { eventToEdit = null },
            onConfirm = { selected ->
                onUpdateEventTime(event.id, selected)
                eventToEdit = null
            }
        )
    }

    eventToDelete?.let { event ->
        val deleteDisplayTime = (event.respondedAt
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalTime()
            ?: event.localTime).format(historyTimeFormatter)
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text(stringResource(R.string.history_delete_dialog_title)) },
            text = { Text(stringResource(R.string.history_delete_dialog_message, event.medicationName, deleteDisplayTime)) },
            dismissButton = {
                TextButton(onClick = { eventToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEvent(event.id)
                        eventToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.medications_delete))
                }
            }
        )
    }
}

@Composable
private fun CloudDeletedBadge(notice: CloudDeletionNoticeUi) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    ) {
        Text(
            text = "ענן ${notice.deletedAtLabel}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyHistoryState(startDate: LocalDate, endDate: LocalDate) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "אין היסטוריה להצגה", // "No history to display"
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "נסה לשנות את טווח התאריכים או את הסינון", // "Try changing the date range or filter"
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReportDialog(
    state: HistoryUiState,
    onDismiss: () -> Unit,
    onDownload: (HistoryReportRequest) -> Unit,
    onShare: (HistoryReportRequest) -> Unit
) {
    val defaultReportToDate = remember { LocalDate.now() }
    val defaultReportFromDate = remember(defaultReportToDate) { defaultReportToDate.minusMonths(1) }
    var selectedMedicationId by remember(state.selectedMedicationId) { mutableStateOf(state.selectedMedicationId) }
    var selectedFromDate by remember { mutableStateOf(defaultReportFromDate) }
    var selectedToDate by remember { mutableStateOf(defaultReportToDate) }
    var activeDatePicker by remember { mutableStateOf<DateRangePickerField?>(null) }
    val normalizedFrom = minOf(selectedFromDate, selectedToDate)
    val normalizedTo = maxOf(selectedFromDate, selectedToDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Date Range Selection inside Dialog
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.history_filter_date_range), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { activeDatePicker = DateRangePickerField.FROM },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(selectedFromDate.format(shortDateFormatter))
                        }
                        OutlinedButton(
                            onClick = { activeDatePicker = DateRangePickerField.TO },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(selectedToDate.format(shortDateFormatter))
                        }
                    }
                }

                // Medication Selection inside Dialog
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.history_filter_medication), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedMedicationId == null,
                                onClick = { selectedMedicationId = null },
                                label = { Text(stringResource(R.string.history_filter_all_medications)) }
                            )
                        }
                        items(state.medicationFilters, key = { it.id }) { medication ->
                            FilterChip(
                                selected = selectedMedicationId == medication.id,
                                onClick = { selectedMedicationId = medication.id },
                                label = { Text(medication.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDownload(
                        HistoryReportRequest(
                            medicationId = selectedMedicationId,
                            fromDate = normalizedFrom,
                            toDate = normalizedTo
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.history_report_download))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = {
                        onShare(
                            HistoryReportRequest(
                                medicationId = selectedMedicationId,
                                fromDate = normalizedFrom,
                                toDate = normalizedTo
                            )
                        )
                    }
                ) {
                    Text(stringResource(R.string.history_report_share))
                }
            }
        }
    )

    activeDatePicker?.let { field ->
        HistoryDatePickerDialog(
            initialDate = if (field == DateRangePickerField.FROM) selectedFromDate else selectedToDate,
            onDismiss = { activeDatePicker = null },
            onDateSelected = { date ->
                if (field == DateRangePickerField.FROM) {
                    selectedFromDate = date
                } else {
                    selectedToDate = date
                }
                activeDatePicker = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerDialog(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditorDialog(
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    )
}

// Formatters
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val headerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM", Locale("he"))
private val historyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private enum class DateRangePickerField {
    FROM,
    TO
}
