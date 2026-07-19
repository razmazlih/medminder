package com.medminder.app.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medminder.app.R
import com.medminder.app.core.accessibility.AccessibilityDefaults
import com.medminder.app.core.i18n.displayLabelHe
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.IntakeAction
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
private data class TimeBadgeColors(
    val background: Color,
    val content: Color
)

private data class StatusBadgeColors(
    val background: Color,
    val content: Color
)

private data class EncouragementPalette(
    val container: Color,
    val glow: Color,
    val accent: Color,
    val title: Color,
    val text: Color
)

@Composable
fun TodayScreen(
    state: TodayUiState,
    onAction: (String, IntakeAction) -> Unit,
    onMarkAsNeededTaken: (String) -> Unit,
    onAddOneTimeMedicationIntake: (String) -> Unit,
    onEncouragementClick: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var showAsNeededDialog by remember { mutableStateOf(false) }
    var showAddAsNeededDialog by remember { mutableStateOf(false) }
    var newAsNeededMedicationName by remember { mutableStateOf("") }
    var selectedAsNeededMedicationId by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp)
        ) {
            item {
                TodayHeroCard(
                    greetingTitle = state.greetingTitle
                )
            }
            item {
                EncouragementCard(
                    message = state.encouragementMessage,
                    emoji = state.encouragementEmoji,
                    encouragementIndex = state.encouragementIndex,
                    onClick = onEncouragementClick
                )
            }
            item {
                QuickActionsCard(
                    onAsNeededClick = {
                        selectedAsNeededMedicationId = null
                        showAsNeededDialog = true
                    },
                    onAddOneTimeClick = {
                        newAsNeededMedicationName = ""
                        showAddAsNeededDialog = true
                    }
                )
            }

            if (!state.notificationsEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.today_notifications_disabled),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (!state.permissionGranted) {
                                Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.today_request_permission))
                                }
                            } else {
                                Button(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.open_settings))
                                }
                            }
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.today_empty_done),
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            items(state.items, key = { it.id }) { item ->
                var showTakenConfirmation by remember(item.id) { mutableStateOf(false) }
                val timeBadgeColors = timeLabelSunColors(item.timeLabel)
                val statusBadgeColors = statusBadgeColors(item.status)
                val doseSummary = if (item.doseLabel.isNotBlank()) {
                    stringResource(R.string.today_dose, item.doseLabel)
                } else {
                    ""
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append(item.medicationName)
                                append(", ")
                                append(item.timeLabel)
                                append(", ")
                                append(item.status.displayLabelHe())
                                if (doseSummary.isNotBlank()) {
                                    append(", ")
                                    append(doseSummary)
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = timeBadgeColors.background,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    text = "⏰ ${item.timeLabel}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = timeBadgeColors.content
                                )
                            }
                            Surface(
                                color = statusBadgeColors.background,
                                shape = MaterialTheme.shapes.large
                            ) {
                                val statusIcon = when (item.status) {
                                    DoseStatus.TAKEN -> "✅"
                                    DoseStatus.MISSED -> "⚠️"
                                    DoseStatus.PENDING -> "⏳"
                                }
                                Text(
                                    text = "$statusIcon ${item.status.displayLabelHe()}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusBadgeColors.content
                                )
                            }
                        }

                        Text(
                            item.medicationName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (item.doseLabel.isNotBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "💊",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = item.doseLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (item.notes.isNotBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "📝",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = item.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (item.canTakeNow) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LargeActionButton(stringResource(R.string.taken)) { showTakenConfirmation = true }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    text = stringResource(R.string.today_take_when_ready_hint),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                if (showTakenConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showTakenConfirmation = false },
                        title = {
                            Text(
                                text = "💊 ${stringResource(R.string.as_needed_taken_confirm_title)}",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "נא לאשר את הנטילה",
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "תרופה: ${item.medicationName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "שעה מתוכננת: ${item.timeLabel}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onAction(item.id, IntakeAction.TAKEN)
                                    showTakenConfirmation = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.common_confirm))
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showTakenConfirmation = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.successBannerMessageResId != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 520.dp),
                color = Color(0xFFE7F6EC),
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF1F6B3D)
                    )
                    Text(
                        text = state.successBannerMessageResId?.let { stringResource(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF1F6B3D),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showAsNeededDialog) {
        val selectedMedication = state.asNeededMedications.firstOrNull { it.id == selectedAsNeededMedicationId }
        val dialogContainerColor = Color(0xFFF8FAFC)
        val optionContainerColor = Color(0xFFFFFFFF)
        val optionSelectedContainerColor = Color(0xFFEAF2FF)
        val optionBorderColor = Color(0xFFD8E1EE)
        val optionSelectedBorderColor = Color(0xFF2D5FA8)
        val optionIndicatorColor = Color(0xFF2D5FA8)
        val confirmButtonColor = Color(0xFF2D5FA8)
        val confirmButtonDisabledColor = Color(0xFFAFC2DF)
        val optionAccentColor = Color(0xFF89A8D8)
        AlertDialog(
            onDismissRequest = { showAsNeededDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = dialogContainerColor,
            title = {
                Text(
                    text = stringResource(R.string.today_as_needed_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B2A3F)
                )
            },
            text = {
                if (state.asNeededMedications.isEmpty()) {
                    Text(stringResource(R.string.today_as_needed_empty))
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.asNeededMedications.forEach { medication ->
                            val isSelected = selectedAsNeededMedicationId == medication.id
                            val subtitle = medication.lastTakenMinutesAgo?.let { minutesAgo ->
                                when {
                                    minutesAgo > 360L -> null
                                    minutesAgo < 60L ->
                                        stringResource(R.string.today_as_needed_last_taken_minutes, minutesAgo)
                                    else ->
                                        stringResource(R.string.today_as_needed_last_taken_hours, minutesAgo / 60L)
                                }
                            }
                            OutlinedButton(
                                onClick = { selectedAsNeededMedicationId = medication.id },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 84.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) optionSelectedBorderColor else optionBorderColor
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) {
                                        optionSelectedContainerColor
                                    } else {
                                        optionContainerColor
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isSelected) optionIndicatorColor else optionAccentColor.copy(alpha = 0.12f),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            text = if (isSelected) "✓" else "＋",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else optionAccentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = medication.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF123E7A) else Color(0xFF263546)
                                        )
                                        if (subtitle != null) {
                                            Surface(
                                                color = if (isSelected) Color(0xFFDCE8FA) else Color(0xFFF1F5F9),
                                                shape = MaterialTheme.shapes.large
                                            ) {
                                                Text(
                                                    text = subtitle,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isSelected) Color(0xFF315B96) else Color(0xFF607289)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "זמינה עכשיו",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF7A8898)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedAsNeededMedicationId?.let { medicationId ->
                            onMarkAsNeededTaken(medicationId)
                            showAsNeededDialog = false
                        }
                    },
                    enabled = selectedAsNeededMedicationId != null && state.asNeededMedications.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = confirmButtonColor,
                        contentColor = Color.White,
                        disabledContainerColor = confirmButtonDisabledColor,
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        selectedMedication?.name?.let {
                            "${stringResource(R.string.today_as_needed_confirm_take)}: $it"
                        } ?: stringResource(R.string.today_as_needed_confirm_take)
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAsNeededDialog = false },
                    border = BorderStroke(1.dp, Color(0xFFD3DCE8)),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF52657C))
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    if (showAddAsNeededDialog) {
        AlertDialog(
            onDismissRequest = { showAddAsNeededDialog = false },
            title = { Text(stringResource(R.string.today_add_single_medication_title)) },
            text = {
                OutlinedTextField(
                    value = newAsNeededMedicationName,
                    onValueChange = { newAsNeededMedicationName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.editor_medication_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddOneTimeMedicationIntake(newAsNeededMedicationName)
                        showAddAsNeededDialog = false
                    },
                    enabled = newAsNeededMedicationName.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.common_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAsNeededDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun TodayHeroCard(
    greetingTitle: String
) {
    val titleText = greetingTitle.ifBlank { "שלום לך, יקרה" }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val currentDate = LocalDate.now()
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1_000L)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier.background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        Color(0xFF1457B8),
                        Color(0xFF0C4598)
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .size(96.dp)
                    .align(Alignment.TopStart)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = currentDate.format(dateFormatter),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = currentTime.format(timeFormatter),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Text(
                    text = titleText,
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun EncouragementCard(
    message: String,
    emoji: String,
    encouragementIndex: Int,
    onClick: () -> Unit
) {
    val palette = encouragementPalette(encouragementIndex)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                palette.glow.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = palette.glow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = emoji,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "encouragementEmoji"
                        ) { currentEmoji ->
                            Text(
                                text = currentEmoji,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "משפט קטן להמשך היום",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.title
                    )
                    AnimatedContent(
                        targetState = message,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "encouragementMessage"
                    ) { currentMessage ->
                        Text(
                            text = currentMessage,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.text
                        )
                    }
                }
                Surface(
                    color = palette.accent.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onAsNeededClick: () -> Unit,
    onAddOneTimeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "פעולות מהירות",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onAsNeededClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Medication,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.today_as_needed_button))
            }

            OutlinedButton(
                onClick = onAddOneTimeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.today_add_single_medication_button))
            }
        }
    }
}

@Composable
private fun RowScope.LargeActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = AccessibilityDefaults.MinTouchTarget)
            .semantics { contentDescription = text }
    ) {
        Text(text)
    }
}

@Composable
private fun timeLabelSunColors(timeLabel: String): TimeBadgeColors {
    val hour = runCatching { LocalTime.parse(timeLabel).hour }.getOrNull()
    return when (hour) {
        in 5..9 -> TimeBadgeColors(background = Color(0xFFFFE082), content = Color(0xFF2F1B00))
        in 10..16 -> TimeBadgeColors(background = Color(0xFFFFB74D), content = Color(0xFF2A1600))
        in 17..19 -> TimeBadgeColors(background = Color(0xFFFF8A65), content = Color(0xFF2A0E06))
        else -> TimeBadgeColors(background = Color(0xFF5C6BC0), content = Color(0xFFFFFFFF))
    }
}

@Composable
private fun statusBadgeColors(status: DoseStatus): StatusBadgeColors {
    return when (status) {
        DoseStatus.TAKEN -> StatusBadgeColors(
            background = Color(0xFFD8F5DF),
            content = Color(0xFF0F5132)
        )
        DoseStatus.MISSED -> StatusBadgeColors(
            background = Color(0xFFFFE0DB),
            content = Color(0xFF8A2A20)
        )
        DoseStatus.PENDING -> StatusBadgeColors(
            background = Color(0xFFDCEBFF),
            content = Color(0xFF103A71)
        )
    }
}

private fun encouragementPalette(index: Int): EncouragementPalette {
    val palettes = listOf(
        EncouragementPalette(
            container = Color(0xFFF7F2EA),
            glow = Color(0xFFFFE2B8),
            accent = Color(0xFFE39A1F),
            title = Color(0xFF986100),
            text = Color(0xFF3B3328)
        ),
        EncouragementPalette(
            container = Color(0xFFEEF7F3),
            glow = Color(0xFFCFEBDD),
            accent = Color(0xFF2F8F6B),
            title = Color(0xFF22684E),
            text = Color(0xFF26362F)
        ),
        EncouragementPalette(
            container = Color(0xFFF3F1FB),
            glow = Color(0xFFE1D8FB),
            accent = Color(0xFF7A5CC7),
            title = Color(0xFF6245A9),
            text = Color(0xFF312B45)
        ),
        EncouragementPalette(
            container = Color(0xFFFFF3F1),
            glow = Color(0xFFFFDDD8),
            accent = Color(0xFFD26A57),
            title = Color(0xFFB05545),
            text = Color(0xFF43302C)
        ),
        EncouragementPalette(
            container = Color(0xFFF0F6FB),
            glow = Color(0xFFD7E8F8),
            accent = Color(0xFF3E7CB8),
            title = Color(0xFF2C6295),
            text = Color(0xFF263542)
        )
    )
    return palettes[Math.floorMod(index, palettes.size)]
}
