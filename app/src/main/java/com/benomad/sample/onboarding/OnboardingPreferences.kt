package com.benomad.sample.onboarding

import android.content.Context
import androidx.core.content.edit
import com.benomad.sample.sdk.SdkConfig

/**
 * Tiny [android.content.SharedPreferences] wrapper for the onboarding state: the one-time consent
 * flag, the user-supplied license, and whether the optional notification step has been offered.
 *
 * Note we deliberately **do not** persist "permission granted" — runtime permissions are always
 * re-checked live (the user can revoke them in system settings), see [OnboardingRouter]. The
 * optional notification step is the one exception: [notificationPromptHandled] records that it was
 * *offered* (not that it was granted) so a declined optional step isn't re-shown on every launch.
 * The license fields fall back to the optional dev defaults from [SdkConfig] (sourced from
 * `local.properties`) when nothing has been entered yet.
 */
class OnboardingPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the user accepted the license/terms (one-time gate). */
    var consentGiven: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit { putBoolean(KEY_CONSENT, value) }

    /** The BeNomad purchase UUID (license) entered by the user, or the dev default. */
    var purchaseUuid: String
        get() = prefs.getString(KEY_PURCHASE_UUID, null) ?: SdkConfig.defaultPurchaseUuid
        set(value) = prefs.edit { putString(KEY_PURCHASE_UUID, value) }

    /** True once a non-blank purchase UUID is available. */
    val hasLicense: Boolean get() = purchaseUuid.isNotBlank()

    /**
     * Whether the optional notification-permission step has been offered (granted, denied, or
     * skipped). Persisted so denying/skipping it advances onboarding instead of looping back.
     */
    var notificationPromptHandled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PROMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_PROMPT, value) }

    private companion object {
        const val PREFS_NAME = "benomad_sample_onboarding"
        const val KEY_CONSENT = "consent_given"
        const val KEY_PURCHASE_UUID = "purchase_uuid"
        const val KEY_NOTIFICATION_PROMPT = "notification_prompt_handled"
    }
}
