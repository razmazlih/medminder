package com.medminder.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medminder.app.R

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    on24HourChanged: (Boolean) -> Unit,
    onSeniorModeChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val nameLabel = stringResource(R.string.onboarding_name)
    val format24Label = stringResource(R.string.onboarding_24h)
    val seniorModeLabel = stringResource(R.string.onboarding_senior_mode)
    val continueLabel = stringResource(R.string.common_continue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge)
        Text(text = stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = { Text(nameLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = nameLabel }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(
                checked = state.is24Hour,
                onCheckedChange = on24HourChanged,
                modifier = Modifier.semantics { contentDescription = format24Label }
            )
            Text(format24Label, style = MaterialTheme.typography.bodyLarge)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = state.seniorMode,
                onCheckedChange = onSeniorModeChanged,
                modifier = Modifier.semantics { contentDescription = seniorModeLabel }
            )
            Text(seniorModeLabel, style = MaterialTheme.typography.bodyLarge)
        }

        val timezoneValue = state.timezoneDisplay.ifBlank { stringResource(R.string.onboarding_timezone_auto) }
        Text(
            text = stringResource(R.string.onboarding_timezone, timezoneValue),
            style = MaterialTheme.typography.bodyLarge
        )

        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .semantics { contentDescription = continueLabel }
        ) {
            Text(continueLabel)
        }
    }
}
