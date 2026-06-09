package com.benomad.sample.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benomad.msdk.geocoder.results.AutocompleteResult
import com.benomad.sample.R

/**
 * Search field + results dropdown for choosing a destination.
 *
 * A thin presentational component driven by [SearchState]; it has no SDK dependency itself. The
 * field is a bordered surface whose outline turns primary on focus (error-coloured on error); the
 * results list highlights the typed query within each suggestion. Tapping a result hands the chosen
 * [AutocompleteResult] (which carries the routable coordinate) back to the caller.
 */
@Composable
fun DestinationSearchBar(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onResultSelected: (AutocompleteResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    focused -> MaterialTheme.colorScheme.primary
                    state.error != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { innerTextField ->
                        if (state.query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_hint),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        innerTextField()
                    },
                )
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Results / status dropdown — only while the field is focused (it hides once a result is
        // picked, which clears focus).
        val hasResults = state.suggestions.isNotEmpty() || state.error != null || state.isLoading
        if (focused && hasResults) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .shadow(elevation = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
            ) {
                when {
                    state.error != null -> Text(
                        text = state.error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )

                    state.suggestions.isEmpty() && state.isLoading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        itemsIndexed(state.suggestions) { index, result ->
                            ResultRow(
                                text = result.addressLabel ?: result.place,
                                query = state.query,
                                onClick = { onResultSelected(result) },
                            )
                            if (index < state.suggestions.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** One result row: a location pin, then the suggestion text with the typed query bolded. */
@Composable
private fun ResultRow(text: String, query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(highlightQuery(text, query))
    }
}

/** Builds the suggestion text with the (case-insensitive) matched [query] substring in bold. */
private fun highlightQuery(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val start = if (query.isBlank()) -1 else text.indexOf(query, ignoreCase = true)
    if (start < 0) {
        append(text)
    } else {
        append(text.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start, start + query.length))
        }
        append(text.substring(start + query.length))
    }
}
