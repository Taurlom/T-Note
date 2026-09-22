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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

// Разделы переключаются направленным слайдом: какой бы ни была причина
// (свайп по экрану или тап по бару), новый раздел приезжает со стороны,
// где стоит относительно старого. Направление пишет switchTab, потому что
// у переходов NavHost в этой версии нет доступа к стартовому/конечному маршруту.
private val tabSwipeDurationMillis = 220

/** 1 — новый раздел правее, -1 — левее, 0 — без слайда. */
private typealias TabDirection = Int

/** Возврат из drill-down (pop) остаётся мгновенным, как и раньше. */
private val tabNoEnter: EnterTransition = EnterTransition.None
private val tabNoExit: ExitTransition = ExitTransition.None

private fun tabSwipeEnter(direction: TabDirection): EnterTransition = when (direction) {
    1 -> slideInHorizontally(tween(tabSwipeDurationMillis)) { it } +
        fadeIn(tween(tabSwipeDurationMillis))
    -1 -> slideInHorizontally(tween(tabSwipeDurationMillis)) { -it } +
        fadeIn(tween(tabSwipeDurationMillis))
    else -> EnterTransition.None
}

private fun tabSwipeExit(direction: TabDirection): ExitTransition = when (direction) {
    1 -> slideOutHorizontally(tween(tabSwipeDurationMillis)) { -it } +
        fadeOut(tween(tabSwipeDurationMillis))
    -1 -> slideOutHorizontally(tween(tabSwipeDurationMillis)) { it } +
        fadeOut(tween(tabSwipeDurationMillis))
    else -> ExitTransition.None
}

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
 * Тот же [switchTab] вызывает горизонтальный свайп по контенту: соседний раздел
 * берётся из порядка пунктов бара.
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

    // Направление последнего переключения разделов — читают его анимации tabScreen.
    val tabDirection = remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Дистанция, после которой свайп считается переключением раздела.
        val swipeDistancePx = with(LocalDensity.current) { 96.dp.toPx() }
        NavHost(
            navController = navController,
            startDestination = Routes.CATEGORIES,
            modifier = Modifier
                .weight(1f)
                .pointerInput(selectedTab) {
                    // Горизонтальный свайп доступен только на разделах:
                    // на вложенных экранах (список задач, документ) он не нужен.
                    if (selectedTab == null) return@pointerInput
                    var draggedPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { draggedPx = 0f },
                        onDragCancel = { draggedPx = 0f },
                        onDragEnd = {
                            val items = BottomNavItem.items
                            val index = items.indexOfFirst {
                                it.route == navController.currentDestination?.route
                            }
                            if (index >= 0) {
                                // Свайп влево (палец идёт к началу строки) — следующий раздел.
                                val target = when {
                                    draggedPx < -swipeDistancePx -> items.getOrNull(index + 1)
                                    draggedPx > swipeDistancePx -> items.getOrNull(index - 1)
                                    else -> null
                                }
                                target?.let { navController.switchTab(it, tabDirection) }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        draggedPx += dragAmount
                    }
                }
        ) {
            tabScreen(Routes.CATEGORIES, tabDirection) {
                CategoriesScreen(
                    onCategoryClick = { categoryId ->
                        navController.navigate(Routes.tasks(categoryId))
                    },
                    viewModel = categoriesViewModel
                )
            }
            tabScreen(Routes.CALENDAR, tabDirection) {
                CalendarScreen(viewModel = calendarViewModel)
            }
            tabScreen(Routes.DOCUMENTS, tabDirection) {
                DocumentsScreen(
                    onDocumentClick = { documentId ->
                        navController.navigate(Routes.documentDetail(documentId))
                    },
                    viewModel = documentsViewModel
                )
            }
            tabScreen(Routes.SETTINGS, tabDirection) {
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
                onItemSelected = { item -> navController.switchTab(item, tabDirection) }
            )
        }
    }
}

/** Пункт NavBar: переход между разделами — направленный слайд, возврат — мгновенный. */
private fun NavGraphBuilder.tabScreen(
    route: String,
    tabDirection: MutableIntState,
    content: @Composable () -> Unit
) {
    composable(
        route = route,
        enterTransition = { tabSwipeEnter(tabDirection.value) },
        exitTransition = { tabSwipeExit(tabDirection.value) },
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
 * Перед переходом пишем [tabDirection]: если новый раздел правее старого — 1,
 * левее — -1. Откуда именно пришёл пользователь (бар или свайп), значения не имеет.
 */
private fun NavHostController.switchTab(item: BottomNavItem, tabDirection: MutableIntState) {
    val currentRoute = currentDestination?.route
    if (currentRoute == item.route) return

    val items = BottomNavItem.items
    val fromIndex = items.indexOfFirst { it.route == currentRoute }
    val toIndex = items.indexOf(item)
    tabDirection.intValue = if (fromIndex >= 0 && toIndex >= 0) {
        if (toIndex > fromIndex) 1 else -1
    } else {
        0
    }

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
