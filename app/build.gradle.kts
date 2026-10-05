import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Ключ подписи релизов: keystore.properties в корне (в .gitignore).
// Без него release собирается неподписанным — так может работать CI-fork
// или свежий клон без секретов.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

// Версионирование: семантическое (см. CHANGELOG.md). versionCode вычисляется
// из versionName: MAJOR*10000 + MINOR*100 + PATCH — растёт монотонно при любой
// смене версии и не регрессирует на переходе MAJOR (2.0.0 → 20000).
// Исторический минимум — 241: до 1.14.0 включительно действовала формула
// MAJOR*100 + MINOR*10 + PATCH (1.14.0 = 240 ушла в RuStore на модерацию).
val appVersionName = "1.14.4"
val appVersionCode = appVersionName.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

android {
    namespace = "com.example.timemanager"
    compileSdk = 36

    defaultConfig {
        // Идентификатор в магазинах и на устройстве. Отличается от namespace:
        // код остаётся в com.example.timemanager, а пакет публикации —
        // ru.taurlom.tnote (префикс com.example зарезервирован Google Play).
        // -PlegacyPackage собирает прежний пакет для тех, кто уже пользуется
        // приложением: им обновляться поверх, без миграции (см. CHANGELOG 1.11.1).
        applicationId =
            if (project.hasProperty("legacyPackage")) {
                "com.example.timemanager"
            } else {
                "ru.taurlom.tnote"
            }
        minSdk = 24
        targetSdk = 36
        // Версия задаётся один раз — в appVersionName/appVersionCode выше
        // (см. комментарий там про формулу и исторический минимум 241).
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric-тестам нужны ресурсы и assets на JVM-класспасе.
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // Схемы Room читаются MigrationTestHelper'ом как assets. Подключаем их
        // только в debug-вариант: unit-тесты (testDebugUnitTest) их видят,
        // а в релизный APK схемы не попадают вообще.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    // Подпись релизов: если keystore.properties нет — собираем без подписи
    // (debug всё равно подписан отладочным ключом и ставится на устройство).
    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Отладочные сборки подписываем релизным ключом: для Android
            // debug- и release-сборки становятся «одним приложением» и
            // обновляют друг друга без переустановки. Флаг -PkeepDebugSigning
            // оставляет обычный отладочный ключ — он нужен для переходного
            // debug-билда с экспортом резервных копий, который ставится поверх
            // уже установленных приложений, подписанных старым debug-ключом.
            if (!project.hasProperty("keepDebugSigning")) {
                signingConfigs.findByName("release")?.let { signingConfig = it }
            }
        }
        getByName("release") {
            // R8: выкидывает неиспользуемый код/ресурсы библиотек и обфусцирует.
            // mapping.txt для деобфускации стектрейсов прикладывается к релизу
            // на GitHub (см. release.yml).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    // Per-app language: AppCompatDelegate.setApplicationLocales работает и
    // на Android < 13 (там, где нет системного LocaleManager).
    implementation(libs.appcompat)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // material-icons-extended (Material Icons) устарел: все иконки проекта —
    // vector drawables из набора Material Symbols в res/drawable (ic_*.xml).

    implementation(libs.datastore.preferences)
    // Чтение/запись EXIF Orientation без декодирования всего JPEG: поворот
    // фото = правка одного тега, а не перекодирование пикселей.
    implementation(libs.exifinterface)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.core.splashscreen)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    // Настоящий org.json в JVM-тестах (в android.jar он — заглушки).
    testImplementation(libs.json)
    // Тесты миграций Room на JVM: Robolectric + MigrationTestHelper
    // (схемы из app/schemas подключены выше как assets тестового source set).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

ksp {
    // Экспорт схем Room в app/schemas: история схем хранится в git,
    // а сами JSON — основа для будущих тестов миграций (MigrationTestHelper).
    arg("room.schemaLocation", "$projectDir/schemas")
}
