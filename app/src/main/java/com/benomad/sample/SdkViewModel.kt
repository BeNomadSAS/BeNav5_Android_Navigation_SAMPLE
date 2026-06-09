package com.benomad.sample

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.benomad.sample.sdk.SdkProvider

/**
 * Creates (or retrieves, across recompositions/config changes) a [ViewModel] that depends on the
 * shared [SdkProvider].
 *
 * The sample uses **manual DI** (no Hilt), so every SDK-backed ViewModel is built with a tiny
 * factory. This helper collapses the otherwise-repeated
 * `viewModel(factory = viewModelFactory { initializer { … } })` incantation to one readable line,
 * while keeping construction explicit at the call site:
 *
 * ```
 * val viewModel = sdkViewModel(sdk) { GuidanceViewModel(it) }
 * ```
 *
 * The in-scope [sdk] is passed in (rather than re-resolved from the context) so the whole sample
 * keeps a single, consistent way of obtaining the SDK.
 */
@Composable
inline fun <reified VM : ViewModel> sdkViewModel(
    sdk: SdkProvider,
    crossinline create: (SdkProvider) -> VM,
): VM = viewModel(factory = viewModelFactory { initializer { create(sdk) } })
