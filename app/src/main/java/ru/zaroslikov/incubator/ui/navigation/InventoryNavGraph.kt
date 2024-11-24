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
import ru.zaroslikov.incubator.ui.add.AddIncubator
import ru.zaroslikov.incubator.ui.add.AddIncubatorDestination
import ru.zaroslikov.incubator.ui.arhiv.IncubatorArhivDestination
import ru.zaroslikov.incubator.ui.arhiv.IncubatorArhivScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorEditDayScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorEditDayScreenDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorOvoscopDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorOvoscopScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorProjectEditDestination
import ru.zaroslikov.incubator.ui.incubator.IncubatorProjectEditScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorScreen
import ru.zaroslikov.incubator.ui.incubator.IncubatorScreenDestination
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
        startDestination = if (firstLaunch) AddIncubatorDestination.route else StartDestination.route
    ) {

        composable(route = StartDestination.route) {
            StartScreen(
                navigateToItemAdd = {
                    AppMetrica.reportEvent("Переход в Добавление")
                    navController.navigate(AddIncubatorDestination.route)
                },
                navigateToItemIncubator = {
                    AppMetrica.reportEvent("Переход в Инкубатор")
                    navController.navigate("${IncubatorScreenDestination.route}/${it}")
                },
                navigateToItemIncubatorArh = {
                    AppMetrica.reportEvent("Переход в Архив")
                    navController.navigate("${IncubatorArhivDestination.route}/${it}")
                },
                contentPadding = contentPadding
            )

        }

        composable(route = AddIncubatorDestination.route) {
            AddIncubator(
                navigateBack = { navController.popBackStack() },
                navigateContinue = { navController.navigate(StartDestination.route) },
                firstLaunch = firstLaunch,
                contentPadding = contentPadding
            )
        }

        composable(
            route = IncubatorScreenDestination.routeWithArgs,
            arguments = listOf(navArgument(IncubatorScreenDestination.itemIdArg) {
                type = NavType.IntType
            })
        ) {
            IncubatorScreen(navigateBack = { navController.popBackStack() }, navigateDayEdit = {
                AppMetrica.reportEvent("Переход в редактор дня")
                navController.navigate(
                    "${IncubatorEditDayScreenDestination.route}/${it.first}/${it.second}"
                )
            }, navigateProjectEdit = {
                AppMetrica.reportEvent("Переход в редактор инкубатора")
                navController.navigate(
                    "${IncubatorProjectEditDestination.route}/${it}"
                )
            }, navigateOvos = {
                AppMetrica.reportEvent("Переход в Овоскопирование", it.second)
                navController.navigate(
                    "${IncubatorOvoscopDestination.route}/${it.first}/${it.second}"
                )
            }, navigateStart = {
                navController.navigate(StartDestination.route)
            },
                contentPadding = contentPadding
            )
        }

        composable(
            route = IncubatorOvoscopDestination.routeWithArgs,
            arguments = listOf(navArgument(IncubatorOvoscopDestination.itemIdArg) {
                type = NavType.IntType
            }, navArgument(IncubatorOvoscopDestination.itemIdArgTwo) {
                type = NavType.StringType
            })
        ) {
            IncubatorOvoscopScreen(navigateBack = {
                navController.popBackStack()
            }, onNavigateUp = { navController.navigateUp() },
                contentPadding = contentPadding)
        }

        composable(
            route = IncubatorProjectEditDestination.routeWithArgs,
            arguments = listOf(navArgument(IncubatorProjectEditDestination.itemIdArg) {
                type = NavType.IntType
            })
        ) {
            IncubatorProjectEditScreen(navigateBack = { navController.popBackStack() },
                onNavigateUp = { navController.navigateUp() },
                navigateStart = { navController.navigate(StartDestination.route) },
                contentPadding = contentPadding)
        }

        composable(
            route = IncubatorEditDayScreenDestination.routeWithArgs,
            arguments = listOf(navArgument(IncubatorEditDayScreenDestination.itemIdArg) {
                type = NavType.IntType
            }, navArgument(IncubatorEditDayScreenDestination.itemIdArgTwo) {
                type = NavType.IntType
            })
        ) {
            IncubatorEditDayScreen(navigateBack = {
                navController.popBackStack()
            }, onNavigateUp = { navController.navigateUp() },
                contentPadding = contentPadding)
        }

        composable(route = IncubatorArhivDestination.routeWithArgs,
            arguments = listOf(
                navArgument(IncubatorProjectEditDestination.itemIdArg) {
                    type = NavType.IntType
                }
            )) {
            IncubatorArhivScreen(
                navigateBack = { navController.popBackStack() },
                navigateStart = { navController.navigateUp() },
                contentPadding = contentPadding
            )
        }
    }
}
