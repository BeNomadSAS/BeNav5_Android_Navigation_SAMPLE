package com.benomad.sample

import android.app.Application
import com.benomad.sample.sdk.SdkProvider

/**
 * Application entry point for the BeNomad mSDK navigation sample.
 *
 * This sample uses **manual dependency injection** (no Hilt). The single
 * [SdkProvider] created here owns the process-lifetime BeNomad SDK objects that
 * must outlive any individual screen (the online geocoder, the route planner, the
 * navigation engine and the GPS source). Screens reach it with
 * `(application as SampleApp).sdk`.
 *
 * Nothing in the SDK is initialized here: `Core.init(...)` is an asynchronous call
 * that needs a license and runtime permissions, so it is triggered later from the
 * splash screen (see `onboarding/SplashViewModel`). Keeping [Application.onCreate]
 * cheap avoids blocking app startup.
 */
class SampleApp : Application() {

    /** Manual-DI root holding the shared, process-scoped SDK singletons. */
    lateinit var sdk: SdkProvider
        private set

    override fun onCreate() {
        super.onCreate()
        sdk = SdkProvider(applicationContext)
    }
}
