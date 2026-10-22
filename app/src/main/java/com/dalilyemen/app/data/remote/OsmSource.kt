package com.dalilyemen.app.data.remote

import com.dalilyemen.app.data.model.Categories

/**
 * تصفية واحدة داخل استعلام المصدر الجغرافي (مفتاح وسم + نمط القيمة).
 *
 * عندما تكون القيمة null نكتفي بوجود المفتاح نفسه، مثل: أي متجر مهما كان نوعه.
 */
data class TagFilter(val key: String, val valueRegex: String? = null) {

    /** صيغة التصفية كما يفهمها مزوّد الخدمة. */
    fun expression(): String =
        if (valueRegex.isNullOrBlank()) "[\"$key\"]" else "[\"$key\"~\"$valueRegex\"]"
}

/**
 * خريطة ربط تصنيفات التطبيق بوسوم OpenStreetMap الفعلية.
 *
 * كل تصنيف يقابل وسوماً قياسية متعارف عليها في OpenStreetMap، فلا نختلق
 * وسوماً ولا نستنتج بيانات غير مسجّلة في المصدر. التغطية هنا مقصودة لتشمل
 * كل ما يظهر فعلاً في بيانات اليمن: الفنادق والبنوك والمكاتب والأثاث
 * والقرطاسية والملابس وورش الصيانة … حتى لا تُهمَل أماكن حقيقية.
 */
object OsmSource {

    /** حدود اليمن التقريبية: جنوب، غرب، شمال، شرق. */
    const val YEMEN_BBOX: String = "12.00,42.00,19.20,54.80"

    /** أقصى نصف قطر مسموح به للبحث حول نقطة، بالكيلومترات. */
    const val MAX_RADIUS_KM: Double = 40.0

    /**
     * التصنيف الافتراضي عندما يحمل المكان وسم خدمة (shop/office…) دون
     * تصنيف أدق. الافتراضي هو تصنيف المتاجر والخدمات.
     */
    const val DEFAULT_CATEGORY: String = Categories.STORES

    /** ترتيب المطابقة: من الأدق إلى الأعمّ. */
    val allCategories: List<String> = listOf(
        Categories.RESTAURANTS,
        Categories.HEALTH,
        Categories.FUEL,
        Categories.MAINTENANCE,
        Categories.ELECTRONICS,
        Categories.STORES,
    )

    /**
     * قواعد المطابقة لكل تصنيف: المفتاح + نمط قيمة الوسم.
     *
     * الترتيب داخل القائمة يهم: القاعدة الأدق أولاً.
     */
    private val rules: Map<String, List<TagFilter>> = mapOf(
        Categories.RESTAURANTS to listOf(
            TagFilter("amenity", "^(restaurant|cafe|fast_food|food_court|ice_cream|internet_cafe|bar|pub)$"),
            TagFilter("shop", "^(bakery|pastry|confectionery|beverages|coffee|tea|deli|butcher|greengrocer|seafood|spices)$"),
        ),
        Categories.HEALTH to listOf(
            TagFilter("healthcare"),
            TagFilter("amenity", "^(pharmacy|hospital|clinic|doctors|dentist|veterinary|nursing_home)$"),
            TagFilter("shop", "^(chemist|medical_supply|optician|hearing_aids|herbalist|nutrition_supplements)$"),
        ),
        Categories.FUEL to listOf(
            TagFilter("amenity", "^(fuel|charging_station|car_wash)$"),
            TagFilter("shop", "^(gas|fuel)$"),
        ),
        Categories.MAINTENANCE to listOf(
            TagFilter("craft"),
            TagFilter(
                "shop",
                "^(car_repair|tyre|tyres|car_parts|motorcycle_repair|bicycle|doityourself|hardware|paint|trade|electrical|glaziery|plumber|locksmith|key_cutter|shoe_repair|car)$",
            ),
        ),
        Categories.ELECTRONICS to listOf(
            TagFilter(
                "shop",
                "^(mobile_phone|electronics|computer|hifi|appliance|telecommunication|radio|video|video_games|photo|copyshop)$",
            ),
            TagFilter("office", "^(telecommunication|it)$"),
        ),
        Categories.STORES to listOf(
            TagFilter("shop"),
            TagFilter("amenity"),
            TagFilter("office"),
            TagFilter("tourism"),
            TagFilter("leisure", "^(fitness_centre|sports_centre|swimming_pool|bowling_alley|amusement_arcade|dance)$"),
        ),
    )

    /** التصفية المستخدمة عند البحث الحيّ في المصدر. */
    fun filtersForCategory(categoryId: String): List<TagFilter> =
        rules[categoryId] ?: rules.getValue(Categories.STORES)

    private val compiled: Map<String, List<Pair<TagFilter, Regex>>> by lazy {
        allCategories.associateWith { category ->
            filtersForCategory(category).map { filter ->
                filter to Regex(filter.valueRegex ?: ".*")
            }
        }
    }

    /**
     * يستنتج تصنيف العنصر من وسومه الفعلية.
     *
     * الترتيب مهم: الصحة والمطاعم والوقود والصيانة والإلكترونيات أولاً،
     * ثم تصنيف المتاجر والخدمات كتصنيف عام يضمّ الفنادق والبنوك والمكاتب.
     */
    fun categoryForTags(tags: Map<String, String>): String? {
        for (category in allCategories) {
            val categoryRules = compiled[category] ?: continue
            for ((filter, regex) in categoryRules) {
                val value = tags[filter.key]?.takeIf { it.isNotBlank() } ?: continue
                if (regex.matches(value)) return category
            }
        }
        return null
    }
}

/**
 * فهرس الكلمات المفتاحية للبحث.
 *
 * المستخدم قد يكتب «صيدلية» أو "pharmacy" ويريد قائمة الصيدليات كلها،
 * لذا نترجم الكلمة إلى التصنيف المقابل بدل البحث عنها كنص حر فقط.
 */
object SearchSynonyms {

    private val synonyms: Map<String, String> = buildMap {
        // ملاحظة: نكتب في الخريطة مباشرة عبر `map` بدل استدعاء دالة محلية
        // باسم put، لأن ذلك كان يستدعي نفسها بلا نهاية.
        val map: MutableMap<String, String> = this

        fun bind(category: String, vararg words: String) {
            words.forEach { word ->
                val key = OsmText.normalize(word)
                if (key.isNotEmpty()) map[key] = category
            }
        }

        val r = Categories.RESTAURANTS
        bind(
            r,
            "مطعم", "مطاعم", "مقهى", "مقهي", "كافي", "كافيه", "كوفي", "قهوة", "فطور", "وجبات", "حلويات",
            "مخبزة", "مخبز", "فرن", "معجنات", "حلواني", "بوفية",
        )
        bind(r, "restaurant", "restaurants", "cafe", "coffee", "food", "fastfood", "pizza", "burger", "snack", "bakery")

        val h = Categories.HEALTH
        bind(
            h,
            "مستشفى", "مشفى", "مستوصف", "عيادة", "عيادات", "صيدلية", "صيدليات", "دكتور", "طبيب", "اطباء",
            "اسنان", "مختبر", "تحاليل", "تمريض", "صحة", "بيطري", "نظارات", "بصريات",
        )
        bind(h, "hospital", "clinic", "pharmacy", "pharmacies", "doctor", "dentist", "medical", "health", "laboratory", "lab", "medicine", "optician")

        val f = Categories.FUEL
        bind(f, "وقود", "بنزين", "ديزل", "محطة", "محطات", "محطة وقود", "غاز", "تعبئة", "مغسلة سيارات")
        bind(f, "fuel", "petrol", "gas", "gasoline", "diesel", "petrolstation", "charging", "carwash")

        val m = Categories.MAINTENANCE
        bind(
            m,
            "ورشة", "ورش", "صيانة", "تصليح", "ميكانيكي", "كهرباء سيارات", "بنشر", "اطارات", "كوشوك",
            "قطع غيار", "حداد", "نجار", "سباك", "كهربائي", "ادوات", "عدة", "مفاتيح", "خياط",
        )
        bind(m, "repair", "garage", "mechanic", "workshop", "tyre", "tire", "maintenance", "spare", "parts", "carpenter", "plumber", "hardware", "blacksmith")

        val e = Categories.ELECTRONICS
        bind(
            e,
            "جوال", "جوالات", "موبايل", "هاتف", "هواتف", "الكترونيات", "إلكترونيات", "كمبيوتر", "لابتوب",
            "شاشة", "ثلاجة", "غسالة", "تكييف", "تصوير", "استوديو",
        )
        bind(e, "mobile", "phone", "electronics", "computer", "laptop", "telecom", "appliance", "smartphone", "copyshop")

        val s = Categories.STORES
        bind(
            s,
            "سوق", "أسواق", "اسواق", "بازار", "بقالة", "سوبرماركت", "متجر", "متاجر", "محل", "محلات",
            "ملابس", "ذهب", "مجوهرات", "خضار", "فواكه", "حلاق", "بوفية", "مول", "فندق", "فنادق", "بنك",
            "بنوك", "صرافة", "بريد", "قرطاسية", "اثاث", "أثاث", "مكتبة", "عطور", "هدايا", "سفر وسياحة", "صالة", "نادي",
        )
        bind(s, "market", "mall", "supermarket", "grocery", "shop", "store", "clothes", "gold", "jewelry", "shoes", "boutique", "hotel", "bank", "exchange", "post", "stationery", "furniture", "book", "perfumery", "gift", "travel", "gym", "fitness")
    }

    /** كل كلمات البحث التي تقود إلى تصنيف. */
    fun wordsFor(category: String): List<String> =
        synonyms.filterValues { it == category }.keys.toList()

    /**
     * يحوّل نص البحث إلى تصنيف إن كان المستخدم يقصد تصنيفاً معروفاً.
     * يعيد null إذا كان النص يبدو اسماً لمحل معيّن، فيُبحث عنه كنص حر.
     */
    fun categoryForQuery(query: String): String? {
        val normalized = OsmText.normalize(query)
        if (normalized.isEmpty()) return null
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // عبارة كاملة أولاً: «محطة وقود» و«قطع غيار» …
        if (tokens.size > 1) {
            val joined = tokens.joinToString(" ")
            synonyms[joined]?.let { return it }
        }

        // كلمة واحدة فقط: نتجنّب ترجمة اسم محل مكوّن من كلمتين إلى تصنيف.
        if (tokens.size != 1) return null
        val token = tokens.first()
        val withoutPrefix = token.removePrefix("ال")
        val candidates = linkedSetOf(
            token,
            withoutPrefix,
            token.removeSuffix("ات"),
            withoutPrefix.removeSuffix("ات"),
        )
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            synonyms[candidate]?.let { return it }
        }
        return null
    }
}

/** أدوات معالجة النصوص العربية قبل المقارنة. */
object OsmText {

    private val diacritics = Regex("[\\u064B-\\u0652\\u0670\\u0640\\u06D6-\\u06ED]")

    /**
     * توحيد كتابة النص العربي: إزالة التشكيل والتطويل،
     * وتوحيد الألف والياء والتاء المربوطة، وحروف صغيرة للإنجليزية.
     */
    fun normalize(raw: String): String {
        var text = raw.trim().lowercase()
        text = diacritics.replace(text, "")
        text = text
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ئ', 'ي').replace('ؤ', 'و')
            .replace('ة', 'ه')
        return text.replace(Regex("\\s+"), " ")
    }
}
