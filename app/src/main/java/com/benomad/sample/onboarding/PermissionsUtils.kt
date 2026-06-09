package com.benomad.sample.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Small helpers around Android runtime permissions. The sample uses the modern
 * AndroidX permission APIs directly (`ActivityResultContracts.RequestPermission`)
 * rather than the optional mSDK `PermissionsManager`, which is the recommended approach.
 */
object PermissionsUtils {

    /** GPS for tracking and guidance. */
    const val LOCATION: String = Manifest.permission.ACCESS_FINE_LOCATION

    /**
     * Notification permission for the guidance foreground-service notification.
     * The string constant is safe to reference on any API level; it is only *requested*
     * on Android 13+ (see [hasNotificationPermission]).
     */
    const val NOTIFICATIONS: String = Manifest.permission.POST_NOTIFICATIONS

    /** Live check — never cache "granted", as the user can revoke it in settings. */
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Always true below Android 13 (the permission does not exist there). */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Opens this app's system settings page (used when a permission is permanently denied). */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}

/**
 * Walks the [Context] wrapper chain to find the hosting [Activity]. Needed for
 * `shouldShowRequestPermissionRationale`, which requires an Activity.
 *
 * @throws IllegalStateException if no Activity is present in the context chain.
 */
fun Context.findActivity(): Activity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No Activity found in the Context chain")
}
