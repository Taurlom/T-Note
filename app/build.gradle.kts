import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Ключ подписи релизов: keystore.properties в корне (в .gitignore).
// Без него release собирается неподписанным — так может работать CI-fork
// или свежий клон без секретов.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.example.timemanager"
    compileSdk = 34

    defaultConfig {
        // Идентификатор в магазинах и на устройстве. Отличается от namespace:
        // код остаётся в com.example.timemanager, а пакет публикации —
        // ru.taurlom.tnote (префикс com.example зарезервирован Google Play).
        // Смена пакета = новая установка: перенос данных через резервную копию.
        applicationId = "ru.taurlom.tnote"
        minSdk = 24
        targetSdk = 34
        // Версионирование: семантическое (см. CHANGELOG.md).
        // versionCode = MAJOR*100 + MINOR*10 + PATCH — растёт монотонно,
        // синхронно с versionName при каждом релизе.
        versionCode = 211
        versionName = "1.11.1"

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
    kotlinOptions {
        jvmTarget = "17"
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
            isMinifyEnabled = false
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
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    // Per-app language: AppCompatDelegate.setApplicationLocales работает и
    // на Android < 13 (там, где нет системного LocaleManager).
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // material-icons-extended (Material Icons) устарел: все иконки проекта —
    // vector drawables из набора Material Symbols в res/drawable (ic_*.xml).

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-compiler:2.54")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    // Настоящий org.json в JVM-тестах (в android.jar он — заглушки).
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
