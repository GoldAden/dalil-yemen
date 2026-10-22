package com.dalilyemen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.repository.DalilRepositoryProvider
import com.dalilyemen.app.location.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** حالة الموقع كما تظهر في الواجهة. */
enum class LocationState { UNKNOWN, GRANTED, DENIED, UNAVAILABLE, SERVICE_OFF }

/**
 * عقل الشاشات: يجمع البيانات من المستودع والموقع ويعرّضها كحالات
 * بسيطة تراقبها الواجهة.
 *
 * سياسة الأخطاء: لا نُخفي الفشل ولا نُظهر تفاصيل تقنية. عند تعذّر الوصول
 * إلى مصدر البيانات نعرض رسالة عربية مفهومة، وإن كانت هناك بيانات محفوظة
 * نعرضها ونُنبّه المستخدم أنه يعمل دون اتصال.
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

    /** معرّفات المفضلة، مرآة للحالة المحفوظة على الجهاز. */
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    /** قائمة المفضلة بعد حلّ المعرّفات إلى سجلات كاملة، للعرض في الرئيسية. */
    private val _favorites = MutableStateFlow(PlacesUiState())
    val favorites: StateFlow<PlacesUiState> = _favorites.asStateFlow()

    init {
        _favoriteIds.value = runCatching { repository.favoriteIds() }.getOrDefault(emptySet())
        refreshHome()
        loadFavorites()
        syncPermissionState()
    }

    /** هل يوجد اتصال بالإنترنت الآن؟ */
    fun isOnline(): Boolean = runCatching { repository.isOnline() }.getOrDefault(false)

    /** هل توجد بيانات محفوظة تعمل دون إنترنت؟ */
    fun hasOfflineData(): Boolean = runCatching { repository.hasOfflineData() }.getOrDefault(false)

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
            _featured.update { it.copy(isLoading = true, errorMessage = null) }
            val outcome = runCatching { repository.getFeatured() }
            _featured.update {
                it.copy(
                    isLoading = false,
                    places = outcome.getOrDefault(emptyList()),
                    errorMessage = messageOf(outcome.exceptionOrNull(), fallbackKey = null)
                        .takeIf { outcome.isFailure },
                )
            }
            _favoriteIds.value = runCatching { repository.favoriteIds() }.getOrDefault(emptySet())
            loadFavorites()
        }
    }

    fun loadNearby() {
        viewModelScope.launch {
            _nearby.update { it.copy(isLoading = true, errorMessage = null) }
            val loc = locationProvider.lastKnownLatLng()
            _userLocation.value = loc
            if (loc == null) {
                _nearby.update { it.copy(isLoading = false, places = emptyList()) }
                if (_locationState.value == LocationState.GRANTED) {
                    _locationState.value = LocationState.UNAVAILABLE
                }
                return@launch
            }
            val outcome = runCatching { repository.getNearby(origin = loc) }
            _nearby.update {
                it.copy(
                    isLoading = false,
                    places = outcome.getOrDefault(emptyList()),
                    errorMessage = messageOf(outcome.exceptionOrNull(), fallbackKey = null)
                        .takeIf { outcome.isFailure },
                )
            }
        }
    }

    fun loadCategory(categoryId: String, cityId: String? = null) {
        viewModelScope.launch {
            _category.update {
                it.copy(
                    isLoading = true,
                    selectedCategoryId = categoryId,
                    selectedCityId = cityId,
                    errorMessage = null,
                )
            }
            val outcome = runCatching {
                repository.getByCategory(categoryId = categoryId, cityId = cityId)
            }
            _category.update {
                it.copy(
                    isLoading = false,
                    places = outcome.getOrDefault(emptyList()),
                    selectedCategoryId = categoryId,
                    selectedCityId = cityId,
                    errorMessage = messageOf(outcome.exceptionOrNull(), fallbackKey = null)
                        .takeIf { outcome.isFailure },
                )
            }
        }
    }

    /**
     * يُحدّث نص البحث.
     *
     * أي تغيير في النص يُبطل نتيجة البحث السابقة: نُصفّر «تم البحث» ونُخلي
     * النتائج، حتى لا تظهر رسالة «لا توجد نتائج» بينما المستخدم ما زال
     * يكتب استعلاماً جديداً لم يُنفَّذ بعد.
     */
    fun updateSearchQuery(value: String) {
        _search.update { SearchTransitions.queryChanged(it, value) }
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
            _search.update { SearchTransitions.started(it) }
            val outcome = runCatching {
                repository.search(
                    query = state.query.trim(),
                    cityId = state.selectedCityId,
                    categoryId = state.selectedCategoryId,
                )
            }
            _search.update {
                it.copy(
                    isLoading = false,
                    results = outcome.getOrDefault(emptyList()),
                    errorMessage = messageOf(outcome.exceptionOrNull(), fallbackKey = null)
                        .takeIf { outcome.isFailure },
                )
            }
        }
    }

    // ----------------------------- المفضلة -----------------------------

    /** يبدّل حالة المفضلة لمحل ويحدّث المرآة في الواجهة فوراً. */
    fun toggleFavorite(placeId: String) {
        val nowFavorite = runCatching { repository.toggleFavorite(placeId) }.getOrNull() ?: return
        _favoriteIds.update { current ->
            if (nowFavorite) current + placeId else current - placeId
        }
        // نُحدّث قائمة المفضلة المعروضة فوراً كي لا يبقى ما أُزيل ظاهراً.
        loadFavorites()
    }

    fun isFavorite(placeId: String): Boolean = _favoriteIds.value.contains(placeId)

    /** يحلّ معرّفات المفضلة إلى محلات كاملة لعرضها في قائمة. */
    suspend fun favoritePlaces(): List<Place> =
        _favoriteIds.value.mapNotNull { id -> placeById(id) }

    /**
     * يُحدّث قائمة المفضلة المعروضة في الشاشة الرئيسية.
     *
     * نستدعيه عند كل تغيّر في معرّفات المفضلة، فلا يبقى المحل الذي
     * أُزيل من المفضلة معروضاً، ولا يغيب ما أُضيف للتوّ.
     */
    fun loadFavorites() {
        viewModelScope.launch {
            _favorites.update { it.copy(isLoading = true) }
            val resolved = favoritePlaces()
            _favorites.update { it.copy(isLoading = false, places = resolved) }
        }
    }

    /**
     * إضافة محل من المستخدم.
     *
     * الإحداثيات: إن لم يُدخل المستخدم موقعاً دقيقاً نعتمد مركز المدينة
     * ونُعلن أن الموقع تقريبي — لا ننسب للمحل دقةً غير موجودة.
     */
    fun addUserPlace(
        name: String,
        categoryId: String,
        cityId: String?,
        phone: String?,
        address: String?,
        openingHours: String?,
        notes: String?,
        kindValue: String?,
        latitude: Double?,
        longitude: Double?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false

        val hasExactLocation = latitude != null && longitude != null
        val cityCenter = com.dalilyemen.app.data.model.CityGeo.centerOf(cityId)
        val lat = if (hasExactLocation) latitude else cityCenter?.latitude
        val lon = if (hasExactLocation) longitude else cityCenter?.longitude

        val place = Place(
            id = "user_" + System.currentTimeMillis().toString(36),
            name = cleanName,
            description = notes?.trim()?.takeIf { it.isNotBlank() },
            phone = phone?.trim()?.takeIf { it.isNotBlank() },
            address = address?.trim()?.takeIf { it.isNotBlank() },
            cityId = cityId,
            categoryId = categoryId.ifBlank { Categories.STORES },
            latitude = lat,
            longitude = lon,
            openingHours = openingHours?.trim()?.takeIf { it.isNotBlank() },
            // نوع النشاط كما كتبه المستخدم (مثال: صيدلية) — بلا اختلاق تصنيف.
            kindValue = kindValue?.trim()?.takeIf { it.isNotBlank() },
            coordinatesApproximate = !hasExactLocation,
            source = PlaceSource.USER,
        )

        val saved = runCatching { repository.addUserPlace(place) }.getOrNull() ?: return false
        _favoriteIds.value = runCatching { repository.favoriteIds() }.getOrDefault(emptySet())
        // نُعيد تحميل القوائم حتى يظهر المحل الجديد فوراً في الشاشة الرئيسية.
        refreshHome()
        loadNearby()
        return saved.id.isNotBlank()
    }

    /** يجيب تفاصيل محل واحد من المستودع (أو null إن لم يوجد). */
    suspend fun placeById(id: String): Place? = runCatching { repository.getById(id) }.getOrNull()

    /**
     * يحوّل أي خطأ إلى رسالة عربية قابلة للعرض.
     *
     * الأخطاء الشبكية تحمل رسالة عربية جاهزة من طبقة الاتصال، ونستخدمها
     * كما هي. أي خطأ آخر يُترجم إلى صياغة عامة أوضح للمستخدم.
     */
    private fun messageOf(error: Throwable?, @Suppress("UNUSED_PARAMETER") fallbackKey: String?): String? {
        if (error == null) return null
        // أخطاء الاتصال تحمل رسالة عربية جاهزة، فنعرضها كما هي.
        if (error is java.io.IOException) {
            val arabic = error.message?.takeIf { it.isNotBlank() }
            if (arabic != null) return arabic
        }
        // أي خطأ آخر: صياغة عامة واضحة بدل نص تقني لا يفيد المستخدم.
        return getApplication<Application>().getString(com.dalilyemen.app.R.string.error_generic)
    }
}
