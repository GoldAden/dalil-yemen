package com.dalilyemen.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalilyemen.app.R
import com.dalilyemen.app.data.model.YemenCities
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.component.EmptyState
import com.dalilyemen.app.ui.component.PlaceCard
import com.dalilyemen.app.ui.component.categoryTitle
import com.dalilyemen.app.ui.component.kindLabel

/** شاشة تصنيف واحد مع تصفية بالمدينة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    viewModel: MainViewModel,
    categoryId: String,
    onBack: () -> Unit,
    onOpenPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.category.collectAsStateWithLifecycle()
    var cityId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categoryId, cityId) {
        viewModel.loadCategory(categoryId = categoryId, cityId = cityId)
    }

    val title = categoryTitle(categoryId)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = cityId == null,
                        onClick = { cityId = null },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(YemenCities.all, key = { it.id }) { city ->
                    FilterChip(
                        selected = cityId == city.id,
                        onClick = { cityId = city.id },
                        label = { Text(city.nameAr) },
                    )
                }
            }

            if (state.places.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.state_empty_title),
                            body = state.errorMessage ?: stringResource(R.string.state_empty_body),
                            actionLabel = if (state.errorMessage != null) {
                                stringResource(R.string.action_retry)
                            } else {
                                null
                            },
                            onAction = if (state.errorMessage != null) {
                                { viewModel.loadCategory(categoryId = categoryId, cityId = cityId) }
                            } else {
                                null
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
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
                    items(state.places, key = { it.id }) { place ->
                        PlaceCard(
                            place = place,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            trailingHint = kindLabel(place.kindValue),
                            onClick = { onOpenPlace(place.id) },
                        )
                    }
                }
            }
        }
    }
}
