# richeditor-compose (вендоренный модуль)

Копия библиотеки [com.mohamedrejeb.richeditor:richeditor-compose] 1.2.1
(репозиторий MohamedRejeb/compose-rich-editor, коммит `d0eb001`, 2026-09-30 —
он же артефакт 1.2.1 на Maven Central) **с фиксом выравнивания списков
в режиме просмотра** (апстрим-PR #853, не смержен на момент вендоринга).

## Зачем вендорить

Баг: в read-only просмотре (`BasicRichText`) пункты списков съезжают —
часть маркеров уезжает к левому краю, а отступ меняется от пункта к пункту;
в редакторе всё ровно. Причина — библиотека измеряет ширину префикса
(`«• »`) по фактической разметке после первого layout, часть измерений
оказывается бессмысленной (префикс попадает меж строк) и фиксируется
в состоянии навсегда (`TextIndent.firstLine = 0`).

Фикс из PR #853 (адаптирован под 1.2.1):

- `RichTextConfig.kt` — константа `StartTextWidthSentinel`;
- `RichTextState.kt` — `measureStartTextWidths()` (предзамер префиксов
  через `TextMeasurer` до первого кадра) + guard в
  `adjustRichParagraphLayout()`: пропуск замера, если начало и конец
  префикса приходятся на разные строки;
- `BasicRichText.kt` — вызов предзамера перед первым чтением
  `annotatedString`.

Проверено на desktop-тестах самого репозитория библиотеки: на чистой
базе тесты PR падают, с патчем проходят; полный desktop-набор
(883 теста) зелёный.

## Отличия от апстрима при простом копировании

Модуль урезан до Android-only сборки. При переносе новых изменений
из апстрима нужно повторить:

- исходники `commonMain` и `androidMain` объединены в один
  `src/main/kotlin`;
- 4 пары expect/actual инлайнены в общие файлы:
  - `clipboard/RichTextClipboardManager.kt` — `createRichTextClipboardManager`
    возвращает `AndroidRichTextClipboardManager` сразу;
  - `clipboard/ClipboardEventEffect.kt` — no-op-реализация;
  - `platform/Platform.kt` — `currentPlatform = Platform.Android`;
  - `ui/BasicRichTextEditor.kt` — `adjustTextIndicatorOffset(...): Modifier = this`;
  - файлы-actual из `androidMain` удалены (кроме `AndroidRichTextClipboardManager.kt`);
- тесты (desktop/js/android) не перенесены — повторяются в апстриме;
- `explicitApi` снят, публикация (`module.publication`) не нужна.

## Когда снять вендоринг

После мержа PR #853 и выпуска релиза ≥ фикса: вернуть зависимость
`libs.richeditor.compose` в `app/build.gradle.kts`, модуль удалить,
каталог-версии почистить. Пока PR не смержен — обновлять вендоренную
копию осознанно, прогоняя тесты репозитория библиотеки.
