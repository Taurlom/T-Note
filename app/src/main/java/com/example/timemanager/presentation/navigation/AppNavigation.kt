package com.example.timemanager.presentation.navigation

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.timemanager.presentation.components.BottomNavBar
import com.example.timemanager.presentation.components.BottomNavItem
import com.example.timemanager.presentation.screens.calendar.CalendarScreen
import com.example.timemanager.presentation.screens.calendar.CalendarViewModel
import com.example.timemanager.presentation.screens.categories.CategoriesScreen
import com.example.timemanager.presentation.screens.categories.CategoriesViewModel
import com.example.timemanager.presentation.screens.documents.DocumentDetailScreen
import com.example.timemanager.presentation.screens.documents.DocumentsScreen
import com.example.timemanager.presentation.screens.documents.DocumentsViewModel
import com.example.timemanager.presentation.screens.settings.SettingsScreen
import com.example.timemanager.presentation.screens.settings.SettingsViewModel
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

// Разделы переключаются без анимации маршрута: экран держит своё состояние,
// так что анимировать при переключении нечего.
private val tabNoEnter: EnterTransition = EnterTransition.None
private val tabNoExit: ExitTransition = ExitTransition.None

// Для вложенных экранов остаётся короткий slide+fade.
private val detailEnter: EnterTransition =
    fadeIn(tween(150)) + slideInHorizontally(tween(220)) { it / 6 }
private val detailExit: ExitTransition =
    fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { it / 6 }
private val detailPopEnter: EnterTransition =
    fadeIn(tween(150)) + slideInHorizontally(tween(220)) { -it / 6 }
private val detailPopExit: ExitTransition =
    fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { -it / 6 }

/**
 * Навигация приложения: один [BottomNavBar] поверх [NavHost].
 *
 * Разделы переключаются одним пунктом стека ([switchTab]) с сохранением состояния,
 * а их ViewModel живут в скоупе активности — поэтому данные раздела загружаются
 * один раз и остаются в памяти при любом количестве переключений.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // На первом кадре маршрут ещё не определён — считаем активным стартовый раздел,
    // иначе бар мигал бы отсутствием при запуске. Для вложенных маршрутов
    // fromRoute возвращает null, и бар скрывается.
    val selectedTab = if (currentRoute == null) {
        BottomNavItem.Categories
    } else {
        BottomNavItem.fromRoute(currentRoute)
    }

    val tabViewModelStoreOwner: ViewModelStoreOwner = LocalContext.current
        .viewModelStoreOwnerOrNull()
        ?: error("AppNavigation должен вызываться из Activity (MainActivity)")

    // Создаются все четыре ViewModel сразу: каждый раздел подписан на свою
    // Room-выборку и держит её открытой. Первый переход в раздел больше не
    // тратит время на создание ViewModel и запрос к базе.
    val categoriesViewModel: CategoriesViewModel = hiltViewModel(tabViewModelStoreOwner)
    val calendarViewModel: CalendarViewModel = hiltViewModel(tabViewModelStoreOwner)
    val documentsViewModel: DocumentsViewModel = hiltViewModel(tabViewModelStoreOwner)
    val settingsViewModel: SettingsViewModel = hiltViewModel(tabViewModelStoreOwner)

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.CATEGORIES,
            modifier = Modifier.weight(1f)
        ) {
            tabScreen(Routes.CATEGORIES) {
                CategoriesScreen(
                    onCategoryClick = { categoryId ->
                        navController.navigate(Routes.tasks(categoryId))
                    },
                    viewModel = categoriesViewModel
                )
            }
            tabScreen(Routes.CALENDAR) {
                CalendarScreen(viewModel = calendarViewModel)
            }
            tabScreen(Routes.DOCUMENTS) {
                DocumentsScreen(
                    onDocumentClick = { documentId ->
                        navController.navigate(Routes.documentDetail(documentId))
                    },
                    viewModel = documentsViewModel
                )
            }
            tabScreen(Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = Routes.TASKS,
                arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
                enterTransition = { detailEnter },
                exitTransition = { detailExit },
                popEnterTransition = { detailPopEnter },
                popExitTransition = { detailPopExit }
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong("categoryId")
                if (categoryId != null) {
                    TasksScreen(
                        categoryId = categoryId,
                        onBackClick = { navController.popBackStack() }
                    )
                } else {
                    // Аргумент обязателен в маршруте: вместо пустого экрана откатываемся назад.
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(
                route = Routes.DOCUMENT_DETAIL,
                arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
                enterTransition = { detailEnter },
                exitTransition = { detailExit },
                popEnterTransition = { detailPopEnter },
                popExitTransition = { detailPopExit }
            ) { backStackEntry ->
                if (backStackEntry.arguments?.getLong("documentId") != null) {
                    DocumentDetailScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
        }

        // Бар виден на разделах; вложенные экраны (список задач, документ) —
        // это drill-down, а не пункт навигации.
        if (selectedTab != null) {
            BottomNavBar(
                selectedItem = selectedTab,
                onItemSelected = { item -> navController.switchTab(item) }
            )
        }
    }
}

/** Пункт NavBar без анимации перехода между разделами. */
private fun NavGraphBuilder.tabScreen(
    route: String,
    content: @Composable () -> Unit
) {
    composable(
        route = route,
        enterTransition = { tabNoEnter },
        exitTransition = { tabNoExit },
        popEnterTransition = { tabNoEnter },
        popExitTransition = { tabNoExit },
        content = { content() }
    )
}

/**
 * Переключение раздела: заменяем текущий пункт, а не кладём новый поверх.
 *
 * `launchSingleTop` не даёт плодить дубликаты маршрута, `popUpTo(...) { saveState }`
 * + `restoreState` сохраняют скролл и состояние раздела и держат стек плоским.
 */
private fun NavHostController.switchTab(item: BottomNavItem) {
    if (currentDestination?.route == item.route) return

    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Достаёт [ViewModelStoreOwner] из контекста: Compose может отдать обёртку
 * вроде ContextThemeWrapper, а нужен именно Activity.
 */
private tailrec fun Context.viewModelStoreOwnerOrNull(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.viewModelStoreOwnerOrNull()
    else -> null
}
