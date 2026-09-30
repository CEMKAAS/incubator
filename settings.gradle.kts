pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Единственный репозиторий, где лежат SDK RuStore: обновление, оценка и пуши.
        // В Maven Central их нет и не будет.
        //
        // Фильтр по содержимому — не украшение. Репозиторий стоит последним, и без
        // фильтра Gradle ходил бы в него за каждой зависимостью, которой не нашлось в
        // двух предыдущих: лишний сетевой запрос на каждый промах и, что хуже, чужой
        // сервер в цепочке разрешения любой библиотеки проекта. `ru.ok.tracer` тоже
        // указан: его тянет за собой pushclient, и в Maven Central он есть, но если
        // однажды не окажется нужной версии — пусть будет куда сходить.
        maven("https://artifactory-external.vkpartner.ru/artifactory/maven") {
            content {
                includeGroupByRegex("ru\\.rustore.*")
                includeGroupByRegex("ru\\.ok\\.tracer.*")
            }
        }
        // SDK VK ID — вход в профиль через VK. Отдельный репозиторий того же сервера,
        // и не просто с фильтром, а эксклюзивно: группы самого SDK (`com.vk.id`,
        // `com.vk.id.captcha`) берутся только отсюда и ниоткуда больше. Обычный
        // `content { include… }` лишь ограничивал бы, что может отдать этот
        // репозиторий, — а `google()` и `mavenCentral()` стоят раньше, и артефакт
        // с тем же именем, выложенный туда кем-то другим, победил бы. Две его
        // зависимости — `com.vk:android-sdk-id` и трейсер `ru.ok.tracer` — лежат в Maven
        // Central и приходят оттуда: здесь их нет.
        exclusiveContent {
            forRepository {
                maven("https://artifactory-external.vkpartner.ru/artifactory/vkid-sdk-android")
            }
            filter {
                includeGroup("com.vk.id")
                includeGroupByRegex("com\\.vk\\.id\\..*")
            }
        }
    }
}

rootProject.name = "Incubator"
include(":app")
include(":domain")
include(":data")
include(":design")
