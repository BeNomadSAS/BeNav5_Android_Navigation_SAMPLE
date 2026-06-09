package com.benomad.sample.guidance

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.benomad.sample.SampleApp

/**
 * A minimal started service whose only job is to stop guidance when the app is swiped away from
 * the recents screen **while guidance is running**.
 *
 * ### Why this exists (mSDK gotcha worth documenting for integrators)
 * The BeNomad foreground service that keeps GPS flowing during guidance only removes its own
 * notification when the task is removed — it does **not** stop the native guidance session. So
 * without this, swiping the app away leaves guidance running headless: the progress thread keeps
 * firing, TTS keeps speaking, demo movement continues, and reopening the app shows a stale route.
 *
 * [Service.onTaskRemoved] is the only reliable hook for "the user dismissed the app": it fires for
 * a started service that belongs to the dismissed task, whereas `Activity.onDestroy` is not
 * guaranteed when the OS kills the process.
 *
 * The service holds no state. [NavigationController] starts it when guidance begins and stops it
 * when guidance ends, so it is alive exactly when it can be useful. It is declared with
 * `android:stopWithTask="false"` so the system delivers [onTaskRemoved] (with `"true"` the service
 * would just be stopped without the callback).
 */
class GuidanceLifecycleService : Service() {

    // Not a bound service.
    override fun onBind(intent: Intent?): IBinder? = null

    // Nothing to do on start — we exist only to receive onTaskRemoved.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val sdk = (application as SampleApp).sdk
        // Stop the native guidance session (this also stops TTS and removes the foreground
        // notification) and release the GPS source. Run synchronously: the process may be torn
        // down immediately after this returns.
        sdk.navigationController.stopGuidanceNow()
        sdk.gpsController.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
