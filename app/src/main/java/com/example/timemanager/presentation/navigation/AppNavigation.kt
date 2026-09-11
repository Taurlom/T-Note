package com.example.timemanager.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timemanager.presentation.screens.categories.CategoriesScreen
import com.example.timemanager.presentation.screens.tasks.TasksScreen

object Routes {
    const val CATEGORIES = "categories"
    const val TASKS = "tasks/{categoryId}"

    fun tasks(categoryId: Long): String = "tasks/$categoryId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CATEGORIES) {
        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.tasks(categoryId))
                }
            )
        }
        composable(
            route = Routes.TASKS,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: return@composable
            TasksScreen(
                categoryId = categoryId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
