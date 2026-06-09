package com.benomad.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.benomad.sample.ui.theme.BeNomadSampleTheme

/**
 * The single Activity of the sample.
 *
 * It is a plain [ComponentActivity] (no AppCompat / Fragments needed) that draws
 * edge-to-edge and hosts the whole app as a Compose [AppNavHost]. The navigation
 * graph — onboarding, splash (SDK init), map, search, route preview and guidance —
 * is wired in [AppNavHost].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars; screens apply window insets where needed.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeNomadSampleTheme {
                AppNavHost()
            }
        }
    }
}
