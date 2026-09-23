# T-Note

Персональный планировщик для Android: списки задач, календарь событий
и база документов с фотографиями — полностью офлайн, без аккаунтов
и сети.

## Возможности

**Списки** — категории с цветами; задачи с описанием, отметкой выполнения,
перетаскиванием и копированием в другой список.

**Календарь** — заметки на день; события четырёх типов: обычные
(иконка + цвет), дни рождения (повторяются ежегодно), повторяющиеся
(интервал «раз в N дней», длительность, скрытие прошедших) и «Выходной»
(подсветка дня как Сб/Вс, видна и в соседних месяцах). Навигация:
свайп влево/вправо между разделами, свайп вверх/вниз по календарю —
между месяцами.

**Документы** — карточки с описанием и фотографиями (до 4): галерея
с зумом, поворотом и кадрированием; съёмка камерой; порядок списка
настраивается перетаскиванием.

**Настройки** — 5 цветовых тем (Тёмная, Светлая, Океан, Лес, Осень) и 10 шрифтов,
применяются мгновенно.

## Технологии

| | |
|---|---|
| Язык | Kotlin 2.1 |
| UI | Jetpack Compose, Material 3 (BOM 2025.02) |
| Архитектура | MVVM + Clean Architecture (domain / data / presentation) |
| Хранилище | Room (миграции), DataStore Preferences |
| DI | Hilt + KSP/kapt |
| Навигация | Navigation Compose + HorizontalPager для разделов |
| Изображения | Coil |
| Мин. Android | 24 (target 34) |

## Структура

```
app/src/main/java/com/example/timemanager/
├── data/            # Room (БД, DAO, entity), мапперы, реализация репозиториев
├── di/              # модули Hilt
├── domain/          # модели, интерфейсы репозиториев, use-case
└── presentation/
    ├── components/  # дизайн-система: кнопки, поля, диалоги, карточки
    ├── navigation/  # NavHost + экран разделов (HorizontalPager)
    ├── screens/     # calendar / categories / tasks / documents / settings
    ├── splash/      # заставка
    └── theme/       # цвета-роли, темы, типографика
```

## Сборка

```bash
./gradlew :app:assembleDebug      # отладочный APK (подписан debug-ключом)
./gradlew :app:testDebugUnitTest  # юнит-тесты
./gradlew :app:assembleRelease    # релизный APK
```

Для подписанного релизного APK нужны файлы вне репозитория
(уже в `.gitignore`): `keystore.properties` в корне и сам keystore.
Формат `keystore.properties`:

```properties
storeFile=signing/timemanager-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Без них `assembleRelease` собирает неподписанный APK.

## Релизы

Каждый релиз — аннотированный тег `vX.Y.Z`. Проверка локально:

1. обновить `versionName`/`versionCode` в `app/build.gradle.kts`;
2. добавить секцию в [CHANGELOG.md](CHANGELOG.md);
3. закоммитить и смержить в `main`.

Дальше:

```bash
git tag -a vX.Y.Z -m "T-Note vX.Y.Z"
git push origin main vX.Y.Z
```

Push тега запускает GitHub Actions ([.github/workflows/release.yml](.github/workflows/release.yml)):
CI собирает подписанный APK (ключ и пароли — из secrets репозитория)
и публикует GitHub Release с файлом и описанием из changelog.

Каждый push в `main` и PR прогоняет компиляцию и тесты
([.github/workflows/ci.yml](.github/workflows/ci.yml)).

Готовые сборки: [releases](../../releases).

## Лицензия

Правообладатель — автор репозитория. Распространение без лицензии
(все права защищены), пока не указано иное.
