package ru.taurlom.tnote.presentation.screens.categories

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.SharedList

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    /** Список, прочитанный из `.tnote`-файла, ждёт подтверждения импорта. */
    val incomingShare: SharedList? = null,
    /** Разовое уведомление о результате (тост покажет AppNavigation). */
    val shareFeedback: ShareFeedback? = null
)

sealed interface ShareFeedback {
    data class Imported(val listName: String) : ShareFeedback
    data object Failed : ShareFeedback
}
