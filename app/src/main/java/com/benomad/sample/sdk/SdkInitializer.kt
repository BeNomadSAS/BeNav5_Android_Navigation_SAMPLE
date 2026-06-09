package com.benomad.sample.sdk

import android.content.Context
import com.benomad.msdk.core.Core
import com.benomad.msdk.core.callbacks.OnCoreInit.OnCoreInitCallback
import com.benomad.msdk.core.callbacks.OnLicenseError.OnLicenseErrorCallback
import com.benomad.msdk.errormanager.Error
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Initializes the BeNomad SDK **Core** — the mandatory first step before any other
 * module (Mapping, Geocoder, Planner, Navigation) can be used.
 *
 * `Core.init(...)` is asynchronous: it deploys the bundled map resources from assets,
 * runs the native initialization off the main thread, performs the **license / LBO
 * call** (which is what decides full vs hybrid map data), and finally reports the
 * result on the main thread via an [OnCoreInitCallback]. This class bridges that
 * callback into a clean `suspend` function returning an [InitResult].
 *
 * Two correctness details worth copying into your own integration:
 * 1. A license failure is delivered through a *separate* [OnLicenseErrorCallback]
 *    that fires **before** the generic `onCoreInitError`. We register it first and
 *    prefer its (more specific) message when surfacing the error.
 * 2. `Core.init` is single-flight (returns `BUSY` if re-entered), so we short-circuit
 *    when [Core.isInit] is already `true`.
 */
class SdkInitializer {

    private val core: Core = Core.getInstance()

    /** Outcome of [initialize]. */
    sealed interface InitResult {
        /** Core is ready; every other module can now be created/used. */
        data object Success : InitResult

        /** Initialization failed (invalid/expired license, missing assets, no network…). */
        data class Failure(val message: String) : InitResult
    }

    /**
     * Initializes Core and suspends until it is ready or fails.
     *
     * @param appContext application context (never an Activity).
     * @param purchaseUuid the BeNomad purchase UUID (license) supplied by the user.
     */
    suspend fun initialize(
        appContext: Context,
        purchaseUuid: String,
    ): InitResult {
        // Already initialized (e.g. after a configuration change) — nothing to do.
        if (core.isInit()) return InitResult.Success

        return suspendCancellableCoroutine { continuation ->
            // Capture the license-specific error if one is emitted before onCoreInitError.
            var licenseError: Error? = null
            val licenseObserver = OnLicenseErrorCallback { error -> licenseError = error }
            core.addOnLicenseErrorObserver(licenseObserver)

            // If the caller's scope is cancelled before a callback fires (e.g. the user
            // leaves the splash screen), detach the observer so it does not leak on the
            // process-scoped Core singleton. The native init itself cannot be cancelled.
            continuation.invokeOnCancellation {
                core.removeOnLicenseErrorObserver(licenseObserver)
            }

            val callback = object : OnCoreInitCallback {
                override fun onCoreReady() {
                    core.removeOnLicenseErrorObserver(licenseObserver)
                    if (continuation.isActive) continuation.resume(InitResult.Success)
                }

                override fun onCoreInitError(error: Error) {
                    core.removeOnLicenseErrorObserver(licenseObserver)
                    // Prefer the specific license error over the generic init error.
                    val effective = licenseError ?: error
                    if (continuation.isActive) {
                        continuation.resume(InitResult.Failure(effective.readableMessage()))
                    }
                }

                override fun onCoreInitException(exception: Exception) {
                    core.removeOnLicenseErrorObserver(licenseObserver)
                    if (continuation.isActive) {
                        continuation.resume(InitResult.Failure(exception.message ?: "Initialization exception"))
                    }
                }
            }

            // mapsPathInAssets = null → the sample ships no offline map package; the LBO
            // decides whether map data is full (downloaded) or hybrid (streamed).
            core.init(
                appContext,
                purchaseUuid,
                // licenseKey: the SDK can force activation with an explicit key; not used here.
                null,
                core.getDefaultMapsPath(appContext, /* useSdCard = */ false),
                SdkConfig.RESOURCES_FOLDER_IN_ASSETS,
                /* mapsPathInAssets = */ null,
                callback,
            )
        }
    }
}
