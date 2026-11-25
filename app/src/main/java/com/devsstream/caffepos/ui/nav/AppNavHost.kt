package com.devsstream.caffepos.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devsstream.caffepos.ui.catalog.CatalogScreen
import com.devsstream.caffepos.ui.cart.CartScreen
import com.devsstream.caffepos.ui.orders.OrdersScreen
import com.devsstream.caffepos.ui.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Catalog : Screen("catalog")
    object Cart : Screen("cart")
    object Orders : Screen("orders")
    object Summary : Screen("summary")
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Catalog.route) {
        composable(Screen.Catalog.route) {
            CatalogScreen(
                onGoToCart = { navController.navigate(Screen.Cart.route) },
                onGoToOrders = { navController.navigate(Screen.Orders.route) },
                onGoToSummary = { navController.navigate(Screen.Summary.route) }
            )
        }
        composable(Screen.Cart.route) {
            CartScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Orders.route) {
            OrdersScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Summary.route) {
            SummaryScreen(onBack = { navController.popBackStack() })
        }
    }
}
