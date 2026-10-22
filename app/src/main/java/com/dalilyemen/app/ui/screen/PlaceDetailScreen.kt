package com.dalilyemen.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dalilyemen.app.R
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.component.EmptyState
import com.dalilyemen.app.ui.component.kindLabel

/**
 * شاشة تفاصيل المحل: الاسم، الوصف، الهاتف، العنوان، ساعات العمل،
 * الموقع، والصور عند توفّرها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    viewModel: MainViewModel,
    placeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var place by remember { mutableStateOf<Place?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    LaunchedEffect(placeId) {
        place = viewModel.placeById(placeId)
        loaded = true
    }

    val isFavorite = favoriteIds.contains(placeId)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_description)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // زرّ المفضلة متاح دائماً في الشريط العلوي.
                    IconButton(onClick = { viewModel.toggleFavorite(placeId) }) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                            ),
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
            )
        },
    ) { inner ->
        val current = place

        when {
            !loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.loading)) }

            current == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                EmptyState(
                    title = stringResource(R.string.detail_unavailable),
                    body = stringResource(R.string.state_empty_body),
                )
            }

            else -> PlaceDetailContent(
                place = current,
                isFavorite = isFavorite,
                onToggleFavorite = { viewModel.toggleFavorite(current.id) },
                contentPadding = inner,
            )
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: Place,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = 32.dp,
        ),
    ) {
        // --- الصور ---
        item {
            if (place.photoUrls.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(place.photoUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = stringResource(R.string.cd_place_image),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.detail_no_photos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // --- الاسم ---
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = place.name.ifBlank { stringResource(R.string.detail_unavailable) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!place.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = place.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // --- معلومات أساسية ---
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!place.kindValue.isNullOrBlank()) {
                    InfoRow(
                        icon = Icons.Outlined.Category,
                        label = stringResource(R.string.detail_kind),
                        value = kindLabel(place.kindValue),
                    )
                }
                InfoRow(
                    icon = Icons.Outlined.Call,
                    label = stringResource(R.string.detail_phone),
                    value = place.phone,
                )
                InfoRow(
                    icon = Icons.Outlined.LocationOn,
                    label = stringResource(R.string.detail_address),
                    value = place.address,
                )
                InfoRow(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.detail_hours),
                    value = place.openingHours,
                )
            }
        }

        // --- تنبيه الموقع التقريبي ---
        if (place.coordinatesApproximate) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.detail_approximate_location),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        // --- مصدر البيانات ---
        if (place.isShared) {
            item {
                Text(
                    text = stringResource(R.string.source_shared_user),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else if (place.addedByUser) {
            item {
                Text(
                    text = stringResource(R.string.source_user_added),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // --- المفضلة ---
        item {
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                    )
                )
            }
        }

        // --- الأزرار ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        val phone = place.phone
                        if (!phone.isNullOrBlank()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }
                    },
                    enabled = !place.phone.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.detail_call))
                }

                OutlinedButton(
                    onClick = {
                        val lat = place.latitude
                        val lon = place.longitude
                        if (lat != null && lon != null) {
                            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    },
                    enabled = place.latitude != null && place.longitude != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.detail_open_map))
                }
            }
        }

        // --- مشاركة ---
        item {
            OutlinedButton(
                onClick = {
                    val text = buildString {
                        append(place.name)
                        if (!place.address.isNullOrBlank()) append("\n").append(place.address)
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.detail_share))
            }
        }

        // --- إسناد المصدر ---
        item {
            Text(
                text = stringResource(R.string.attribution_osm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Text(
                text = value?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.detail_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
