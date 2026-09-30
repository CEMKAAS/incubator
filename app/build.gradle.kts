import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// Импорт, а не полное имя: в Kotlin DSL `java` — это расширение Java-плагина проекта,
// и `java.util.Properties()` разбирается как обращение к нему.
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/**
 * Идентификатор проекта пушей RuStore — тот, что уезжает в `RuStorePushClient.init`.
 *
 * Берётся сначала из `local.properties`, и только потом из обычного свойства Gradle
 * (`gradle.properties` или `-PrustorePushProjectId=...`). Порядок именно такой, потому
 * что `local.properties` не лежит в git: идентификатор проекта — не секрет вроде ключа
 * подписи, но и в публичной истории репозитория ему делать нечего, а держать его рядом
 * с `sdk.dir` — привычное для Android место для «настроек этой машины».
 *
 * Пустая строка — рабочее состояние, а не недосмотр: `RuStorePush.init` на ней молча
 * не поднимает SDK, и приложение ведёт себя ровно так же, как до появления пушей. Иначе
 * любой, кто склонировал репозиторий, получал бы падение на первом же запуске.
 */
val rustorePushProjectId: String = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties()
            .apply { local.inputStream().use { stream -> load(stream) } }
            .getProperty("rustorePushProjectId")
    } else {
        null
    }
    (fromLocal ?: project.findProperty("rustorePushProjectId") as? String).orEmpty().trim()
}

/**
 * Свойство сборки: сначала `local.properties`, потом обычное свойство Gradle — тот же
 * порядок и по той же причине, что у [rustorePushProjectId].
 */
fun buildInput(name: String): String {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties()
            .apply { local.inputStream().use { stream -> load(stream) } }
            .getProperty(name)
    } else {
        null
    }
    return (fromLocal ?: project.findProperty(name) as? String).orEmpty().trim()
}

/**
 * Приложение в кабинете VK ID (id.vk.com): идентификатор, защищённый ключ и хост
 * адреса возврата. Ключ не секрет в строгом смысле — VK ID для мобильных приложений
 * всё равно проверяет подпись APK, — но и в публичной истории репозитория ему не место.
 *
 * Пустой идентификатор — рабочее состояние, как пустой проект пушей: SDK не
 * поднимается, кнопок VK на экране профиля нет, профиль заполняется только вручную.
 * Плейсхолдеры манифеста при этом всё равно нужны — манифест SDK на них ссылается, и
 * без значений сборка не пройдёт, — поэтому подставляется заглушка `0`.
 */
val vkidClientId: String = buildInput("vkidClientId")
val vkidClientSecret: String = buildInput("vkidClientSecret")
val vkidRedirectHost: String = buildInput("vkidRedirectHost").ifEmpty { "vk.ru" }

/**
 * Адрес сервера аккаунтов — вход по почте и паролю (контракт API — в CLAUDE.md, «Account»). Как у VK: пусто — рабочее
 * состояние, карточка «Аккаунт» не рисуется. В релизе только https: cleartext разрешён
 * лишь отладочной сборке и лишь для эмулятора (`src/debug/res/xml/network_security_config.xml`),
 * так что `http://10.0.2.2:8080` — адрес для локального сервера с эмулятора.
 */
val accountServerUrl: String = buildInput("accountServerUrl")

android {
    namespace = "ru.zaroslikov.incubator"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.zaroslikov.incubator"
        minSdk = 26
        targetSdk = 37
        // versionCode поднимается с каждым выпуском вместе с versionName: по нему
        // реклама при запуске отличает первый запуск после обновления от обычного
        // (AppSettings.adsVersionCode), и неподнятый код молча сломал бы это правило.
        versionCode = 3
        versionName = "1.1.0rНуаж"

        // Дата выпуска этой версии — её показывает экран «О приложении». Отдельным
        // полем, потому что взять её больше неоткуда: у APK есть время сборки, но оно
        // меняется на каждой пересборке и датой выпуска не является. Обновляется
        // руками вместе с versionName — на то и стоит рядом с ним.
        buildConfigField("String", "RELEASE_DATE", "\"10.09.2026\"")

        // Идентификатор проекта пушей RuStore. Пустой — пуши не поднимаются вовсе;
        // где он берётся и почему пустота допустима, написано у `rustorePushProjectId`
        // выше. Полем сборки, а не строкой в коде, ровно затем, чтобы его можно было
        // держать вне git.
        buildConfigField("String", "RUSTORE_PUSH_PROJECT_ID", "\"$rustorePushProjectId\"")

        // VK ID: пустой идентификатор выключает вход через VK целиком, см. `vkidClientId`.
        // В коде нужен только сам факт настройки — остальное SDK читает из манифеста.
        buildConfigField("String", "VKID_CLIENT_ID", "\"$vkidClientId\"")
        buildConfigField("String", "ACCOUNT_SERVER_URL", "\"$accountServerUrl\"")
        val vkidId = vkidClientId.ifEmpty { "0" }
        manifestPlaceholders["VKIDClientID"] = vkidId
        manifestPlaceholders["VKIDClientSecret"] = vkidClientSecret.ifEmpty { "0" }
        manifestPlaceholders["VKIDRedirectHost"] = vkidRedirectHost
        manifestPlaceholders["VKIDRedirectScheme"] = "vk$vkidId"
        manifestPlaceholders["vkidEnabled"] = vkidClientId.isNotEmpty().toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // Минификация включена, и вместе с ней обязателен `proguard-rules.pro`: без
            // правил R8 переименовывает то, что приложение ищет по имени, — воркер
            // напоминаний (WorkManager хранит имя его класса строкой в своей базе),
            // агент резервной копии из манифеста и константы `ThemeMode`, чьё имя лежит
            // в настройках. Всё это ломается только в релизе и только в рантайме.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Нужен ради versionName и RELEASE_DATE на экране «О приложении»: иначе
        // BuildConfig не генерируется вовсе и версию пришлось бы дублировать строкой
        // в коде — то есть однажды забыть обновить.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":design"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    // Только в отладочной сборке: это инструмент разработчика — инспектор разметки и
    // инструментирование композиции, — и в релизе он тянет за собой лишние классы и
    // метаданные, не давая пользователю ничего.
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.ui.text.google.fonts)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Ради LifecycleResumeEffect: экран настроек обязан перечитать разрешение на
    // уведомления, когда человек вернулся из системных настроек. Артефакт лежал в графе
    // только ограничением версии (`(c)`), на classpath его не было.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)


    implementation(libs.androidx.work.runtime.ktx)

    // AppMetrica
    implementation(libs.appmetrica)

    // Реклама. Всё, что импортирует com.yandex.mobile.ads, лежит в пакете ads/ — как
    // AppMetrica в analytics/. lifecycle-process нужен только ей: реклама при запуске
    // показывается по выходу *процесса* на передний план, а не по onStart активности —
    // тот срабатывает и на поворот экрана.
    implementation(libs.mobileads)
    implementation(libs.androidx.lifecycle.process)

    // RuStore: обновление, оценка и пуши. Всё, что импортирует ru.rustore.sdk, лежит
    // в пакете rustore/ — как AppMetrica в analytics/, а реклама в ads/.
    implementation(libs.rustore.appupdate)
    implementation(libs.rustore.review)
    implementation(libs.rustore.pushclient)

    // QR-код инкубатора: генерация и сканер. ZXing — чистая Java, и генерация с
    // распознаванием проверяются JVM-тестом без эмулятора; CameraX даёт кадры
    // анализатору и превью экрану сканера. Всё, что импортирует их, лежит в qr/ и
    // ui/qr/, как AppMetrica в analytics/.
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Вход в профиль через VK ID. Всё, что импортирует com.vk.id, лежит в пакете vkid/ —
    // как AppMetrica в analytics/.
    implementation(libs.vkid)
    // HTTP сервера аккаунтов (account/AccountApi). В APK OkHttp и так лежит — его приносит
    // AppMetrica; своя строка нужна лишь затем, чтобы не опираться на чужую транзитивную.
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.junit)
    // org.json для JVM-тестов кодека файла закладки: на устройстве его даёт платформа,
    // а в юнит-тестах методы android.jar — заглушки, бросающие «not mocked». Только в
    // тесты — в основной сборке AGP такую зависимость и так отбросил бы как дубликат.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Ради TestListenableWorkerBuilder и WorkManagerTestInitHelper: поведение
    // напоминаний — «завершённая закладка молчит», «расписание пересобирается по
    // базе» — проверяется прогоном настоящей работы, а не рассуждением о ней.
    androidTestImplementation(libs.androidx.work.testing)
    // GrantPermissionRule: без POST_NOTIFICATIONS работа честно ничего не покажет, и
    // тест «идущая закладка будит» проверял бы отсутствие разрешения, а не поведение.
    androidTestImplementation(libs.androidx.test.rules)
}
