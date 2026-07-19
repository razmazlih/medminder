package com.medminder.app.feature.applock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.medminder.app.R

@Composable
fun AppLockScreen(
    state: AppLockUiState,
    forceLock: Boolean,
    onSubmit: (String) -> Unit
) {
    BackHandler(enabled = forceLock) { }
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(
                if (state.hasPin) R.string.applock_title else R.string.applock_setup_title
            ),
            style = MaterialTheme.typography.headlineLarge
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.take(6) },
            label = { Text(stringResource(R.string.applock_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { onSubmit(pin) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.common_confirm))
        }
    }
}
