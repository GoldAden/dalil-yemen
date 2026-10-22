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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dalilyemen.app.R
import com.dalilyemen.app.data.model.YemenCities
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.component.HomeCategories
import kotlinx.coroutines.launch

/**
 * نموذج إضافة محل.
 *
 * ما يكتبه المستخدم هنا يبقى على جهازه، ولا ننسخه أو نرفعه إلى أي جهة.
 * إن ترك المستخدم الموقع فارغاً نعتمد مركز مدينته ونُخبره أن الموقع تقريبي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(HomeCategories.first().id) }
    var cityId by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val nameRequiredMessage = stringResource(R.string.add_place_name_required)
    val savedMessage = stringResource(R.string.add_place_saved)
    val savedApproxMessage = stringResource(R.string.add_place_saved_approx)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_place_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.add_place_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // --- الاسم ---
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) showError = false
                    },
                    label = { Text(stringResource(R.string.add_place_name)) },
                    isError = showError,
                    singleLine = true,
                    supportingText = if (showError) {
                        { Text(nameRequiredMessage) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- التصنيف ---
            item {
                Text(
                    text = stringResource(R.string.add_place_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(HomeCategories, key = { it.id }) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(stringResource(category.titleRes)) },
                        )
                    }
                }
            }

            // --- المدينة ---
            item {
                Text(
                    text = stringResource(R.string.add_place_city),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
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
            }

            // --- الهاتف ---
            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.add_place_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- نوع النشاط ---
            item {
                OutlinedTextField(
                    value = kind,
                    onValueChange = { kind = it },
                    label = { Text(stringResource(R.string.add_place_type)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- العنوان ---
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.add_place_address)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- ساعات العمل ---
            item {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text(stringResource(R.string.add_place_hours)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- ملاحظات ---
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.add_place_notes)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // --- الحفظ والإلغاء ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                showError = true
                                return@Button
                            }
                            val ok = viewModel.addUserPlace(
                                name = name,
                                categoryId = categoryId,
                                cityId = cityId,
                                phone = phone,
                                address = address,
                                openingHours = hours,
                                notes = notes,
                                kindValue = kind,
                                latitude = null,
                                longitude = null,
                            )
                            val message = if (!ok) {
                                nameRequiredMessage
                            } else if (cityId != null) {
                                savedApproxMessage
                            } else {
                                savedMessage
                            }
                            scope.launch { snackbarHostState.showSnackbar(message) }
                            if (ok) onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.add_place_save))
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.add_place_cancel))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
