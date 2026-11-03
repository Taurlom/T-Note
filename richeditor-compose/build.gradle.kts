// Вендоренный модуль: richeditor-compose (WYSIWYG-редактор заметок).
//
// Источник: MohamedRejeb/compose-rich-editor, коммит d0eb001 — он же
// артефакт 1.2.1 с Maven Central, плюс фикс неровных отступов списков
// в режиме просмотра (апстрим-PR #853, не смержен; адаптирован под 1.2.1
// в RichTextConfig.kt / RichTextState.kt / BasicRichText.kt).
//
// Собрано как Android-only модуль (без KMP-таргетов): общие исходники
// commonMain и androidMain объединены в src/main/kotlin, 4 пары
// expect/actual инлайнены в общие файлы. При обновлении из апстрима
// эти вещи придётся повторить — подробности в README.md модуля.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mohamedrejeb.richeditor.compose"
    // 37: все Compose-артефакты проекта собраны против SDK 37
    // (см. комментарий compileSdk в app/build.gradle.kts).
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Вендоренный код не локализуем и не линтуется правилами проекта.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)

    // Разбор HTML и markdown в RichTextState.
    implementation(libs.ksoup.html)
    implementation(libs.ksoup.entities)
    implementation(libs.jetbrains.markdown)
}
