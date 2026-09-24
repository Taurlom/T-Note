package com.example.timemanager.presentation.navigation

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timemanager.R
import com.example.timemanager.presentation.components.BottomNavBar
import com.example.timemanager.presentation.components.BottomNavItem
import com.example.timemanager.presentation.components.SharedListImportDialog
import com.example.timemanager.presentation.screens.calendar.CalendarScreen
import com.example.timemanager.presentation.screens.calendar.CalendarViewModel
import com.example.timemanager.presentation.screens.categories.CategoriesEvent
import com.example.timemanager.presentation.screens.categories.CategoriesScreen
import com.example.timemanager.presentation.screens.categories.CategoriesViewModel
import com.example.timemanager.presentation.screens.categories.ShareFeedback
import com.example.timemanager.presentation.screens.documents.DocumentDetailScreen
import com.example.timemanager.presentation.screens.documents.DocumentsScreen
import com.example.timemanager.presentation.screens.documents.DocumentsViewModel
import com.example.timemanager.presentation.screens.settings.SettingsScreen
import com.example.timemanager.presentation.screens.settings.SettingsViewModel
import com.example.timemanager.presentation.screens.tasks.TasksScreen
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val TASKS = "tasks/{categoryId}"
    const val DOCUMENT_DETAIL = "documentDetail/{documentId}"

    fun tasks(categoryId: Long): String = "tasks/$categoryId"
    fun documentDetail(documentId: Long): String = "documentDetail/$documentId"
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

// Главный экран разделов не анимируется: внутри него свитчи раздает
// HorizontalPager, а NavHost трогает только вход/выход drill-down экранов.
private val mainNoEnter: EnterTransition = EnterTransition.None
private val mainNoExit: ExitTransition = ExitTransition.None

/**
 * Навигация приложения: [NavHost] с единственным маршрутом разделов
 * ([MainTabsScreen]) поверх которого кладываются drill-down экраны
 * (список задач, карточка документа).
 *
 * ViewModel всех разделов живут в скоупе активности — данные раздела
 * загружаются один раз и остаются в памяти при любых переключениях и
 * погружениях вглубь.
 *
 * [pendingShareUri] — `.tnote`-файл, переданный в приложение тапом
 * («Открыть в T-Note»): читаем его, показываем диалог подтверждения импорта
 * поверх текущего экрана и гасим интент после обработки.
 */
@Composable
fun AppNavigation(
    pendingShareUri: Uri? = null,
    onPendingShareUriHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current

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

    val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pendingShareUri) {
        if (pendingShareUri != null) {
            categoriesViewModel.onEvent(CategoriesEvent.OnReadSharedList(pendingShareUri))
            onPendingShareUriHandled()
        }
    }

    LaunchedEffect(categoriesState.shareFeedback) {
        when (val feedback = categoriesState.shareFeedback) {
            is ShareFeedback.Imported -> Toast.makeText(
                context,
                context.getString(R.string.shared_list_added, feedback.listName),
                Toast.LENGTH_SHORT
            ).show()
            ShareFeedback.Failed -> Toast.makeText(
                context,
                R.string.shared_list_error,
                Toast.LENGTH_LONG
            ).show()
            null -> return@LaunchedEffect
        }
        categoriesViewModel.onEvent(CategoriesEvent.OnShareFeedbackShown)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            route = Routes.MAIN,
            enterTransition = { mainNoEnter },
            exitTransition = { mainNoExit },
            popEnterTransition = { mainNoEnter },
            popExitTransition = { mainNoExit }
        ) {
            MainTabsScreen(
                categoriesViewModel = categoriesViewModel,
                calendarViewModel = calendarViewModel,
                documentsViewModel = documentsViewModel,
                settingsViewModel = settingsViewModel,
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.tasks(categoryId))
                },
                onDocumentClick = { documentId ->
                    navController.navigate(Routes.documentDetail(documentId))
                }
            )
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

    // Диалог импорта — поверх NavHost: файл могут открыть из любого экрана.
    categoriesState.incomingShare?.let { shared ->
        SharedListImportDialog(
            shared = shared,
            onConfirm = {
                categoriesViewModel.onEvent(CategoriesEvent.OnConfirmImportSharedList)
            },
            onDismiss = {
                categoriesViewModel.onEvent(CategoriesEvent.OnDismissImportSharedList)
            }
        )
    }
}

/**
 * Четыре раздела в [HorizontalPager]: переключение свайпом ведёт страницу
 * за пальцем, тап по бару доезжает плавно (animateScrollToPage).
 *
 * Все страницы держим в композиции (beyondViewportPageCount): скролл
 * списков не теряется при переключениях, а навигация больше не участвует
 * в перелистывании разделов — стек остаётся плоским.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabsScreen(
    categoriesViewModel: CategoriesViewModel,
    calendarViewModel: CalendarViewModel,
    documentsViewModel: DocumentsViewModel,
    settingsViewModel: SettingsViewModel,
    onCategoryClick: (Long) -> Unit,
    onDocumentClick: (Long) -> Unit
) {
    val items = BottomNavItem.items
    // Страница не проставляется в маршруте: раздел — состояние экрана
    // разделов, а не стек навигации. rememberPagerState переживает
    // уход в drill-down и возврат через SavedStateRegistry навигации.
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scrollScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // Держим все страницы живыми: 4 раздела, цена небольшая,
            // зато скролл и локальное состояние списков не сбрасываются.
            beyondViewportPageCount = items.size - 1,
            // Жёсткие границы страницы без «щелей» между разделами.
            pageSpacing = 0.dp
        ) { page ->
            when (items[page]) {
                BottomNavItem.Categories -> CategoriesScreen(
                    onCategoryClick = onCategoryClick,
                    viewModel = categoriesViewModel
                )
                BottomNavItem.Calendar -> CalendarScreen(viewModel = calendarViewModel)
                BottomNavItem.Documents -> DocumentsScreen(
                    onDocumentClick = onDocumentClick,
                    viewModel = documentsViewModel
                )
                BottomNavItem.Settings -> SettingsScreen(viewModel = settingsViewModel)
            }
        }

        BottomNavBar(
            selectedItem = items[pagerState.currentPage],
            onItemSelected = { item ->
                scrollScope.launch { pagerState.animateScrollToPage(items.indexOf(item)) }
            }
        )
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
