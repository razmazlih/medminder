package com.medminder.app.feature.missed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medminder.app.R

@Composable
fun MissedDoseScreen(
    onMarkTakenNow: () -> Unit,
    onSkip: () -> Unit,
    onDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.missed_dose_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.missed_dose_message),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onMarkTakenNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.missed_mark_taken)) }
        Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.missed_skip)) }
        Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.missed_details)) }
    }
}
