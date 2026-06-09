package com.benomad.sample.search

import com.benomad.msdk.geocoder.results.AutocompleteResult

/**
 * UI state for the destination search.
 *
 * @property query current text in the search field.
 * @property suggestions autocomplete results that have a usable coordinate.
 * @property isLoading a request is in flight.
 * @property error a user-facing error message (no network, license, …), or null.
 */
data class SearchState(
    val query: String = "",
    val suggestions: List<AutocompleteResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
