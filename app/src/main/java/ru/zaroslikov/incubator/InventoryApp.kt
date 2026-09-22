package ru.zaroslikov.incubator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.ui.navigation.InventoryNavHost
import ru.zaroslikov.incubator.ui.pickerMillisToDate
import java.time.LocalDate


/**
 * @param showGuide инструкция ещё не закрыта: приложение открывается ею, а не главным
 *   экраном. Верно и для обновившихся — инструкцию видят все, кто её не закрыл.
 * @param openAddIncubator после инструкции сразу открыть форму инкубатора. Только для
 *   настоящего первого запуска: обновившийся попадает на свой список инкубаторов.
 * @param launchTarget куда приложение открыто снаружи — закладка из уведомления или
 *   инкубатор из QR-кода; `null` — обычный запуск с главного экрана.
 * @param launchSerial порядковый номер цели: растёт, когда ссылка из QR-кода приходит
 *   в работающее приложение, чтобы граф отличил новую цель от уже обработанной.
 * @param onGuideFinished инструкцию дочитали или пропустили — активность снимает флаги
 *   и спрашивает разрешение на уведомления.
 */
@Composable
fun InventoryApp(
    navController: NavHostController = rememberNavController(),
    showGuide: Boolean,
    openAddIncubator: Boolean = false,
    launchTarget: LaunchTarget? = null,
    launchSerial: Int = 0,
    onGuideFinished: () -> Unit = {},
) {
    // Фокус снимается нажатием мимо поля на любом экране: обработчик стоит в корне,
    // а не в каждой форме, — до него доходит только касание, которое не забрали себе
    // поле, кнопка или карточка.
    Scaffold(modifier = Modifier.clearFocusOnTap()) { innerPadding ->
        InventoryNavHost(
            navController = navController,
            showGuide = showGuide,
            openAddIncubator = openAddIncubator,
            launchTarget = launchTarget,
            launchSerial = launchSerial,
            onGuideFinished = onGuideFinished,
            contentPadding = innerPadding
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarStart(
    title: String,
    settingBoolean: Boolean,
    navigateUp: () -> Unit = {},
    settingUp: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(text = title)
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад"
                )
            }
        },
        actions = {
            if (settingBoolean) {
                IconButton(onClick = settingUp) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Настройка"
                    )
                }
            }
        }
    )
}

fun formatterTime(hour: Int, minute: Int): String {
    val formattedHour = hour.toString().padStart(2, '0')
    val formattedMinute = minute.toString().padStart(2, '0')

    return "$formattedHour:$formattedMinute"
}

@OptIn(ExperimentalMaterial3Api::class)
object PastOrPresentSelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        return utcTimeMillis <= System.currentTimeMillis()
    }

    override fun isSelectableYear(year: Int): Boolean {
        return year <= LocalDate.now().year
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogSample(
    datePickerState: DatePickerState,
    dateToday: String,
    onDateSelected: (String) -> Unit
) {
    DatePickerDialog(
        onDismissRequest = {
            onDateSelected(dateToday)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Пикер отдаёт полночь по UTC, а без выбранной даты — null: и то и
                    // другое здесь раньше уходило в SimpleDateFormat как есть, и второе
                    // роняло приложение прямо на кнопке «Выбрать».
                    val millis = datePickerState.selectedDateMillis
                    onDateSelected(if (millis == null) dateToday else pickerMillisToDate(millis))
                },
            ) { Text("Выбрать") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDateSelected(dateToday)
                }
            ) { Text("Назад") }
        }
    ) {
        DatePicker(state = datePickerState, dateFormatter = DatePickerDefaults.dateFormatter())
    }
}

