package com.benomad.sample.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.benomad.sample.R

/**
 * Requests `ACCESS_FINE_LOCATION` using the modern AndroidX launcher.
 *
 * - First tap → system permission dialog.
 * - Permanently denied (the dialog won't show again) → offer "Open settings".
 * - Returning from settings with the permission granted → auto-advance via an
 *   `ON_RESUME` re-check (permission state is always read live).
 */
@Composable
fun RequestLocationPermissionScreen(onGranted: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // Starts false even if the user permanently denied in a PRIOR session: the first tap then does
    // nothing visible (the system silently rejects it), the launcher callback flips this to true,
    // and the button switches to "Open settings". One wasted tap is acceptable for a sample;
    // persisting a "has been asked" flag would contradict the deliberate live-only permission model.
    var permanentlyDenied by remember { mutableStateOf(false) }
    var awaitingSettingsReturn by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onGranted()
        } else {
            // shouldShowRationale == false right after a denial means "don't ask again".
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, PermissionsUtils.LOCATION)
        }
    }

    // Re-check ONLY after returning from system settings. The permission dialog also
    // triggers an ON_RESUME, so an unconditional check here would call onGranted() twice
    // (once from the launcher result, once on resume) and corrupt the back stack.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Single-use: consume the flag so this acts only on the first resume after returning from
        // settings (and never on the resume that follows the permission dialog itself).
        if (awaitingSettingsReturn) {
            awaitingSettingsReturn = false
            if (PermissionsUtils.hasLocationPermission(context)) onGranted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_location_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_location_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        if (permanentlyDenied) {
            Button(
                onClick = {
                    awaitingSettingsReturn = true
                    PermissionsUtils.openAppSettings(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(
                onClick = { launcher.launch(PermissionsUtils.LOCATION) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.permission_location_grant))
            }
        }
    }
}
