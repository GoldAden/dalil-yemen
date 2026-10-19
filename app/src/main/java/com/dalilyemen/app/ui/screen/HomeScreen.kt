package com.dalilyemen.app.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dalilyemen.app.R
import com.dalilyemen.app.ui.LocationState
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.component.CategoryGrid
import com.dalilyemen.app.ui.component.EmptyState
import com.dalilyemen.app.ui.component.PlaceCard
import com.dalilyemen.app.ui.component.SectionHeader
import com.dalilyemen.app.ui.component.SearchOffIcon

/**
 * الشاشة الرئيسية: البحث، الأقسام، والأماكن القريبة والمقترحة.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSearch: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val featured by viewModel.featured.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // --- الترويسة والبحث ---
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(14.dp))

                // حقل بحث للقراءة فقط يفتح شاشة البحث عند اللمس
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenSearch) {
                    Text(stringResource(R.string.search_title))
                }
            }
        }

        // --- الأقسام ---
        item { SectionHeader(stringResource(R.string.section_categories)) }
        item { CategoryGrid(onCategoryClick = onOpenCategory) }
        item { Spacer(Modifier.height(8.dp)) }

        // --- الأماكن القريبة ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(0.dp))
                SectionHeader(
                    title = stringResource(R.string.section_nearby),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }

        item {
            NearbyHeader(
                locationState = locationState,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                onRetry = { viewModel.loadNearby() },
            )
        }

        when {
            nearby.isLoading -> item {
                EmptyState(
                    title = stringResource(R.string.loading),
                    body = "",
                )
            }

            nearby.places.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.state_empty_title),
                    body = stringResource(R.string.state_empty_body),
                    icon = SearchOffIcon,
                )
            }

            else -> items(nearby.places, key = { it.id }) { place ->
                PlaceCard(
                    place = place,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    onClick = { onOpenPlace(place.id) },
                )
            }
        }

        // --- المقترحة ---
        item {
            SectionHeader(
                title = stringResource(R.string.section_featured),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (featured.places.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.state_empty_title),
                    body = stringResource(R.string.state_empty_body),
                )
            }
        } else {
            items(featured.places, key = { "f_" + it.id }) { place ->
                PlaceCard(
                    place = place,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    onClick = { onOpenPlace(place.id) },
                )
            }
        }
    }
}

/**
 * شريط حالة الموقع.
 *
 * يجسّد التعامل الصحيح مع كل الحالات: لم يُطلب الإذن، مرفوض، خدمة مغلقة،
 * أو تعذّر الحصول على الموقع — مع زرّ مناسب لكل حالة دون تعطيل بقية الشاشة.
 */
@Composable
private fun NearbyHeader(
    locationState: LocationState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        when (locationState) {
            LocationState.UNKNOWN, LocationState.DENIED -> {
                Text(
                    text = stringResource(R.string.location_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.location_allow))
                }
            }

            LocationState.SERVICE_OFF -> {
                Text(
                    text = stringResource(R.string.location_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }

            LocationState.UNAVAILABLE -> {
                Text(
                    text = stringResource(R.string.location_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }

            LocationState.GRANTED -> {
                Text(
                    text = stringResource(R.string.location_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
