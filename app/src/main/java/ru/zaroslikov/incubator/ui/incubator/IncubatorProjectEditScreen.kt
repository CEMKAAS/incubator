package ru.zaroslikov.incubator.ui.incubator


import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.DatePickerDialogSample
import ru.zaroslikov.incubator.PastOrPresentSelectableDates
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.convertDateStringToMillis
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.add.TimeOutlinedTextField
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination


object IncubatorProjectEditDestination : NavigationDestination {
    override val route = "IncubatorProjectEdit"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemId"
    val routeWithArgs = "$route/{$itemIdArg}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorProjectEditScreen(
    navigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    navigateStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncubatorProjectEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val coroutineScope = rememberCoroutineScope()
    val project = viewModel.projectState
    val newList = viewModel.incubatorTimeList.toMutableList()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val eventParameters: MutableMap<String, Any> = HashMap()
    eventParameters["Имя"] = project.title
    eventParameters["Тип"] = project.type
    eventParameters["Кол-во"] = project.eggAll
    eventParameters["АвтоОхл"] = project.airing
    eventParameters["АвтоПрев"] = project.over
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarEdit(
                title = "Инкубатор",
                navigateUp = navigateBack,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->

        IncubatorEditDayContainer(
            project = project,
            modifier = modifier
                .padding(innerPadding)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            onValueChange = viewModel::updateUiState,
            saveInRoomAdd = {
                if (it) {
                    coroutineScope.launch {
                        viewModel.saveItem()
                        AppMetrica.reportEvent("Incubator Edit", eventParameters)
                        onNavigateUp()
                    }
                }
            },
            archivInRoom = {
                if (it) {
                    coroutineScope.launch {
                        viewModel.archivItem()
                        AppMetrica.reportEvent("Incubator Archive", eventParameters)
                        navigateStart()
                    }
                }
            },
            deleteRoom = {
                coroutineScope.launch {
                    viewModel.deleteItem()
                    navigateStart()
                }
            },
            timeList = newList,
            onClick = {
                viewModel.addItem(Time(id = 0, time = "08:00", idPT = 0))
            },
            onClickDel = { viewModel.delItem(it) },
            onClickUp = { viewModel.updateItem(it.first, it.second) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorEditDayContainer(
    project: IncubatorUiState,
    modifier: Modifier = Modifier,
    onValueChange: (IncubatorUiState) -> Unit = {},
    saveInRoomAdd: (Boolean) -> Unit,
    archivInRoom: (Boolean) -> Unit,
    deleteRoom: () -> Unit,
    timeList: MutableList<Time>,
    onClick: () -> Unit,
    onClickDel: (Int) -> Unit,
    onClickUp: (Pair<Int, String>) -> Unit
) {
    var isErrorTitle by rememberSaveable { mutableStateOf(false) }
    var isErrorCount by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    //Дата
    var openDialog by remember { mutableStateOf(false) }

    if (openDialog) {
        val datePickerState = rememberDatePickerState(
            selectableDates = PastOrPresentSelectableDates,
            initialSelectedDateMillis = convertDateStringToMillis(project.data)
        )
        DatePickerDialogSample(datePickerState, project.data) { date ->
            onValueChange(project.copy(data = date))
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
        isErrorTitle = project.title == ""
        isErrorCount = project.eggAll == ""
        return !(isErrorTitle || isErrorCount)
    }

    Column(modifier = modifier) {

        OutlinedTextField(
            value = project.title,
            onValueChange = {
                onValueChange(project.copy(title = it))
                validateTitle(it)
            },
            label = { Text("Инкубатор") },
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
            }
            )
        )

        OutlinedTextField(
            value = project.eggAll,
            onValueChange = {
                onValueChange(
                    project.copy(
                        eggAll = it.replace(Regex("[^\\d.]"), "").replace(",", ".")
                    )
                )
                validateCount(project.eggAll)
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
            value = project.data,
            onValueChange = {},
            readOnly = true,
            label = { Text("Дата") },
            supportingText = {
                Text("Выберите дату ")
            },
            trailingIcon = {
                IconButton(onClick = { openDialog = true }) {
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
            value = project.note,
            onValueChange = {
                onValueChange(
                    project.copy(note = it)
                )
            },
            label = { Text("Примечание") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                Text("Здесь может быть важная информация")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            )
        )

        timeList.forEachIndexed { index, it ->
            TimeOutlinedTextField(
                onClickUp = onClickUp,
                time = it,
                count = index,
                countTime = {
                    onClickDel(it)
                }
            )
        }

        OutlinedButton(
            onClick = { onClick() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 10.dp)
                .padding(horizontal = 20.dp),
        ) {
            Text(text = "Добавить напоминание")
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Добавить",
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Button(
            onClick = { saveInRoomAdd(errorBoolean()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_create_24),
                contentDescription = " Обновить "
            )
            Text(text = " Обновить ")
        }

        OutlinedButton(
            onClick = {
                onValueChange(project.copy(arhive = "1"))
                archivInRoom(errorBoolean())
            }, modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_archive_24),
                contentDescription = "В Архив"
            )
            Text(text = " В Архив")
        }

        OutlinedButton(
            onClick = deleteRoom,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_delete_24),
                contentDescription = "Удалить"
            )
            Text(text = " Удалить ")
        }
    }
}
