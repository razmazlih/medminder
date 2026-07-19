package com.medminder.app.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medminder.app.R

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTextScaleChanged: (Float) -> Unit,
    onHighContrastChanged: (Boolean) -> Unit,
    onEscalatedReminderChanged: (Boolean) -> Unit,
    onAppLockChanged: (Boolean) -> Unit,
    onCloudBackupChanged: (Boolean) -> Unit,
    onGenerateNewSyncCode: () -> Unit,
    onSyncNow: () -> Unit,
    onExportAllLocalDataToCloud: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onSaveDisplayName: () -> Unit,
    onDismissSyncMessage: () -> Unit,
    onOpenHiddenLog: () -> Unit
) {
    var titleTapCount by remember { mutableIntStateOf(0) }
    var copyFeedbackVisible by remember { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeaderCard(
            title = stringResource(R.string.settings_title),
            subtitle = "התאמה אישית, נגישות וסנכרון במקום אחד",
            onTitleTap = {
                titleTapCount += 1
                if (titleTapCount >= 7) {
                    titleTapCount = 0
                    onOpenHiddenLog()
                }
            }
        )

        SettingsSection(title = "פרופיל") {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChanged,
                label = { Text(stringResource(R.string.settings_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSaveDisplayName,
                enabled = state.displayName.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_save_name))
            }
        }

        SettingsSection(title = "נגישות") {
            Text(
                text = stringResource(R.string.settings_text_scale, state.textScale),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = state.textScale,
                onValueChange = onTextScaleChanged,
                valueRange = 1.0f..1.6f,
                modifier = Modifier.padding(top = 4.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LabeledSwitchRow(
                label = stringResource(R.string.settings_high_contrast),
                checked = state.highContrast,
                onCheckedChange = onHighContrastChanged
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSection(title = "התראות ואבטחה") {
            LabeledSwitchRow(
                label = stringResource(R.string.settings_escalated_reminder),
                checked = state.escalatedReminderEnabled,
                onCheckedChange = onEscalatedReminderChanged
            )
            Spacer(Modifier.height(10.dp))
            LabeledSwitchRow(
                label = stringResource(R.string.settings_app_lock),
                checked = state.appLockEnabled,
                onCheckedChange = onAppLockChanged
            )
        }

        SettingsSection(title = stringResource(R.string.settings_cloud_title)) {
            LabeledSwitchRow(
                label = stringResource(R.string.settings_cloud_backup),
                checked = state.cloudBackupEnabled,
                onCheckedChange = onCloudBackupChanged
            )
            Spacer(Modifier.height(10.dp))
            SyncStatusRow(
                label = stringResource(R.string.settings_cloud_status, ""),
                value = state.syncState
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_cloud_last_sync, state.lastSyncAtLabel),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.settings_cloud_last_pull, state.lastPulledAtLabel),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onGenerateNewSyncCode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_cloud_generate))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sync Code מונפק מהאפליקציה בלבד ומאפשר התחברות לאתר",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSyncNow,
                enabled = state.cloudBackupEnabled && state.hasCloudConfiguration,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_cloud_sync_now))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportAllLocalDataToCloud,
                enabled = state.cloudBackupEnabled && state.hasCloudConfiguration,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_cloud_export_all_now))
            }

            state.generatedSyncCode?.let { code ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.settings_cloud_generated_code, code),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            copyFeedbackVisible += 1
                        }
                    ) {
                        Text("העתק קוד")
                    }
                }
                if (copyFeedbackVisible > 0) {
                    Text(
                        text = "הקוד הועתק ללוח",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LaunchedEffect(copyFeedbackVisible) {
                        kotlinx.coroutines.delay(1800)
                        copyFeedbackVisible = 0
                    }
                }
            }

            state.syncMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismissSyncMessage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(message)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    title: String,
    subtitle: String,
    onTitleTap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onTap = { onTitleTap() })
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SyncStatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
