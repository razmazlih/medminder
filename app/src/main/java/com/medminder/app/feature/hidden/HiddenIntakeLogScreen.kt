package com.medminder.app.feature.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.core.i18n.displayLabelHe
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.IntakeLogOperation
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HiddenIntakeLogScreen(
    state: HiddenIntakeLogUiState,
    onQueryChange: (String) -> Unit,
    onOperationFilterChange: (IntakeLogOperation?) -> Unit,
    onSourceFilterChange: (ActionSource?) -> Unit,
    onTimeRangeChange: (HiddenLogTimeRange) -> Unit,
    onSuspiciousOnlyChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.hidden_log_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "בקרה וניתוח פעילות נטילות",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            MonitoringCard(summary = state.summary)
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("חיפוש לפי תרופה או פרטי שינוי") }
            )
        }

        item {
            FilterSectionTitle("טווח זמן")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HiddenLogTimeRange.values().toList()) { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onTimeRangeChange(range) },
                        label = { Text(rangeLabel(range)) }
                    )
                }
            }
        }

        item {
            FilterSectionTitle("סוג פעולה")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedOperation == null,
                        onClick = { onOperationFilterChange(null) },
                        label = { Text("הכל") }
                    )
                }
                items(IntakeLogOperation.values().toList()) { operation ->
                    FilterChip(
                        selected = state.selectedOperation == operation,
                        onClick = { onOperationFilterChange(operation) },
                        label = { Text(operationLabel(operation)) }
                    )
                }
            }
        }

        item {
            FilterSectionTitle("מקור פעולה")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedSource == null,
                        onClick = { onSourceFilterChange(null) },
                        label = { Text("הכל") }
                    )
                }
                items(ActionSource.values().toList()) { source ->
                    FilterChip(
                        selected = state.selectedSource == source,
                        onClick = { onSourceFilterChange(source) },
                        label = { Text(source.displayLabelHe()) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("חשודים בלבד")
                    Switch(
                        checked = state.suspiciousOnly,
                        onCheckedChange = onSuspiciousOnlyChange
                    )
                }
                OutlinedButton(onClick = onClearFilters) {
                    Text("נקה סינון")
                }
            }
        }

        if (state.items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.hidden_log_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@LazyColumn
        }

        items(state.items, key = { it.log.id }) { entry ->
            val log = entry.log
            val suspicious = entry.suspiciousReasons.isNotEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (suspicious) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = log.medicationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (suspicious) {
                            AssistChip(
                                onClick = {},
                                label = { Text("חשוד") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }

                    Text(
                        text = log.takenAt.atZone(ZoneId.systemDefault()).format(formatter),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(text = operationLabel(log.operationType))
                        Tag(text = log.actionSource.displayLabelHe())
                    }

                    if (!log.operationDetails.isNullOrBlank()) {
                        Text(
                            text = log.operationDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (entry.suspiciousReasons.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "סימנים חריגים",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            entry.suspiciousReasons.forEach { reason ->
                                Text(
                                    text = "• $reason",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun MonitoringCard(summary: HiddenLogSummary) {
    val statusColor = when {
        summary.suspiciousCount >= 8 -> MaterialTheme.colorScheme.error
        summary.suspiciousCount > 0 -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.primary
    }
    val backgroundColor = when {
        summary.suspiciousCount >= 8 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        summary.suspiciousCount > 0 -> Color(0xFFFFF1DC)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "סטטוס ניטור",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        summary.suspiciousCount >= 8 -> "סיכון גבוה"
                        summary.suspiciousCount > 0 -> "דורש בדיקה"
                        else -> "תקין"
                    },
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricTile(label = "פעולות", value = summary.total.toString())
                MetricTile(label = "חשודים", value = summary.suspiciousCount.toString())
                MetricTile(label = "תרופות", value = summary.uniqueMedicationCount.toString())
                MetricTile(label = "מחיקות חסומות", value = summary.blockedDeleteAttempts.toString())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("הוספה: ${summary.addCount}")
                Text("עריכה: ${summary.updateCount}")
                Text("מחיקה: ${summary.deleteCount}")
                Text("שינוי: ${summary.changeCount}")
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Tag(text: String) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

private fun rangeLabel(range: HiddenLogTimeRange): String {
    return when (range) {
        HiddenLogTimeRange.LAST_24_HOURS -> "24 שעות"
        HiddenLogTimeRange.LAST_7_DAYS -> "7 ימים"
        HiddenLogTimeRange.LAST_30_DAYS -> "30 ימים"
        HiddenLogTimeRange.ALL -> "הכול"
    }
}

private fun operationLabel(operationType: IntakeLogOperation): String {
    return when (operationType) {
        IntakeLogOperation.ADD -> "הוספה"
        IntakeLogOperation.UPDATE -> "עריכה"
        IntakeLogOperation.DELETE -> "מחיקה"
        IntakeLogOperation.CHANGE -> "שינוי"
    }
}
