package ru.zaroslikov.incubator

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
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
import ru.zaroslikov.incubator.ui.Banner
import ru.zaroslikov.incubator.ui.navigation.InventoryNavHost
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


@Composable
fun InventoryApp(
    navController: NavHostController = rememberNavController(),
    firstLaunch: Boolean
) {
    Scaffold(bottomBar = {
        Banner(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
    ) { innerPadding ->
        InventoryNavHost(
            navController = navController,
            firstLaunch = firstLaunch,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarStart2(
    title: String,
    infoBottomSheet: () -> Unit,
    archiveButton: () -> Unit,
    boolean: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        colors = TopAppBarDefaults.largeTopAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(text = title)
        },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = archiveButton) {
                Icon(
                    painter = painterResource(id = if (boolean) R.drawable.baseline_unarchive_24 else R.drawable.baseline_archive_24),
                    contentDescription = "Localized description",
                )
            }
            IconButton(onClick = infoBottomSheet) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Информация"
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarEdit(
    firstLaunch: Boolean = false,
    title: String, navigateUp: () -> Unit = {},
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
            if (!firstLaunch) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад"
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

fun convertDateStringToMillis(dateString: String): Long {
    val sd = dateString.split(".")

    val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, sd[0].toInt())
        set(Calendar.MONTH, sd[1].toInt()-1)
        set(Calendar.YEAR,  sd[2].toInt())
    }
    calendar.timeZone = TimeZone.getDefault()
    return calendar.timeInMillis
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
                    val format = SimpleDateFormat("dd.MM.yyyy")
                    val formattedDate: String =
                        format.format(datePickerState.selectedDateMillis)
                    onDateSelected(formattedDate)
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

