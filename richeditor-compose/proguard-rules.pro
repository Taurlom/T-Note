# Копия consumer-правил апстрим-модуля richeditor-compose:
# парсер ksoup построен на reflection — R8 иначе вырежет его.
-keep class com.mohamedrejeb.ksoup.html.** { *; }
