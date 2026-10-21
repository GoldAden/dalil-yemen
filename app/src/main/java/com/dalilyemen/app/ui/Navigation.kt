package com.dalilyemen.app.ui

import android.net.Uri

/** مسارات التنقّل داخل التطبيق. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CATEGORY = "category/{categoryId}"
    const val PLACE_DETAIL = "place/{placeId}"
    const val ADD_PLACE = "add_place"

    fun category(categoryId: String): String = "category/$categoryId"
    fun placeDetail(placeId: String): String = "place/${Uri.encode(placeId)}"

    const val ARG_CATEGORY_ID = "categoryId"
    const val ARG_PLACE_ID = "placeId"
}
