package ru.taurlom.tnote.presentation.screens.categories

import android.net.Uri
import ru.taurlom.tnote.domain.model.Category

sealed class CategoriesEvent {
    data class OnCategoryClick(val categoryId: Long) : CategoriesEvent()
    data class OnAddCategory(val name: String, val color: Long) : CategoriesEvent()
    data class OnEditCategory(val category: Category) : CategoriesEvent()
    data class OnReorderCategories(val categories: List<Category>) : CategoriesEvent()

    /** Корзина в списке = переместить в архив (обратимо, снекбар «Отменить»). */
    data class OnArchiveCategory(val category: Category) : CategoriesEvent()

    /** Действие «Отменить» в снекбаре после архивации. */
    data class OnRestoreCategory(val category: Category) : CategoriesEvent()

    /** Прочитать `.tnote` (пришёл тапом по файлу или через выбор файла). */
    data class OnReadSharedList(val uri: Uri) : CategoriesEvent()
    data object OnConfirmImportSharedList : CategoriesEvent()
    data object OnDismissImportSharedList : CategoriesEvent()
    data object OnShareFeedbackShown : CategoriesEvent()
}
