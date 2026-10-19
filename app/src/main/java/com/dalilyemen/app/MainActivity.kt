package com.dalilyemen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dalilyemen.app.ui.MainViewModel
import com.dalilyemen.app.ui.Routes
import com.dalilyemen.app.ui.screen.CategoryScreen
import com.dalilyemen.app.ui.screen.HomeScreen
import com.dalilyemen.app.ui.screen.PlaceDetailScreen
import com.dalilyemen.app.ui.screen.SearchScreen
import com.dalilyemen.app.ui.theme.DalilYemenTheme

/**
 * النشاط الرئيسي.
 *
 * يفرض اتجاه RTL على كامل الواجهة ليمنح تجربة عربية صحيحة، مع
 * الاكتفاء بـ LTR للنصوص الرقمية عند الحاجة داخل العناصر نفسها.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DalilYemenApp() }
    }
}

@Composable
fun DalilYemenApp() {
    DalilYemenTheme {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val viewModel: MainViewModel = viewModel()

                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenCategory = { id ->
                                navController.navigate(Routes.category(id))
                            },
                            onOpenPlace = { id ->
                                navController.navigate(Routes.placeDetail(id))
                            },
                        )
                    }

                    composable(Routes.SEARCH) {
                        SearchScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPlace = { id ->
                                navController.navigate(Routes.placeDetail(id))
                            },
                        )
                    }

                    composable(Routes.CATEGORY) { entry ->
                        val categoryId = entry.arguments?.getString(Routes.ARG_CATEGORY_ID).orEmpty()
                        CategoryScreen(
                            viewModel = viewModel,
                            categoryId = categoryId,
                            onBack = { navController.popBackStack() },
                            onOpenPlace = { id ->
                                navController.navigate(Routes.placeDetail(id))
                            },
                        )
                    }

                    composable(Routes.PLACE_DETAIL) { entry ->
                        val placeId = entry.arguments?.getString(Routes.ARG_PLACE_ID).orEmpty()
                        PlaceDetailScreen(
                            viewModel = viewModel,
                            placeId = placeId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
