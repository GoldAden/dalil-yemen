package com.dalilyemen.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalilyemen.app.R
import com.dalilyemen.app.data.model.YemenCities
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.component.EmptyState
import com.dalilyemen.app.ui.component.HomeCategories
import com.dalilyemen.app.ui.component.PlaceCard
import com.dalilyemen.app.ui.component.SearchOffIcon

/**
 * شاشة البحث مع التصفية حسب المدينة والتصنيف.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.search.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            // --- تصفية المدن ---
            Text(
                text = stringResource(R.string.filter_city),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCityId == null,
                        onClick = { viewModel.setSearchCity(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(YemenCities.all, key = { it.id }) { city ->
                    FilterChip(
                        selected = state.selectedCityId == city.id,
                        onClick = { viewModel.setSearchCity(city.id) },
                        label = { Text(city.nameAr) },
                    )
                }
            }

            // --- تصفية التصنيفات ---
            Text(
                text = stringResource(R.string.filter_category),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { viewModel.setSearchCategory(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(HomeCategories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.setSearchCategory(category.id) },
                        label = { Text(stringResource(category.titleRes)) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.runSearch() }) {
                    Text(stringResource(R.string.filter_apply))
                }
                TextButton(onClick = { viewModel.resetSearchFilters() }) {
                    Text(stringResource(R.string.filter_reset))
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                state.results.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            title = if (state.hasSearched) {
                                stringResource(R.string.search_no_results)
                            } else {
                                stringResource(R.string.state_empty_title)
                            },
                            body = when {
                                // عند تعذّر الوصول إلى المصدر نوضّح السبب بدل
                                // ادّعاء أن البحث لم يجد شيئاً.
                                state.errorMessage != null -> state.errorMessage.orEmpty()
                                state.hasSearched -> ""
                                else -> stringResource(R.string.search_empty)
                            },
                            icon = SearchOffIcon,
                            actionLabel = if (state.errorMessage != null) {
                                stringResource(R.string.action_retry)
                            } else {
                                null
                            },
                            onAction = if (state.errorMessage != null) {
                                { viewModel.runSearch() }
                            } else {
                                null
                            },
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.errorMessage != null) {
                        item {
                            Text(
                                text = state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.id }) { place ->
                        PlaceCard(
                            place = place,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { onOpenPlace(place.id) },
                        )
                    }
                }
            }
        }
    }
}
