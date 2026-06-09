package com.benomad.sample.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benomad.msdk.errormanager.Error
import com.benomad.msdk.geocoder.filters.OnlineSearchFilter
import com.benomad.msdk.geocoder.results.AutocompleteResult
import com.benomad.msdk.geocoder.results.SearchCallback
import com.benomad.sample.sdk.SdkConfig
import com.benomad.sample.sdk.SdkProvider
import com.benomad.sample.sdk.readableMessage
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives online destination search via the BeNomad [com.benomad.msdk.geocoder.OnlineGeoCoder].
 *
 * The SDK does not debounce, so we replicate the standard pattern: cancel the previous
 * job and relaunch after a 500 ms pause, then call `autoComplete`. Results bias toward the
 * user's last known position (or a default city centre). `OnlineGeoCoder` delivers its
 * callback on the HTTP thread, so we (a) only apply results that still match the current
 * query and (b) rely on [MutableStateFlow] being thread-safe.
 *
 * Requires Core to be initialized with an autocomplete-enabled license and a network
 * connection; otherwise `autoComplete` returns an error in the callback.
 */
class SearchViewModel(private val sdk: SdkProvider) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Called on each keystroke; debounces then searches. */
    fun onQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(query = query, suggestions = emptyList(), isLoading = false, error = null) }
            return
        }
        // Show the spinner immediately; the request itself fires after the debounce.
        _state.update { it.copy(query = query, isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(query)
        }
    }

    /** Clears the query and results. */
    fun clear() {
        searchJob?.cancel()
        _state.update { SearchState() }
    }

    /**
     * Shows [text] (the chosen destination's address) in the field without launching a search and
     * clears the dropdown — so after a selection the field reads as the selected address.
     */
    fun showSelected(text: String) {
        searchJob?.cancel()
        _state.update { it.copy(query = text, suggestions = emptyList(), isLoading = false, error = null) }
    }

    private fun runSearch(query: String) {
        // Note: cancelling searchJob only prevents the debounce delay from elapsing — it
        // cannot abort an autoComplete request already dispatched here. The stale-query
        // guard in the callback is what discards an out-of-order response.
        val bias = sdk.gpsController.lastKnownPoint() ?: SdkConfig.DEFAULT_MAP_CENTER
        val filter = OnlineSearchFilter(language = Locale.getDefault().language, position = bias)

        sdk.onlineGeoCoder.autoComplete(query, filter, object : SearchCallback {
            override fun onSuggestions(suggestions: List<AutocompleteResult>, error: Error?) {
                // Ignore a late response for a query the user has since changed.
                if (_state.value.query != query) return
                _state.update {
                    if (error != null) {
                        it.copy(isLoading = false, error = error.readableMessage())
                    } else {
                        // Drop category/chain results that have no routable coordinate.
                        it.copy(isLoading = false, error = null, suggestions = suggestions.filter { s -> s.coordinate != null })
                    }
                }
            }
        })
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
