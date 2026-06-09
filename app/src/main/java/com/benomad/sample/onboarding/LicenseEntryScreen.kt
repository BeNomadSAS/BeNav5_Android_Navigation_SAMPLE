package com.benomad.sample.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.benomad.sample.R

/**
 * License entry. The user pastes their BeNomad purchase UUID; `Core.init` then performs
 * the LBO call that decides full vs hybrid map data.
 *
 * The field is pre-filled from [OnboardingPreferences] (which falls back to the optional
 * `local.properties` dev default), so a developer who set it never has to type here.
 */
@Composable
fun LicenseEntryScreen(
    prefs: OnboardingPreferences,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable captures the initial value once (then restores from saved state), so editing
    // local.properties only re-prefills this field after clearing app data. The field is editable anyway.
    var uuid by rememberSaveable { mutableStateOf(prefs.purchaseUuid) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.license_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.license_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = uuid,
            onValueChange = { uuid = it },
            label = { Text(stringResource(R.string.license_uuid_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                prefs.purchaseUuid = uuid.trim()
                onContinue()
            },
            enabled = uuid.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.license_continue))
        }
    }
}
