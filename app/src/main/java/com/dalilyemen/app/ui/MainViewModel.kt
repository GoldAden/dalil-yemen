package com.dalilyemen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.repository.DalilRepositoryProvider
import com.dalilyemen.app.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** حالة الموقع كما تظهر في الواجهة. */
enum class LocationState { UNKNOWN, GRANTED, DENIED, UNAVAILABLE, SERVICE_OFF }

/** حالة قائمة المحلات. */
data class PlacesUiState(
    val isLoading: Boolean = false,
    val places: List<Place> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCityId: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<Place> = emptyList(),
    val hasSearched: Boolean = false,
    val selectedCityId: String? = null,
    val selectedCategoryId: String? = null,
)

/**
 * عقل الشاشات: يجمع البيانات من المستودع والموقع ويعرّضها كحالات
 * بسيطة تراقبها الواجهة.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DalilRepositoryProvider.places()
    private val locationProvider = LocationProvider(app)

    private val _locationState = MutableStateFlow(LocationState.UNKNOWN)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _userLocation = MutableStateFlow<LatLng?>(null)
    val userLocation: StateFlow<LatLng?> = _userLocation.asStateFlow()

    private val _nearby = MutableStateFlow(PlacesUiState())
    val nearby: StateFlow<PlacesUiState> = _nearby.asStateFlow()

    private val _featured = MutableStateFlow(PlacesUiState())
    val featured: StateFlow<PlacesUiState> = _featured.asStateFlow()

    private val _category = MutableStateFlow(PlacesUiState())
    val category: StateFlow<PlacesUiState> = _category.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    init {
        refreshHome()
        syncPermissionState()
    }

    /** يزامن حالة الإذن مع الواجهة دون طلب أي شيء من المستخدم. */
    fun syncPermissionState() {
        _locationState.value = when {
            !locationProvider.hasPermission() -> LocationState.DENIED
            !locationProvider.isLocationServiceEnabled() -> LocationState.SERVICE_OFF
            else -> LocationState.GRANTED
        }
    }

    /** يُستدعى بعد أن يوافق المستخدم على الإذن من نافذة النظام. */
    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            _locationState.value = LocationState.DENIED
            return
        }
        if (!locationProvider.isLocationServiceEnabled()) {
            _locationState.value = LocationState.SERVICE_OFF
            return
        }
        _locationState.value = LocationState.GRANTED
        loadNearby()
    }

    fun refreshHome() {
        viewModelScope.launch {
            _featured.update { it.copy(isLoading = true) }
            val list = runCatching { repository.getFeatured() }.getOrDefault(emptyList())
            _featured.update { it.copy(isLoading = false, places = list) }
        }
    }

    fun loadNearby() {
        viewModelScope.launch {
            _nearby.update { it.copy(isLoading = true) }
            val loc = locationProvider.lastKnownLatLng()
            _userLocation.value = loc
            if (loc == null) {
                _nearby.update { it.copy(isLoading = false, places = emptyList()) }
                if (_locationState.value == LocationState.GRANTED) {
                    _locationState.value = LocationState.UNAVAILABLE
                }
                return@launch
            }
            val list = runCatching {
                repository.getNearby(origin = loc)
            }.getOrDefault(emptyList())
            _nearby.update { it.copy(isLoading = false, places = list) }
        }
    }

    fun loadCategory(categoryId: String, cityId: String? = null) {
        viewModelScope.launch {
            _category.update {
                it.copy(isLoading = true, selectedCategoryId = categoryId, selectedCityId = cityId)
            }
            val list = runCatching {
                repository.getByCategory(categoryId = categoryId, cityId = cityId)
            }.getOrDefault(emptyList())
            _category.update {
                it.copy(isLoading = false, places = list, selectedCategoryId = categoryId, selectedCityId = cityId)
            }
        }
    }

    fun updateSearchQuery(value: String) {
        _search.update { it.copy(query = value) }
    }

    fun setSearchCity(cityId: String?) {
        _search.update { it.copy(selectedCityId = cityId) }
        runSearch()
    }

    fun setSearchCategory(categoryId: String?) {
        _search.update { it.copy(selectedCategoryId = categoryId) }
        runSearch()
    }

    fun resetSearchFilters() {
        _search.update { it.copy(selectedCityId = null, selectedCategoryId = null) }
        runSearch()
    }

    fun runSearch() {
        viewModelScope.launch {
            val state = _search.value
            _search.update { it.copy(isLoading = true, hasSearched = true) }
            val list = runCatching {
                repository.search(
                    query = state.query.trim(),
                    cityId = state.selectedCityId,
                    categoryId = state.selectedCategoryId,
                )
            }.getOrDefault(emptyList())
            _search.update { it.copy(isLoading = false, results = list) }
        }
    }

    /** يجيب تفاصيل محل واحد من المستودع (أو null إن لم يوجد). */
    suspend fun placeById(id: String): Place? = runCatching { repository.getById(id) }.getOrNull()
}
