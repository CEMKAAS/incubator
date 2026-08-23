package ru.zaroslikov.incubator.ui.navigation


import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.ui.batch.BatchArchiveDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorScreen
import ru.zaroslikov.incubator.ui.batch.BatchArchiveScreen
import ru.zaroslikov.incubator.ui.batch.BatchDayScreen
import ru.zaroslikov.incubator.ui.batch.BatchDayDestination
import ru.zaroslikov.incubator.ui.batch.CandlingDestination
import ru.zaroslikov.incubator.ui.batch.CandlingScreen
import ru.zaroslikov.incubator.ui.batch.BatchScreen
import ru.zaroslikov.incubator.ui.batch.BatchDestination
import ru.zaroslikov.incubator.ui.start.StartDestination
import ru.zaroslikov.incubator.ui.start.StartScreen


@Composable
fun InventoryNavHost(
    navController: NavHostController,
    firstLaunch: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    NavHost(
        navController = navController,
        startDestination = StartDestination.route
    ) {

        // Форма инкубатора — нижняя шторка, а не пункт назначения: она открывается
        // поверх списка (создание) или поверх самого инкубатора (правка).
        composable(route = StartDestination.route) {
            StartScreen(
                navigateToIncubator = {
                    AppMetrica.reportEvent("Переход в Инкубатор")
                    navController.navigate("${IncubatorDestination.route}/${it}")
                },
                openAddOnStart = firstLaunch,
                contentPadding = contentPadding
            )

        }

        composable(
            route = IncubatorDestination.routeWithArgs,
            arguments = listOf(navArgument(IncubatorDestination.itemIdArg) {
                type = NavType.LongType
            })
        ) {
            IncubatorScreen(
                navigateBack = { navController.popBackStack() },
                // Само открытие закладки — шторка внутри экрана; сюда ведёт только
                // ссылка «Расписание по дням» из неё.
                navigateToBatch = {
                    AppMetrica.reportEvent("Переход в расписание")
                    navController.navigate("${BatchDestination.route}/${it}")
                },
                navigateToArchivedBatch = {
                    AppMetrica.reportEvent("Переход в Архив")
                    navController.navigate("${BatchArchiveDestination.route}/${it}")
                },
                contentPadding = contentPadding
            )
        }

        composable(
            route = BatchDestination.routeWithArgs,
            arguments = listOf(navArgument(BatchDestination.itemIdArg) {
                type = NavType.LongType
            })
        ) {
            BatchScreen(navigateBack = { navController.popBackStack() }, navigateDayEdit = {
                AppMetrica.reportEvent("Переход в редактор дня")
                navController.navigate(
                    "${BatchDayDestination.route}/${it.first}/${it.second}"
                )
            }, navigateOvos = {
                AppMetrica.reportEvent("Переход в Овоскопирование", it.second)
                navController.navigate(
                    "${CandlingDestination.route}/${it.first}/${it.second}"
                )
            }, navigateStart = {
                navController.navigate(StartDestination.route)
            },
                contentPadding = contentPadding
            )
        }

        composable(
            route = CandlingDestination.routeWithArgs,
            arguments = listOf(navArgument(CandlingDestination.itemIdArg) {
                type = NavType.IntType
            }, navArgument(CandlingDestination.itemIdArgTwo) {
                type = NavType.StringType
            })
        ) {
            CandlingScreen(navigateBack = {
                navController.popBackStack()
            }, onNavigateUp = { navController.navigateUp() },
                contentPadding = contentPadding)
        }

        composable(
            route = BatchDayDestination.routeWithArgs,
            arguments = listOf(navArgument(BatchDayDestination.itemIdArg) {
                type = NavType.LongType
            }, navArgument(BatchDayDestination.itemIdArgTwo) {
                type = NavType.IntType
            })
        ) {
            BatchDayScreen(navigateBack = {
                navController.popBackStack()
            }, onNavigateUp = { navController.navigateUp() },
                contentPadding = contentPadding)
        }

        composable(route = BatchArchiveDestination.routeWithArgs,
            arguments = listOf(
                navArgument(BatchArchiveDestination.itemIdArg) {
                    type = NavType.LongType
                }
            )) {
            BatchArchiveScreen(
                navigateBack = { navController.popBackStack() },
                navigateStart = { navController.navigateUp() },
                contentPadding = contentPadding
            )
        }
    }
}
