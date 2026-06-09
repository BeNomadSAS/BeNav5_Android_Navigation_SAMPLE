package com.benomad.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.benomad.sample.guidance.GuidanceScreen
import com.benomad.sample.map.MapScreen
import com.benomad.sample.onboarding.LicenseConsentScreen
import com.benomad.sample.onboarding.LicenseEntryScreen
import com.benomad.sample.onboarding.OnboardingRouter
import com.benomad.sample.onboarding.RequestLocationPermissionScreen
import com.benomad.sample.onboarding.RequestNotificationPermissionScreen
import com.benomad.sample.onboarding.SplashScreen
import com.benomad.sample.onboarding.SplashViewModel
import com.benomad.sample.route.RoutePreviewScreen
import com.benomad.sample.sdk.geoPoint

/**
 * Root navigation graph.
 *
 * The onboarding routes (consent → license → permissions → splash) are visited in the
 * order decided by [OnboardingRouter]; each screen recomputes the next route on
 * completion, so already-satisfied steps are skipped and the flow is resumable. Reaching
 * [AppRoutes.MAP] means the SDK is initialized and the map style is loaded.
 */
@Composable
fun AppNavHost() {
    val context = LocalContext.current
    val sdk = remember(context) { (context.applicationContext as SampleApp).sdk }
    val prefs = sdk.onboardingPreferences
    val navController = rememberNavController()

    // Computed once; the graph self-corrects as each onboarding step completes.
    val startRoute = remember { OnboardingRouter.nextRoute(sdk.appContext, prefs) }

    // Navigate to the next required route, replacing the screen we are leaving.
    fun advanceFrom(current: String) {
        val next = OnboardingRouter.nextRoute(sdk.appContext, prefs)
        navController.navigate(next) {
            popUpTo(current) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {

        composable(AppRoutes.CONSENT) {
            LicenseConsentScreen(
                onAgree = {
                    prefs.consentGiven = true
                    advanceFrom(AppRoutes.CONSENT)
                },
            )
        }

        composable(AppRoutes.LICENSE) {
            LicenseEntryScreen(
                prefs = prefs,
                onContinue = { advanceFrom(AppRoutes.LICENSE) },
            )
        }

        composable(AppRoutes.LOCATION_PERMISSION) {
            RequestLocationPermissionScreen(
                onGranted = { advanceFrom(AppRoutes.LOCATION_PERMISSION) },
            )
        }

        composable(AppRoutes.NOTIFICATION_PERMISSION) {
            RequestNotificationPermissionScreen(
                onContinue = {
                    // Optional step: record it was offered so a denial/skip doesn't route back here.
                    prefs.notificationPromptHandled = true
                    advanceFrom(AppRoutes.NOTIFICATION_PERMISSION)
                },
            )
        }

        composable(AppRoutes.SPLASH) {
            val splashViewModel = sdkViewModel(sdk) { SplashViewModel(it) }
            SplashScreen(
                viewModel = splashViewModel,
                onReady = { advanceFrom(AppRoutes.SPLASH) },
            )
        }

        composable(AppRoutes.MAP) {
            MapScreen(
                sdk = sdk,
                onPlanRoute = { destination, label ->
                    navController.navigate(
                        AppRoutes.routePreview(destination.longitude(), destination.latitude(), label),
                    )
                },
            )
        }

        composable(
            route = AppRoutes.ROUTE_PREVIEW,
            arguments = listOf(
                navArgument("lon") { type = NavType.StringType },
                navArgument("lat") { type = NavType.StringType },
                navArgument("label") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val longitude = arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            val latitude = arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val label = arguments?.getString("label").orEmpty()
            RoutePreviewScreen(
                sdk = sdk,
                destination = geoPoint(longitude, latitude),
                destinationLabel = label,
                onStartNavigation = { navController.navigate(AppRoutes.GUIDANCE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppRoutes.GUIDANCE) {
            GuidanceScreen(
                sdk = sdk,
                onExit = { navController.popBackStack(AppRoutes.MAP, inclusive = false) },
            )
        }
    }
}
