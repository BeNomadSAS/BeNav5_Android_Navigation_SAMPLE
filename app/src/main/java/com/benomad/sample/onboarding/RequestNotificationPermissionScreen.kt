package com.benomad.sample.onboarding

import android.os.Build
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.benomad.sample.R

/**
 * Requests `POST_NOTIFICATIONS` (Android 13+) so the guidance foreground-service
 * notification can be shown. Denial is **non-blocking** — the user can skip and still
 * navigate. On older Android versions this screen immediately advances.
 */
@Composable
fun RequestNotificationPermissionScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    // OnboardingRouter already skips this screen below Android 13; this guard is a defensive
    // fallback so the screen stays safe to reuse in a different routing setup.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(Unit) { onContinue() }
        return
    }

    // Either outcome advances the flow — notifications are optional.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onContinue() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_notification_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_notification_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { launcher.launch(PermissionsUtils.NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.permission_notification_grant))
        }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.permission_skip))
        }
    }
}
