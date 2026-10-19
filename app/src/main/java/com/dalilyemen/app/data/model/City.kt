package com.dalilyemen.app.data.model

/**
 * مدينة/محافظة يمنية للتصفية.
 *
 * هذه أسماء جغرافية عامة معرّفة لتسهيل التصفية، وليست بيانات محلات.
 */
data class City(
    val id: String,
    val nameAr: String,
    val nameEn: String,
)

object YemenCities {
    val all: List<City> = listOf(
        City("sanaa", "صنعاء", "Sanaa"),
        City("aden", "عدن", "Aden"),
        City("taiz", "تعز", "Taiz"),
        City("hodeidah", "الحديدة", "Hodeidah"),
        City("mukalla", "المكلا", "Mukalla"),
        City("ibb", "إب", "Ibb"),
        City("dhamar", "ذمار", "Dhamar"),
        City("hajjah", "حجة", "Hajjah"),
        City("saada", "صعدة", "Saada"),
        City("marib", "مأرب", "Marib"),
        City("hadramout", "حضرموت", "Hadramout"),
        City("lahj", "لحج", "Lahj"),
        City("abyan", "أبين", "Abyan"),
        City("shabwah", "شبوة", "Shabwah"),
        City("albayda", "البيضاء", "Al Bayda"),
        City("aldhale", "الضالع", "Al Dhale"),
        City("aljawf", "الجوف", "Al Jawf"),
        City("amran", "عمران", "Amran"),
        City("almahwit", "المحويت", "Al Mahwit"),
        City("raymah", "ريمة", "Raymah"),
        City("socotra", "سقطرى", "Socotra"),
    )

    fun find(id: String?): City? = all.firstOrNull { it.id == id }
}
