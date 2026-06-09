package com.benomad.sample.onboarding

import android.content.Context
import android.os.Build
import com.benomad.msdk.core.Core
import com.benomad.sample.AppRoutes

/**
 * Pure routing function for the onboarding flow.
 *
 * It inspects persisted state ([OnboardingPreferences]) plus *live* permission and
 * SDK-init state and returns the next route. Because it is recomputed after every
 * screen completes, the flow naturally skips already-satisfied steps and is resumable
 * across app launches.
 */
object OnboardingRouter {

    /** Returns the route the app should currently be on. */
    fun nextRoute(context: Context, prefs: OnboardingPreferences): String = when {
        !prefs.consentGiven -> AppRoutes.CONSENT
        !prefs.hasLicense -> AppRoutes.LICENSE
        !PermissionsUtils.hasLocationPermission(context) -> AppRoutes.LOCATION_PERMISSION
        // Notification permission is OPTIONAL: offer it only once (notificationPromptHandled), so
        // denying or skipping advances instead of looping back here. (Location, above, is required
        // and stays live-checked until granted.)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionsUtils.hasNotificationPermission(context) &&
            !prefs.notificationPromptHandled -> AppRoutes.NOTIFICATION_PERMISSION
        // Everything granted: go to the map if the SDK is already up, else initialize it.
        // Core.getInstance() returns the existing singleton; isInit() is a cheap flag read, not a re-init.
        Core.getInstance().isInit() -> AppRoutes.MAP
        else -> AppRoutes.SPLASH
    }
}
