package com.example.timemanager.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timemanager.presentation.screens.calendar.CalendarScreen
import com.example.timemanager.presentation.screens.categories.CategoriesScreen
import com.example.timemanager.presentation.screens.documents.DocumentDetailScreen
import com.example.timemanager.presentation.screens.documents.DocumentsScreen
import com.example.timemanager.presentation.screens.settings.SettingsScreen
import com.example.timemanager.presentation.screens.tasks.TasksScreen

object Routes {
    const val CATEGORIES = "categories"
    const val TASKS = "tasks/{categoryId}"
    const val CALENDAR = "calendar"
    const val DOCUMENTS = "documents"
    const val DOCUMENT_DETAIL = "documentDetail/{documentId}"
    const val SETTINGS = "settings"

    fun tasks(categoryId: Long): String = "tasks/$categoryId"
    fun documentDetail(documentId: Long): String = "documentDetail/$documentId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CATEGORIES) {
        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.tasks(categoryId))
                },
                onNavigateToCalendar = {
                    navController.navigate(Routes.CALENDAR)
                },
                onNavigateToDocuments = {
                    navController.navigate(Routes.DOCUMENTS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
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
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToDocuments = {
                    navController.navigate(Routes.DOCUMENTS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        composable(Routes.DOCUMENTS) {
            DocumentsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToCalendar = {
                    navController.navigate(Routes.CALENDAR)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onDocumentClick = { documentId ->
                    navController.navigate(Routes.documentDetail(documentId))
                }
            )
        }
        composable(
            route = Routes.DOCUMENT_DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) {
            DocumentDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToCalendar = {
                    navController.navigate(Routes.CALENDAR)
                },
                onNavigateToDocuments = {
                    navController.navigate(Routes.DOCUMENTS)
                }
            )
        }
    }
}
