package ru.taurlom.tnote.presentation.navigation

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.SharedText
import ru.taurlom.tnote.presentation.components.BottomNavBar
import ru.taurlom.tnote.presentation.components.BottomNavItem
import ru.taurlom.tnote.presentation.components.SectionsDialog
import ru.taurlom.tnote.presentation.components.SharedListImportDialog
import ru.taurlom.tnote.presentation.components.SharedTextSaveDialog
import ru.taurlom.tnote.presentation.screens.calendar.CalendarScreen
import ru.taurlom.tnote.presentation.screens.calendar.CalendarViewModel
import ru.taurlom.tnote.presentation.screens.categories.ArchiveScreen
import ru.taurlom.tnote.presentation.screens.categories.CategoriesEvent
import ru.taurlom.tnote.presentation.screens.categories.CategoriesScreen
import ru.taurlom.tnote.presentation.screens.categories.CategoriesViewModel
import ru.taurlom.tnote.presentation.screens.categories.ShareFeedback
import ru.taurlom.tnote.presentation.screens.documents.DocumentDetailScreen
import ru.taurlom.tnote.presentation.screens.documents.DocumentsScreen
import ru.taurlom.tnote.presentation.screens.documents.DocumentsViewModel
import ru.taurlom.tnote.presentation.screens.notes.NoteDetailScreen
import ru.taurlom.tnote.presentation.screens.notes.NotesEvent
import ru.taurlom.tnote.presentation.screens.notes.NotesFeedback
import ru.taurlom.tnote.presentation.screens.notes.NotesScreen
import ru.taurlom.tnote.presentation.screens.notes.NotesViewModel
import ru.taurlom.tnote.presentation.screens.settings.SettingsScreen
import ru.taurlom.tnote.presentation.screens.settings.SettingsViewModel
import ru.taurlom.tnote.presentation.screens.tasks.TasksScreen

object Routes {
    const val MAIN = "main"
    const val TASKS = "tasks/{categoryId}"
    const val DOCUMENT_DETAIL = "documentDetail/{documentId}"
    const val NOTE_DETAIL = "noteDetail/{noteId}"
    const val ARCHIVE = "archive"

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
 *
 * [pendingOpenCalendar] — тап по уведомлению-сводке: вернуться из
 * drill-down к разделам и открыть «Календарь».
 */
@Composable
fun AppNavigation(
    pendingShareUri: Uri? = null,
    onPendingShareUriHandled: () -> Unit = {},
    pendingSharedText: SharedText? = null,
    onPendingSharedTextHandled: () -> Unit = {},
    pendingOpenCalendar: Boolean = false,
    onPendingOpenCalendarHandled: () -> Unit = {},
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
    val notesState by notesViewModel.uiState.collectAsStateWithLifecycle()
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

    // Текст из «Поделиться» — тем же маршрутом: диалог над любым экраном,
    // интент гасится сразу, повторная доставка не перезапустит флоу.
    LaunchedEffect(pendingSharedText) {
        if (pendingSharedText != null) {
            notesViewModel.onEvent(NotesEvent.OnSharedTextReceived(pendingSharedText))
            onPendingSharedTextHandled()
        }
    }

    // Тап по уведомлению-сводке: возвращаемся из drill-down к разделам
    // (если ушли) и листаем пейджер на «Календарь». Запрос передаём
    // счётчиком, а не флагом: повторный тап при уже открытом календаре
    // должен сработать снова, а флаг бы «застрял» включённым.
    var openCalendarRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingOpenCalendar) {
        if (pendingOpenCalendar) {
            openCalendarRequest++
            navController.popBackStack(Routes.MAIN, inclusive = false)
            onPendingOpenCalendarHandled()
        }
    }

    LaunchedEffect(categoriesState.shareFeedback) {
        when (val feedback = categoriesState.shareFeedback) {
            is ShareFeedback.Imported -> Toast.makeText(
                context,
                context.getString(R.string.shared_list_added, feedback.listName),
                Toast.LENGTH_SHORT,
            ).show()
            ShareFeedback.Failed -> Toast.makeText(
                context,
                R.string.shared_list_error,
                Toast.LENGTH_LONG,
            ).show()
            null -> return@LaunchedEffect
        }
        categoriesViewModel.onEvent(CategoriesEvent.OnShareFeedbackShown)
    }

    // Тост о сохранении текстового шэра — как у импорта списка.
    LaunchedEffect(notesState.saveFeedback) {
        when (val feedback = notesState.saveFeedback) {
            is NotesFeedback.Saved -> Toast.makeText(
                context,
                context.getString(R.string.shared_text_added, feedback.noteTitle),
                Toast.LENGTH_SHORT,
            ).show()
            NotesFeedback.Failed -> Toast.makeText(
                context,
                R.string.shared_text_error,
                Toast.LENGTH_LONG,
            ).show()
            null -> return@LaunchedEffect
        }
        notesViewModel.onEvent(NotesEvent.OnSharedTextFeedbackShown)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(
            route = Routes.MAIN,
            enterTransition = { mainNoEnter },
            exitTransition = { mainNoExit },
            popEnterTransition = { mainNoEnter },
            popExitTransition = { mainNoExit },
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
                },
                onArchiveClick = {
                    navController.navigate(Routes.ARCHIVE)
                },
                openCalendarRequest = openCalendarRequest,
            )
        }

        composable(
            route = Routes.TASKS,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit },
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId")
            if (categoryId != null) {
                TasksScreen(
                    categoryId = categoryId,
                    onBackClick = { navController.popBackStack() },
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
            popExitTransition = { detailPopExit },
        ) { backStackEntry ->
            if (backStackEntry.arguments?.getLong("documentId") != null) {
                DocumentDetailScreen(
                    onBackClick = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
        composable(
            route = Routes.ARCHIVE,
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit },
        ) {
            ArchiveScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.NOTE_DETAIL,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            enterTransition = { detailEnter },
            exitTransition = { detailExit },
            popEnterTransition = { detailPopEnter },
            popExitTransition = { detailPopExit },
        ) { backStackEntry ->
            if (backStackEntry.arguments?.getLong("noteId") != null) {
                NoteDetailScreen(
                    onBackClick = { navController.popBackStack() },
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
            },
        )
    }

    // Диалог сохранения текста — по той же причине: «Поделиться» приходит
    // поверх любого экрана приложения.
    notesState.incomingSharedText?.let { shared ->
        SharedTextSaveDialog(
            shared = shared,
            onConfirm = {
                notesViewModel.onEvent(NotesEvent.OnConfirmSaveSharedText)
            },
            onDismiss = {
                notesViewModel.onEvent(NotesEvent.OnDismissSaveSharedText)
            },
        )
    }

    // «Настройка разделов» — тоже поверх NavHost: при скрытии раздела
    // пейджер перестраивает страницы, и диалог внутри SettingsScreen
    // закрывался бы вместе с пересозданной страницей.
    if (sectionsDialogOpen) {
        SectionsDialog(
            visibleSections = settingsState.visibleSections,
            onDismiss = settingsViewModel::closeSectionsDialog,
            onApply = settingsViewModel::updateSections,
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
    onNoteClick: (Long) -> Unit,
    onArchiveClick: () -> Unit,
    openCalendarRequest: Int = 0,
) {
    // Запасной путь импорта: выбор .tnote-файла вручную — мессенджеры не
    // всегда отдают наш MIME при «Открыть с помощью». Ланчер живёт здесь, а
    // не в разделе: бренд-шапка с кнопкой импорта достаётся первому по
    // настройке разделу, каким бы он ни оказался. Диалог подтверждения
    // показывает AppNavigation.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
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

    // Тап по уведомлению-сводке: плавно доезжаем до «Календаря», как при
    // тапе по нижней панели. Раздел может быть скрыт пользователем — тогда
    // тихо остаёмся на текущем: настраивать видимость из уведомления нельзя.
    LaunchedEffect(openCalendarRequest) {
        if (openCalendarRequest > 0) {
            val target = items.indexOfFirst { it is BottomNavItem.Calendar }
            if (target >= 0 && pagerState.currentPage != target) {
                scrollScope.launch { pagerState.animateScrollToPage(target) }
            }
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
            pageSpacing = 0.dp,
        ) { page ->
            when (items[page]) {
                BottomNavItem.Categories -> CategoriesScreen(
                    onCategoryClick = onCategoryClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    onArchiveClick = onArchiveClick,
                    viewModel = categoriesViewModel,
                )
                BottomNavItem.Calendar -> CalendarScreen(
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = calendarViewModel,
                )
                BottomNavItem.Documents -> DocumentsScreen(
                    onDocumentClick = onDocumentClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = documentsViewModel,
                )
                BottomNavItem.Notes -> NotesScreen(
                    onNoteClick = onNoteClick,
                    showBrandHeader = page == 0,
                    onImportLists = onImportLists,
                    viewModel = notesViewModel,
                )
                BottomNavItem.Settings -> SettingsScreen(viewModel = settingsViewModel)
            }
        }

        BottomNavBar(
            items = items,
            selectedItem = items.getOrNull(pagerState.currentPage),
            onItemSelected = { item ->
                scrollScope.launch { pagerState.animateScrollToPage(items.indexOf(item)) }
            },
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
