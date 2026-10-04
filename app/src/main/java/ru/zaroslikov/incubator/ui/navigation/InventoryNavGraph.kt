package ru.zaroslikov.incubator.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.LaunchTarget
import ru.zaroslikov.incubator.QrTarget
import ru.zaroslikov.incubator.ReminderTarget
import ru.zaroslikov.incubator.TimerTarget
import ru.zaroslikov.incubator.ui.guide.GuideDestination
import ru.zaroslikov.incubator.ui.guide.GuideScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorScreen
import ru.zaroslikov.incubator.ui.menu.AboutDestination
import ru.zaroslikov.incubator.ui.menu.AboutScreen
import ru.zaroslikov.incubator.ui.menu.AnalyticsDestination
import ru.zaroslikov.incubator.ui.menu.AnalyticsScreen
import ru.zaroslikov.incubator.ui.menu.SettingsDestination
import ru.zaroslikov.incubator.ui.menu.SettingsScreen
import ru.zaroslikov.incubator.ui.qr.ScanQrDestination
import ru.zaroslikov.incubator.ui.qr.ScanQrScreen
import ru.zaroslikov.incubator.ui.start.StartDestination
import ru.zaroslikov.incubator.ui.start.StartScreen

/**
 * @param showGuide инструкция ещё не закрыта — граф начинается с неё.
 * @param openAddIncubator после инструкции главный экран сразу открывает форму
 *   инкубатора. Только настоящий первый запуск: обновившийся попадает на свой список.
 * @param onGuideFinished инструкцию закрыли — дочитав или пропустив. Повторный просмотр
 *   из «О приложении» сюда не приходит: там нечего снимать.
 */
@Composable
fun InventoryNavHost(
    navController: NavHostController,
    showGuide: Boolean,
    openAddIncubator: Boolean = false,
    launchTarget: LaunchTarget? = null,
    launchSerial: Int = 0,
    onGuideFinished: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // Приложение открыли снаружи — нажатием по напоминанию или ссылкой из QR-кода — и
    // уходим туда, куда оно открыто. Переход поверх главного экрана, а не вместо него:
    // «назад» тогда ведёт к списку инкубаторов, как и всякий раз, когда в инкубатор
    // входят сами.
    //
    // Обработанный номер — в `rememberSaveable`: поворот пересоздаёт активность с тем же
    // намерением, и без него переход повторялся бы на каждый поворот. Номер, а не флаг:
    // QR-ссылка приходит и в работающее приложение (`onNewIntent`) с новым номером, а
    // новый экземпляр активности начинает счёт с нуля и ничего не повторяет.
    var handledSerial by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(launchTarget, launchSerial) {
        val target = launchTarget ?: return@LaunchedEffect
        if (handledSerial >= launchSerial) return@LaunchedEffect
        handledSerial = launchSerial
        when (target) {
            is ReminderTarget -> {
                Analytics.report(Events.REMINDER_OPENED)
                navController.navigate(
                    IncubatorDestination.routeFor(target.incubatorId, target.batchId)
                )
            }
            // Код — это «подойти к прибору»: стек над главным экраном сбрасывается, и
            // сверху ложится один этот инкубатор, а не по одному на каждую наклейку,
            // отсканированную за обход птичника. Если инкубатора с таким номером нет,
            // экран сам скажет об этом. `popUpTo` — до стартового экрана графа, а не до
            // `Start` по имени: пока открыта инструкция, стартовый — она, и `Start` в
            // стеке нет вовсе; по имени ничего бы не срезалось, и инкубатор лёг бы поверх
            // инструкции, а «назад» возвращал бы в неё.
            is QrTarget -> {
                Analytics.report(Events.QR_OPENED, mapOf("Источник" to "камера"))
                navController.navigate(
                    IncubatorDestination.routeFor(target.incubatorId, measure = true)
                ) {
                    popUpTo(navController.graph.findStartDestination().id)
                }
            }
            // Уведомление таймера проветривания — «вернуться к той форме»: к закладке,
            // если таймер ставили в её шторке, или к «Замерам за сегодня» инкубатора.
            // Стек срезается, как по коду: приложение, скорее всего, уже открыто, и та же
            // шторка не должна лечь второй раз поверх самой себя.
            is TimerTarget -> {
                Analytics.report(
                    Events.AIRING_TIMER_OPENED,
                    mapOf("Из инкубатора" to (target.batchId == 0L)),
                )
                val route = if (target.batchId != 0L) {
                    IncubatorDestination.routeFor(target.incubatorId, target.batchId)
                } else {
                    IncubatorDestination.routeFor(target.incubatorId, measure = true)
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        // Пока инструкция не закрыта, приложение начинается с неё — и у поставившего его
        // впервые, и у обновившегося. Первый сперва узнаёт, что тут где, и только потом
        // получает поле «Название»; второй просто узнаёт то же самое и выходит на свой
        // список инкубаторов (`openAddIncubator`). Стартовый маршрут — с шаблоном
        // аргумента: у него есть значение по умолчанию, и «не повтор» оно и означает.
        startDestination = if (showGuide) GuideDestination.routeWithArgs
        else StartDestination.route
    ) {
        // Инструкция — экран, а не шторка: она занимает всё, и листается сама, а шторка
        // поверх главного экрана спорила бы с ним за жест. Один и тот же экран открывают
        // дважды: первым запуском (тогда после него — главный экран, и инструкция
        // вычищается из стека, чтобы «назад» в неё не возвращал) и из «О приложении»
        // (`revisit` — тогда закрытие просто возвращает туда, откуда пришли).
        composable(
            route = GuideDestination.routeWithArgs,
            arguments = listOf(
                navArgument(GuideDestination.revisitArg) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val revisit = backStackEntry.arguments
                ?.getBoolean(GuideDestination.revisitArg) ?: false
            GuideScreen(
                revisit = revisit,
                contentPadding = contentPadding,
                onFinish = { completed ->
                    if (revisit) {
                        // Повторный просмотр событий не шлёт: пара «пройдена / пропущена»
                        // считает долю первых запусков, дочитавших до конца, и повтор из
                        // «О приложении» её бы размывал. Само открытие считает «Инструкция».
                        navController.popBackStack()
                    } else {
                        Analytics.report(
                            if (completed) Events.GUIDE_COMPLETED else Events.GUIDE_SKIPPED
                        )
                        onGuideFinished()
                        navController.navigate(StartDestination.route) {
                            popUpTo(GuideDestination.routeWithArgs) { inclusive = true }
                            // Уходящий экран остаётся нажимаемым, пока идёт переход:
                            // второе нажатие «Начать» иначе клало бы в стек второй Start.
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        // Форма инкубатора — нижняя шторка, а не пункт назначения: она открывается
        // поверх списка (создание) или поверх самого инкубатора (правка).
        composable(route = StartDestination.route) {
            StartScreen(
                navigateToIncubator = {
                    Analytics.report(Events.OPEN_INCUBATOR)
                    navController.navigate(IncubatorDestination.routeFor(it))
                },
                navigateToAnalytics = {
                    Analytics.report(Events.OPEN_ANALYTICS)
                    navController.navigate(AnalyticsDestination.route)
                },
                navigateToSettings = {
                    Analytics.report(Events.OPEN_SETTINGS)
                    navController.navigate(SettingsDestination.route)
                },
                navigateToAbout = {
                    // Имя события прежнее: раздел заменил собой шторку «Информация» с
                    // главного экрана, и переименование разорвало бы ряд статистики.
                    Analytics.report(Events.OPEN_ABOUT)
                    navController.navigate(AboutDestination.route)
                },
                openAddOnStart = openAddIncubator,
                navigateToScanner = {
                    Analytics.report(Events.SCANNER_OPENED)
                    navController.navigate(ScanQrDestination.route) { launchSingleTop = true }
                },
                contentPadding = contentPadding
            )
        }

        // Три раздела меню приложения. Аргументов у них нет: аналитика считается по
        // всему хозяйству, а настройки и «О приложении» вообще не зависят от данных.
        composable(route = AnalyticsDestination.route) {
            AnalyticsScreen(
                navigateBack = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }

        composable(route = SettingsDestination.route) {
            SettingsScreen(
                navigateBack = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }

        // Сканер QR-кода — экран, а не шторка: ему нужна камера во весь экран, и открывают
        // его с главного экрана и из шторки замеров. Найденный инкубатор ложится поверх
        // главного экрана вместо всего, что было выше него, — тот же переход, что и у
        // ссылки из камеры телефона: сканер и инкубатор, из которого его открыли,
        // уходят из стека вместе, и «назад» ведёт к списку инкубаторов.
        composable(route = ScanQrDestination.route) {
            ScanQrScreen(
                navigateBack = { navController.popBackStack() },
                onFound = { incubatorId ->
                    Analytics.report(Events.QR_OPENED, mapOf("Источник" to "сканер"))
                    navController.navigate(
                        IncubatorDestination.routeFor(incubatorId, measure = true)
                    ) {
                        popUpTo(navController.graph.findStartDestination().id)
                    }
                },
                contentPadding = contentPadding,
            )
        }

        composable(route = AboutDestination.route) {
            AboutScreen(
                navigateBack = { navController.popBackStack() },
                navigateToGuide = {
                    Analytics.report(Events.GUIDE_OPENED)
                    navController.navigate(GuideDestination.routeFor(revisit = true)) {
                        launchSingleTop = true
                    }
                },
                contentPadding = contentPadding
            )
        }

        composable(
            route = IncubatorDestination.routeWithArgs,
            arguments = listOf(
                navArgument(IncubatorDestination.itemIdArg) { type = NavType.LongType },
                // Необязательный: его передаёт только приход по напоминанию, обычный
                // переход в инкубатор оставляет ноль — «шторку не раскрывать».
                navArgument(IncubatorDestination.batchIdArg) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                // Тоже необязательный: его передаёт только приход по QR-коду — сразу
                // раскрыть «Замеры за сегодня» по инкубатору.
                navArgument(IncubatorDestination.measureArg) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            )
        ) { backStackEntry ->
            IncubatorScreen(
                navigateBack = { navController.popBackStack() },
                contentPadding = contentPadding,
                openBatchId = backStackEntry.arguments
                    ?.getLong(IncubatorDestination.batchIdArg) ?: 0L,
                openMeasurements = backStackEntry.arguments
                    ?.getBoolean(IncubatorDestination.measureArg) ?: false,
                navigateToScanner = {
                    Analytics.report(Events.SCANNER_OPENED)
                    navController.navigate(ScanQrDestination.route) { launchSingleTop = true }
                },
            )
        }
    }
}
