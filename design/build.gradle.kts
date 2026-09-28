import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
}

// Дизайн приложения: тема (цвета, шрифты, `ThemeMode`) и общие compose-компоненты —
// шторка и её поля, переключатель вкладок, чипы, плитки, загрузка, статус-бар.
// Ни от `:domain`, ни от `:data`, ни от `:app` модуль не зависит и не должен: всё, что
// знает о закладках, инкубаторах или единицах измерения, остаётся в `:app`
// (`ScheduleTable`, `IncubatorTabParts`, `NumberFormat`). Компонент, которому
// понадобилась доменная модель, — не компонент дизайна, а часть экрана.
android {
    namespace = "ru.zaroslikov.incubator.design"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // `api`, а не `implementation`: Modifier, Color, TextStyle и Material-типы стоят в
    // публичных сигнатурах компонентов, и потребителю они нужны на classpath.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.material3)
    api(libs.androidx.ui.text.google.fonts)
    api(libs.androidx.material.icons.core)
    implementation(libs.androidx.core.ktx)
    // SheetDraft читает LocalActivity, чтобы отличить поворот от смерти процесса.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
}
