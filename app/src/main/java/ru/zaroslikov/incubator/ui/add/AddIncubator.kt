package ru.zaroslikov.incubator.ui.add

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.TimeZone
import androidx.compose.ui.window.Dialog
import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.DatePickerDialogSample
import ru.zaroslikov.incubator.PastOrPresentSelectableDates
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.convertDateStringToMillis
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.formatterTime
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.IncubatorUiState
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination

object AddIncubatorDestination : NavigationDestination {
    override val route = "AddIncubator"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemId"
}

@Composable
fun AddIncubator(
    navigateBack: () -> Unit,
    firstLaunch:Boolean,
    viewModel: AddIncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
    navigateContinue: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var shouldShowTwo by rememberSaveable { mutableStateOf(true) }
    var openEndDialog by rememberSaveable { mutableStateOf(false) }
    var listBoolean by rememberSaveable { mutableStateOf(false) }
    var openFirstDialog by rememberSaveable { mutableStateOf(firstLaunch) }
    val textFields2 = remember {
        mutableStateListOf(
            Time(
                id = 0,
                time = "08:00",
                idPT = 0
            )
        )
    }

    val incubator = viewModel.incubatorUiState

//   Доставка из архива
    viewModel.getIncubatorArchiveList(incubator.type)
    val projectList = viewModel.incubatorArchiveList.value

    val list = setAutoIncubator(
        setIncubator(incubator.type),
        incubator.airing,
        incubator.over
    )
    val list2 = viewModel.valueArchiveList.value.toMutableList()


    if (openEndDialog) {
        ArhivIncubatorChoice(
            openDialog = {
                listBoolean = false
                openEndDialog = false
                shouldShowTwo = false
            },
            projectList = projectList,
            incubatorArh = {
                viewModel.getValueArchiveList(it)
            },
            incubatorArh2 = {
                listBoolean = true
                openEndDialog = false
                shouldShowTwo = false
            }
        )
    }

    if (openFirstDialog) {
        AlertDialogExample(
            onDismissRequest = { openFirstDialog = false },
            onConfirmation = {
                openFirstDialog = false
            },
            dialogTitle = "Добро Пожаловать!",
            dialogText = "Давайте настроим Ваш первый инкубатор!\n" +
                    "Для начала укажите его название, вид птицы, количество. После этого сможете перейти в меню для детальной настройки каждого дня.\n" +
                    "Удачи!",
            icon = Icons.Default.Info
        )
    }

    if (shouldShowTwo)
        AddIncubatorContainerOne(
            navigateBack = navigateBack,
            navigateContinue = {
                if (projectList.isEmpty()) shouldShowTwo = false else openEndDialog = true
            },
            incubator = incubator,
            onUpdate = viewModel::updateUiState,
            timeList = textFields2,
            firstLaunch = firstLaunch,
            contentPadding = contentPadding
        )
    else AddIncubatorContainerTwo(
        name = incubator.title,
        navigateBack = {
            listBoolean = false
            shouldShowTwo = true
        },
        navigateContinue = {
            viewModel.saveIncubator(it, textFields2)
            val eventParameters: MutableMap<String, Any> = HashMap()
            eventParameters["Имя"] = incubator.title
            eventParameters["Тип"] = incubator.type
            eventParameters["Кол-во"] = incubator.eggAll
            eventParameters["Прим"] = incubator.note
            eventParameters["АвтоОхл"] = incubator.airing
            eventParameters["АвтоПрев"] = incubator.over
            textFields2.forEachIndexed { index, time ->
                eventParameters[index.toString()] = time
            }
            AppMetrica.reportEvent("Инкубатор", eventParameters);
            navigateContinue()
        },
        list = if (listBoolean) list2 else list,
        firstLaunch = firstLaunch,
        contentPadding = contentPadding
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncubatorContainerOne(
    navigateBack: () -> Unit,
    navigateContinue: () -> Unit,
    incubator: IncubatorUiState,
    onUpdate: (IncubatorUiState) -> Unit = {},
    timeList: MutableList<Time>,
    firstLaunch:Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val modifier = Modifier
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarEdit(
                firstLaunch = firstLaunch,
                title = "Установка Инкубатора",
                navigateUp = navigateBack,
                scrollBehavior = scrollBehavior,
            )
        }) { innerPadding ->
        AddIncubatorContainer(
            modifier = modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            navigateContinue = navigateContinue,
            incubator, onUpdate,
            timeList = timeList
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncubatorContainer(
    modifier: Modifier,
    navigateContinue: () -> Unit,
    incubator: IncubatorUiState,
    onUpdate: (IncubatorUiState) -> Unit = {},
    timeList: MutableList<Time>
) {
    val typeBirdsList = arrayListOf("Курицы", "Гуси", "Перепела", "Индюки", "Утки")
    val context = LocalContext.current

    var expandedTypeBirds by remember { mutableStateOf(false) }

    var isErrorTitle by rememberSaveable { mutableStateOf(false) }
    var isErrorCount by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    var selectedItemIndex by remember { mutableIntStateOf(0) }

    //Дата
    var openDialog by remember { mutableStateOf(false) }

    if (openDialog) {
        val datePickerState = rememberDatePickerState(
            selectableDates = PastOrPresentSelectableDates,
            initialSelectedDateMillis = convertDateStringToMillis(incubator.data)
        )
        DatePickerDialogSample(datePickerState, incubator.data) { date ->
            onUpdate(incubator.copy(data = date))
            openDialog = false
        }
    }

    fun validateTitle(text: String) {
        isErrorTitle = text == ""
    }

    fun validateCount(text: String) {
        isErrorCount = text == ""
    }

    fun errorBoolean(): Boolean {
        isErrorTitle = incubator.title == ""
        isErrorCount = incubator.eggAll == ""
        return !(isErrorTitle || isErrorCount)
    }

    Column(modifier = modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {

        OutlinedTextField(
            value = incubator.title,
            onValueChange = {
                onUpdate(incubator.copy(title = it))
                validateTitle(it)
            },
            label = { Text("Название") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                if (isErrorTitle) {
                    Text(
                        text = "Не указано название",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Укажите название инкубатора")
                }
            },
            isError = isErrorTitle,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            })
        )

        Box {
            ExposedDropdownMenuBox(
                expanded = expandedTypeBirds,
                onExpandedChange = { expandedTypeBirds = !expandedTypeBirds },
            ) {
                OutlinedTextField(
                    value = incubator.type,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeBirds) },
                    label = { Text(text = "Вид птицы") },
                    supportingText = {
                        Text("Выберите вид птицы")
                    },
                    keyboardActions = KeyboardActions(onNext = {
                        focusManager.moveFocus(
                            FocusDirection.Down
                        )
                    }),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedTypeBirds,
                    onDismissRequest = { expandedTypeBirds = false }
                ) {
                    typeBirdsList.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item,
                                    fontWeight = if (index == selectedItemIndex) FontWeight.Bold else null
                                )
                            },
                            onClick = {
                                selectedItemIndex = index
                                expandedTypeBirds = false
                                onUpdate(incubator.copy(type = typeBirdsList[selectedItemIndex]))
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = incubator.eggAll,
            onValueChange = {
                onUpdate(
                    incubator.copy(
                        eggAll = it.replace(Regex("[^\\d.]"), "").replace(",", ".")
                    )
                )
                validateCount(it)
            },
            label = { Text("Количество") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                if (isErrorCount) {
                    Text(
                        text = "Не указано кол-во яиц",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Укажите кол-во яиц, которых заложили в инкубатор")
                }
            },
            suffix = { Text(text = "Шт.") },
            isError = isErrorCount,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            }
            )
        )

        OutlinedTextField(
            value = incubator.data,
            onValueChange = {},
            readOnly = true,
            label = { Text("Дата") },
            supportingText = {
                Text("Выберите дату ")
            },
            trailingIcon = {
                IconButton(onClick = {
                    openDialog = true

                }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_calendar_month_24),
                        contentDescription = "Показать меню"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    openDialog = true
                }
                .padding(bottom = 10.dp),
        )

        OutlinedTextField(
            value = incubator.note,
            onValueChange = {
                onUpdate(
                    incubator.copy(note = it)
                )
            },
            label = { Text("Примечание") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            supportingText = {
                Text("Здесь может быть важная информация")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            )
        )

        timeList.forEachIndexed { index, time ->
            TimeOutlinedTextField(
                onClickUp = { timeList[it.first] = time.copy(time = it.second) },
                time = time,
                count = index,
                countTime = {
                    timeList.removeAt(index)
                }
            )
        }

        OutlinedButton(
            onClick = {
                timeList.add(Time(id = 0, time = "08:00", idPT = 0))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 20.dp)
                .padding(bottom = 5.dp)
        ) {
            Text(text = "Добавить напоминание")
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Добавить",
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Card {
            Column(
                Modifier
                    .selectableGroup()
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text(
                    text = "Доп. настройки",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 10.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incubator.airing,
                        onCheckedChange = { onUpdate(incubator.copy(airing = it)) }
                    )
                    Text(text = "Авто охлаждение")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = incubator.over,
                        onCheckedChange = { onUpdate(incubator.copy(over = it)) }
                    )
                    Text(text = "Авто переворот")
                }

            }
        }

        Button(
            onClick = {
                if (errorBoolean()) navigateContinue() else Toast.makeText(
                    context,
                    "Не указано название инкубатора или кол-во яиц",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            Text(text = "Далее")
        }
    }
}


@Composable
fun TimeOutlinedTextField(
    onClickUp: (Pair<Int, String>) -> Unit,
    time: Time,
    count: Int,
    countTime: (Int) -> Unit
) {
    var showDialogTime by remember { mutableStateOf(false) }
    if (showDialogTime) TimePicker(time = time.time, showDialog = {
        onClickUp(Pair(count, it))
        showDialogTime = false
    })

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        OutlinedTextField(
            value = time.time,
            onValueChange = {},
            readOnly = true,
            label = { Text("Уведомление ${count + 1}") },
            supportingText = {
                Text("Укажите время для уведомления ${count + 1}")
            },
            trailingIcon = {
                IconButton(onClick = { showDialogTime = true }) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_access_time_24),
                        contentDescription = "Показать меню"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable { showDialogTime = true }
                .padding(bottom = 2.dp)
        )

        IconButton(
            onClick = {
                countTime(count)
            }, modifier = Modifier
                .padding(bottom = 13.dp)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Удалить")
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePicker(time: String, showDialog: (String) -> Unit) {

    val timsa = time.split(":")
    val timeState = rememberTimePickerState(
        initialHour = timsa[0].toInt(),
        initialMinute = timsa[1].toInt()
    )
    Dialog(
        onDismissRequest = { showDialog(time) }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .background(color = Color.LightGray)
                    .padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        showDialog(formatterTime(timeState.hour, timeState.minute))
                    }) {
                        Text(text = "Принять")
                    }

                    TextButton(onClick = {
                        showDialog(time)
                    }) {
                        Text(text = "Назад")
                    }
                }
            }
        }
    }
}

