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
    }
}

rootProject.name = "Incubator"
include(":app")
include(":domain")
include(":data")
include(":design")
