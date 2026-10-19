package com.dalilyemen.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dalilyemen.app.R
import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.Category
import androidx.compose.ui.text.style.TextOverflow

/** التصنيفات المعروضة في الشاشة الرئيسية. */
val HomeCategories: List<Category> = listOf(
    Category(Categories.RESTAURANTS, R.string.cat_restaurants),
    Category(Categories.HEALTH, R.string.cat_health),
    Category(Categories.FUEL, R.string.cat_fuel),
    Category(Categories.MAINTENANCE, R.string.cat_maintenance),
    Category(Categories.ELECTRONICS, R.string.cat_electronics),
    Category(Categories.STORES, R.string.cat_stores),
)

/** أيقونة كل تصنيف. */
fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    Categories.RESTAURANTS -> Icons.Outlined.Restaurant
    Categories.HEALTH -> Icons.Outlined.LocalHospital
    Categories.FUEL -> Icons.Outlined.LocalGasStation
    Categories.MAINTENANCE -> Icons.Outlined.DirectionsCar
    Categories.ELECTRONICS -> Icons.Outlined.Smartphone
    else -> Icons.Outlined.Storefront
}

/** عنوان عربي مقروء لأي تصنيف. */
@Composable
fun categoryTitle(categoryId: String): String = stringResource(
    when (categoryId) {
        Categories.RESTAURANTS -> R.string.cat_restaurants
        Categories.HEALTH -> R.string.cat_health
        Categories.FUEL -> R.string.cat_fuel
        Categories.MAINTENANCE -> R.string.cat_maintenance
        Categories.ELECTRONICS -> R.string.cat_electronics
        else -> R.string.cat_stores
    }
)

/**
 * شبكة أقسام من صفّين وعمودين منطقيين (3 عناصر في الصف).
 * الصفوف تُبنى يدوياً لتجنّب تعقيد الشبكة داخل التمرير العمودي.
 */
@Composable
fun CategoryGrid(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeCategories.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { category ->
                    CategoryTile(
                        category = category,
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(category.id) },
                    )
                }
                // حشو للحفاظ على تناسق الصف الأخير
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(category.id),
                    contentDescription = stringResource(R.string.cd_category_icon),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
