package com.benomad.sample.guidance

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.annotation.MainThread
import com.benomad.msdk.mapping.MapView
import com.benomad.msdk.navigation.Navigation
import com.benomad.msdk.navigation.SessionState
import com.benomad.msdk.navigation.listener.ArrivalListener
import com.benomad.msdk.navigation.listener.InstructionsListener
import com.benomad.msdk.navigation.listener.NavigationProgressListener
import com.benomad.msdk.navigation.map.NavigationMapViewMode
import com.benomad.msdk.navigation.notification.ForegroundNotificationContent
import com.benomad.msdk.planner.route.Route
import com.benomad.sample.MainActivity
import com.benomad.sample.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps the BeNomad [Navigation] singleton for both **tracking** (follow-the-user with no
 * route) and **guidance** (turn-by-turn along a route).
 *
 * The engine is initialized once, with the application context, in [init]; everywhere else it is
 * fetched with the no-arg `Navigation.getInstance()`, which returns the same singleton without
 * re-initializing (passing a context is what triggers initialization). Requires `Core` to be
 * initialized first.
 *
 * Threading: calls that touch the attached `MapView` (attach/detach, follow mode, mute) and the
 * GNSS source's `start()`/`stop()` run on the main thread (the GNSS callback needs a Looper). The
 * heavy native session calls (`startSession`/`stopSession`) are dispatched off the main thread
 * here to avoid blocking it — our own precaution, not a threading contract the SDK documents.
 *
 * The engine renders the vehicle symbol and the route geometry itself once a session starts,
 * and — because it registers itself as its own `InstructionsListener` — it speaks the TTS
 * announcements automatically when [Navigation.audioEnabled] is true.
 */
class NavigationController(
    private val appContext: Context,
    private val gpsController: GpsController,
) {

    init {
        // Initialize the Navigation engine + TTS exactly once, by passing the application context.
        // Per the SDK contract, getInstance(appContext) initializes the engine ONLY when a context
        // is passed. Requires Core to be initialized first — guaranteed because SdkProvider builds
        // this controller lazily, after Core.init has completed.
        Navigation.getInstance(appContext)
    }

    // The Navigation engine, fetched with the no-arg getInstance() each time it is needed: it
    // returns the singleton initialized in `init` above WITHOUT re-initializing (only passing a
    // context re-initializes). No cached field — the no-arg accessor is the cheap, intended way.
    private val navigation: Navigation get() = Navigation.getInstance()

    /** The engine's current session state. */
    val sessionState: SessionState get() = navigation.getSessionState()

    /** True when a tracking or guidance session is active. */
    val isActive: Boolean get() = sessionState != SessionState.IDLE

    // --- Map binding ---

    // Tracks whether our MapView is attached (guards the relayout re-positioning below).
    private var mapAttached = false

    // The currently-attached MapView, kept only so detachMapView can unregister [onMapRelayout].
    // Cleared on detach so we never retain a destroyed view.
    private var attachedMapView: MapView? = null

    // The engine stores the vehicle's on-screen position in PIXELS, derived from the view's
    // width/height at the moment it is set. With android:configChanges the MapView is kept (NOT
    // re-attached) across a rotation, so we must re-apply the position once the view has been
    // re-laid-out — otherwise the follow camera stays offset (badly in landscape, where width and
    // height swap). This listener fires AFTER layout, so the view reports its true new size here.
    private val onMapRelayout = View.OnLayoutChangeListener {
        _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
        if (sizeChanged) applyVehicleScreenPosition()
    }

    @MainThread
    fun attachMapView(mapView: MapView) {
        navigation.attachMapView(mapView, VERTICAL_VEHICLE_POSITION)
        mapAttached = true
        applyVehicleScreenPosition()
        // Re-apply the position on every later resize (rotation / multi-window). De-dupe first in
        // case the same view is re-attached.
        mapView.removeOnLayoutChangeListener(onMapRelayout)
        mapView.addOnLayoutChangeListener(onMapRelayout)
        attachedMapView = mapView
    }

    @MainThread
    fun detachMapView() {
        // Symmetric teardown: unregister the relayout listener from the view we attached.
        attachedMapView?.removeOnLayoutChangeListener(onMapRelayout)
        attachedMapView = null
        navigation.detachMapView()
        mapAttached = false
    }

    /**
     * Places the vehicle symbol lower-centre on screen (so the upcoming route is visible ahead of
     * it), recomputed from the CURRENT view size. Both axes are pixel values the engine derives
     * from the view dimensions, so this must be re-run whenever the map is resized (see [onMapRelayout]).
     */
    @MainThread
    private fun applyVehicleScreenPosition() {
        if (!mapAttached) return
        navigation.setVehicleVerticalPosition(VERTICAL_VEHICLE_POSITION)
        navigation.setVehicleHorizontalPosition(HORIZONTAL_VEHICLE_POSITION)
    }

    // --- Tracking (no route) ---

    /** Binds real GPS and starts a TRACKING session so the map follows the user. */
    suspend fun startTracking(): Boolean {
        val source = gpsController.deviceSource()
        // GPSManager.start registers a GNSS callback → main thread.
        val started = withContext(Dispatchers.Main) { gpsController.start(source) }
        if (!started) return false
        val bound = withContext(Dispatchers.IO) { gpsController.bind(navigation, source) }
        if (!bound) return false
        withContext(Dispatchers.Main) {
            navigation.zoomLevel3D = TRACKING_ZOOM
            navigation.setNavigationMapViewMode(NavigationMapViewMode.GUIDANCE_VIEW_3D)
        }
        return withContext(Dispatchers.Default) {
            val error = navigation.startSession(appContext) // route == null → TRACKING
            // Some SDK calls signal success with a non-null Error whose code is 0 (confirmed on the
            // location-source / detach calls); accepted here as a precaution for startSession too.
            error == null || error.code == 0L
        }
    }

    // --- Guidance (with route) ---

    /**
     * Starts guidance along [route]. Uses a simulated source when [demoMode] is true, the real
     * GPS otherwise. Enables TTS (if a TTS engine is available), the 3D follow camera, the
     * maneuver-icon styles (so `onNewInstruction` delivers bitmaps), and a foreground-service
     * notification (so location keeps flowing if the app is backgrounded).
     */
    suspend fun startGuidance(route: Route, demoMode: Boolean): Boolean {
        val source = if (demoMode) gpsController.demoSource(route) else gpsController.deviceSource()
        val started = withContext(Dispatchers.Main) { gpsController.start(source) }
        if (!started) return false
        val bound = withContext(Dispatchers.IO) { gpsController.bind(navigation, source) }
        if (!bound) return false
        withContext(Dispatchers.Main) {
            // Navigation speaks the turn-by-turn announcements itself while audioEnabled is true.
            navigation.ttsMode = navigation.isTTSAvailable(appContext)
            navigation.audioEnabled = true
            // Erase the part of the route already driven (it stays drawn otherwise).
            navigation.hidePassedRoute(appContext, true)
            navigation.zoomLevel3D = GUIDANCE_ZOOM
            navigation.setNavigationMapViewMode(NavigationMapViewMode.GUIDANCE_VIEW_3D)
        }
        val sessionStarted = withContext(Dispatchers.Default) {
            val error = navigation.startSession(
                appContext,
                buildNotificationContent(),
                route,
                GuidanceStyles.instructionsIconsStyles(appContext),
            )
            // Some SDK calls signal success with a non-null Error whose code is 0 (confirmed on the
            // location-source / detach calls); accepted here as a precaution for startSession too.
            error == null || error.code == 0L
        }
        if (sessionStarted) {
            // Start the task-removal sentinel: swiping the app away during guidance must stop the
            // native session, which the SDK's own foreground service does NOT do (see
            // GuidanceLifecycleService). Safe to start from the foreground (guidance is on-screen).
            appContext.startService(Intent(appContext, GuidanceLifecycleService::class.java))
        }
        return sessionStarted
    }

    /**
     * Stops the active session; suspends until done (heavy native call, run off the main thread).
     * The caller should await this BEFORE navigating away so the session is fully stopped before
     * the map view is destroyed.
     */
    suspend fun stopGuidance() {
        withContext(Dispatchers.Default) {
            if (isActive) navigation.stopSession(appContext)
        }
        // stopSession does NOT release the GPS source, so stop it explicitly (symmetric with the
        // task-removal path in GuidanceLifecycleService). Otherwise a demo/real source keeps
        // running until the map happens to rebind it — and forever if the user never returns to the
        // map with GPS enabled.
        withContext(Dispatchers.Main) { gpsController.stop() }
        // Guidance is over — stop the task-removal sentinel.
        appContext.stopService(Intent(appContext, GuidanceLifecycleService::class.java))
    }

    /**
     * Synchronously stops guidance. Terminal-cleanup path ONLY — called from
     * [GuidanceLifecycleService.onTaskRemoved], where we cannot suspend and must tear the native
     * session down before the process dies. `stopSession` also stops TTS and removes the
     * foreground notification.
     */
    @MainThread
    fun stopGuidanceNow() {
        if (isActive) navigation.stopSession(appContext)
    }

    // --- Follow camera ---

    /** Re-enables follow mode (also used as "re-center"). */
    @MainThread
    fun enableFollow() {
        navigation.setNavigationMapViewMode(NavigationMapViewMode.GUIDANCE_VIEW_3D)
    }

    /** Switches to free mode: stops following and re-enables map gestures. */
    @MainThread
    fun disableFollow() {
        navigation.setNavigationMapViewMode(NavigationMapViewMode.GUIDANCE_VIEW_FREE)
    }

    /** Re-attaches a recreated [MapView] (config change) and resumes follow if we were following. */
    @MainThread
    fun reattachAndResume(mapView: MapView, following: Boolean) {
        attachMapView(mapView)
        if (isActive && following) enableFollow()
    }

    // --- Listeners (the caller owns the listener objects; we just (un)register them) ---

    fun addProgressListener(listener: NavigationProgressListener) = navigation.addNavigationProgressListener(listener)
    fun removeProgressListener(listener: NavigationProgressListener) = navigation.removeNavigationProgressListener(listener)
    fun addInstructionsListener(listener: InstructionsListener) = navigation.addInstructionsListener(listener)
    fun removeInstructionsListener(listener: InstructionsListener) = navigation.removeInstructionsListener(listener)
    fun addArrivalListener(listener: ArrivalListener) = navigation.addArrivalListener(listener)
    fun removeArrivalListener(listener: ArrivalListener) = navigation.removeArrivalListener(listener)

    /**
     * Toggles voice guidance. `audioEnabled` gates whether new announcements are queued;
     * `muteAudio()`/`unMuteAudio()` also silences/restores any announcement already playing.
     * @return true if guidance is now muted.
     */
    @MainThread
    fun toggleMute(): Boolean {
        val enable = !navigation.audioEnabled
        navigation.audioEnabled = enable
        if (enable) navigation.unMuteAudio() else navigation.muteAudio()
        return !enable
    }

    private fun buildNotificationContent(): ForegroundNotificationContent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return ForegroundNotificationContent(
            pendingIntent = pendingIntent,
            smallIconResId = R.drawable.ic_nav_notification,
            contentTitle = appContext.getString(R.string.app_name),
            contentText = appContext.getString(R.string.guidance_notification_text),
        )
    }

    private companion object {
        // Vehicle symbol position: fraction of the map HEIGHT from the bottom (0.3 = lower third,
        // leaving room ahead to see the upcoming route) and fraction of the WIDTH from the left
        // (0.5 = horizontally centred). Both are re-applied on resize — see applyVehicleScreenPosition.
        const val VERTICAL_VEHICLE_POSITION = 0.3
        const val HORIZONTAL_VEHICLE_POSITION = 0.5
        // 3D follow-camera zoom. The SDK's 3D default (20.0) is too close; 16.0 keeps the route
        // and on-map maneuver arrows at a comfortable size — same value for tracking and guidance.
        const val TRACKING_ZOOM = 16.0
        const val GUIDANCE_ZOOM = 16.0
    }
}
