package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.presentation.theme.AppTheme

@Composable
fun CategoryInputDialog(
    category: Category? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    // rememberSaveable: набранное имя и выбранный цвет переживают
    // пересоздание Activity (смена системной темы/языка/масштаба шрифта),
    // а не только пересборку композиции. Для редактирования это спасает
    // меньше — объект category в remember экрана при пересоздании
    // теряется и диалог закроется, — но создание списка защитой покрыто.
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var selectedColor by rememberSaveable { mutableLongStateOf(category?.color ?: Category.DEFAULT_COLOR) }

    AppDialog(
        title = stringResource(
            if (category == null) R.string.add_category else R.string.edit_category
        ),
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.category_name),
                    singleLine = true,
                    required = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.choose_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.dialogContent
                )
                AppColorPicker(
                    options = categoryColorOptions(),
                    selected = selectedColor,
                    onSelect = { selectedColor = it },
                    size = 40.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppSaveButton(
                onClick = { onConfirm(name, selectedColor) },
                enabled = name.isNotBlank()
            )
        }
    )
}

/**
 * Диалог задачи. При редактировании ([copyTargets] непустой и передан
 * [onCopyTo]) показывает блок «Копировать в»: выпадающий список других
 * списков и кнопку «Скопировать».
 */
@Composable
fun TaskInputDialog(
    titleInitial: String = "",
    descriptionInitial: String = "",
    dialogTitle: String,
    copyTargets: List<Category> = emptyList(),
    onCopyTo: ((Long, String, String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onNext: ((String, String) -> Unit)? = null
) {
    // Аналогично CategoryInputDialog: черновик задачи живёт в Bundle
    // и не теряется на пересоздании Activity.
    var title by rememberSaveable { mutableStateOf(titleInitial) }
    var description by rememberSaveable { mutableStateOf(descriptionInitial) }
    var targetCategoryId by rememberSaveable { mutableLongStateOf(0L) }
    val context = LocalContext.current

    AppDialog(
        title = dialogTitle,
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.task_title),
                singleLine = true,
                required = true,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.task_description),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (copyTargets.isNotEmpty() && onCopyTo != null) {
                AppDropdown(
                    label = stringResource(R.string.copy_to),
                    selectedLabel = copyTargets.find { it.id == targetCategoryId }?.name,
                    options = copyTargets,
                    optionText = { target ->
                        Text(
                            text = target.name,
                            color = AppTheme.colors.dialogContent
                        )
                    },
                    placeholder = stringResource(R.string.choose_list),
                    onSelect = { targetCategoryId = it.id }
                )
                AppButton(
                    onClick = {
                        val target = copyTargets.find { it.id == targetCategoryId }
                            ?: return@AppButton
                        onCopyTo(target.id, title, description)
                        Toast.makeText(
                            context,
                            context.getString(R.string.task_copied, target.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    // Выбор цели обязателен; пустую задачу копировать некуда.
                    enabled = targetCategoryId != 0L && title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.copy),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onNext != null) {
                    AppButton(
                        onClick = {
                            onNext(title, description)
                            title = ""
                            description = ""
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(stringResource(R.string.next))
                    }
                }
                AppSaveButton(
                    onClick = { onConfirm(title, description) },
                    enabled = title.isNotBlank()
                )
            }
        }
    )
}
