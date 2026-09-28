package com.example.timemanager.presentation.navigation

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.timemanager.presentation.components.SectionsDialog
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
import com.example.timemanager.presentation.screens.notes.NoteDetailScreen
import com.example.timemanager.presentation.screens.notes.NotesScreen
import com.example.timemanager.presentation.screens.notes.NotesViewModel
import com.example.timemanager.presentation.screens.settings.SettingsScreen
import com.example.timemanager.presentation.screens.settings.SettingsViewModel
import com.example.timemanager.presentation.screens.tasks.TasksScreen
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val TASKS = "tasks/{categoryId}"
    const val DOCUMENT_DETAIL = "documentDetail/{documentId}"
    const val NOTE_DETAIL = "noteDetail/{noteId}"

    fun tasks(categoryId: Long): String = "tasks/$categoryId"
    fun documentDetail(documentId: Long): String = "documentDetail/$documentId"
    fun noteDetail(noteId: Long): String = "noteDetail/$noteId"
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

    // Создаются все пять ViewModel сразу: каждый раздел подписан на свою
    // Room-выборку и держит её открытой. Первый переход в раздел больше не
    // тратит время на создание ViewModel и запрос к базе.
    val categoriesViewModel: CategoriesViewModel = hiltViewModel(tabViewModelStoreOwner)
    val calendarViewModel: CalendarViewModel = hiltViewModel(tabViewModelStoreOwner)
    val documentsViewModel: DocumentsViewModel = hiltViewModel(tabViewModelStoreOwner)
    val notesViewModel: NotesViewModel = hiltViewModel(tabViewModelStoreOwner)
    val settingsViewModel: SettingsViewModel = hiltViewModel(tabViewModelStoreOwner)

    val categoriesState by categoriesViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val sectionsDialogOpen by settingsViewModel.sectionsDialogOpen
        .collectAsStateWithLifecycle()

    // Разделы нижней панели и страниц пейджера: видимость и порядок
    // настраиваются в «Настройках» (SectionsDialog); «Настройки» — всегда в конце.
    val sectionItems = remember(settingsState.visibleSections) {
        BottomNavItem.itemsFor(settingsState.visibleSections)
    }

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
                items = sectionItems,
                sectionsLoaded = settingsState.sectionsLoaded,
                categoriesViewModel = categoriesViewModel,
                calendarViewModel = calendarViewModel,
                documentsViewModel = documentsViewModel,
                notesViewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                onCategoryClick = { categoryId ->
                    navController.navigate(Routes.tasks(categoryId))
                },
                onDocumentClick = { documentId ->
                    navController.navigate(Routes.documentDetail(documentId))
                },
                onNoteClick = { noteId ->
                    navController.navigate(Routes.noteDetail(noteId))
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
        composable(
            route = Routes.NOTE_DETAIL,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit }
        ) { backStackEntry ->
            if (backStackEntry.arguments?.getLong("noteId") != null) {
                NoteDetailScreen(
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

    // «Настройка разделов» — тоже поверх NavHost: при скрытии раздела
    // пейджер перестраивает страницы, и диалог внутри SettingsScreen
    // закрывался бы вместе с пересозданной страницей.
    if (sectionsDialogOpen) {
        SectionsDialog(
            visibleSections = settingsState.visibleSections,
            onDismiss = settingsViewModel::closeSectionsDialog,
            onApply = settingsViewModel::updateSections
        )
    }
}

/**
 * Пять разделов в [HorizontalPager]: переключение свайпом ведёт страницу
 * за пальцем, тап по бару доезжает плавно (animateScrollToPage).
 *
 * Все страницы держим в композиции (beyondViewportPageCount): скролл
 * списков не теряется при переключениях, а навигация больше не участвует
 * в перелистывании разделов — стек остаётся плоским.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabsScreen(
    items: List<BottomNavItem>,
    sectionsLoaded: Boolean,
    categoriesViewModel: CategoriesViewModel,
    calendarViewModel: CalendarViewModel,
    documentsViewModel: DocumentsViewModel,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    onCategoryClick: (Long) -> Unit,
    onDocumentClick: (Long) -> Unit,
    onNoteClick: (Long) -> Unit
) {
    // Запасной путь импорта: выбор .tnote-файла вручную — мессенджеры не
    // всегда отдают наш MIME при «Открыть с помощью». Ланчер живёт здесь, а
    // не в разделе: бренд-шапка с кнопкой импорта достаётся первому по
    // настройке разделу, каким бы он ни оказался. Диалог подтверждения
    // показывает AppNavigation.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { categoriesViewModel.onEvent(CategoriesEvent.OnReadSharedList(it)) }
    }
    val onImportLists = { importLauncher.launch(arrayOf("*/*")) }

    // Порядок разделов ещё не приехал из DataStore — показываем пустой фон
    // (поверх всё равно сплэш). Без этого пейджер на кадр стартует с
    // порядка по умолчанию, «запоминает» его текущим — и первый раздел
    // пользователя не открывается после загрузки настроек.
    if (!sectionsLoaded) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    // Страница не проставляется в маршруте: раздел — состояние экрана
    // разделов, а не стек навигации. rememberPagerState переживает
    // уход в drill-down и возврат через SavedStateRegistry навигации.
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scrollScope = rememberCoroutineScope()

    // Пейджер держит номер страницы, а вставка/удаление раздела сдвигает
    // номера: без отслеживания id «текущий раздел» под диалогом настройки
    // менялся сам (стоял на «Настройках» — оказался на «Документах»).
    var currentItemId by remember { mutableStateOf(items.first().id) }
    LaunchedEffect(pagerState.currentPage) {
        items.getOrNull(pagerState.currentPage)?.let { currentItemId = it.id }
    }
    LaunchedEffect(items) {
        val target = items.indexOfFirst { it.id == currentItemId }
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            // Держим все страницы живыми: 5 разделов, цена небольшая,
            // зато скролл и локальное состояние списков не сбрасываются.
            beyondViewportPageCount = items.size - 1,
            // Жёсткие границы страницы без «щелей» между разделами.
            pageSpacing = 0.dp
        ) { page ->
            when (items[page]) {
                BottomNavItem.Categories -> CategoriesScreen(
                    onCategoryClick = onCategoryClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = categoriesViewModel
                )
                BottomNavItem.Calendar -> CalendarScreen(
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = calendarViewModel
                )
                BottomNavItem.Documents -> DocumentsScreen(
                    onDocumentClick = onDocumentClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = documentsViewModel
                )
                BottomNavItem.Notes -> NotesScreen(
                    onNoteClick = onNoteClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = notesViewModel
                )
                BottomNavItem.Settings -> SettingsScreen(viewModel = settingsViewModel)
            }
        }

        BottomNavBar(
            items = items,
            selectedItem = items.getOrNull(pagerState.currentPage),
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
