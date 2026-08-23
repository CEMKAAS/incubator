package ru.zaroslikov.incubator.ui.batch

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarStart
import ru.zaroslikov.incubator.domain.incubation.isIncubationFinished
import ru.zaroslikov.incubator.domain.incubation.setOvoskop
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit


object BatchDestination : NavigationDestination {
    override val route = "Batch"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemId"
    val routeWithArgs = "$route/{$itemIdArg}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigateDayEdit: (Pair<Long, Int>) -> Unit,
    navigateOvos: (Pair<Int, String>) -> Unit,
    navigateStart: () -> Unit,
    viewModel: BatchViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val incubator by viewModel.incubatorUiState.collectAsState()
    val project = viewModel.itemUiState
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showEditSheet by remember { mutableStateOf(false) }

    // Правка закладки — та же шторка, что и создание (макет 12:3555): отдельного
    // экрана у неё больше нет, поэтому её хозяин — экран самой закладки.
    if (showEditSheet && project.id != 0L) {
        AddBatchSheet(
            incubatorId = project.incubatorId,
            batchId = project.id,
            onDismiss = { showEditSheet = false },
            onSaved = {
                showEditSheet = false
                viewModel.reload()
            },
            onRemoved = {
                showEditSheet = false
                navigateBack()
            },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarStart(
                title = project.title,
                true,
                scrollBehavior = scrollBehavior,
                navigateUp = navigateBack,
                settingUp = { showEditSheet = true }
            )
        }
    ) { innerPadding ->

        BatchDaysContainer(
            modifier = modifier
                .fillMaxSize(),
            contentPadding = innerPadding,
            incubator = incubator.itemList,
            projectTable = project,
            onValueChange = viewModel::updateUiState,
            navigateDayEdit = navigateDayEdit,
            navigateOvos = navigateOvos,
            saveInEnd = { val eventParameters: MutableMap<String, Any> = HashMap()
                eventParameters["Имя"] = project.title
                eventParameters["Тип"] = project.type
                eventParameters["Кол-во"] = project.eggAll
                eventParameters["Кол-во пос"] = project.eggAllEND
                incubator.itemList.forEachIndexed { index, value ->
                    eventParameters[index.toString()] = value
                }
                AppMetrica.reportEvent("Полное завершение", eventParameters);

                coroutineScope.launch {
                    viewModel.saveBatch()
                    navigateStart()
                }},
            saveInArh = {
                coroutineScope.launch {
                    viewModel.saveBatch()
                    navigateStart()
                }
            },
            deleteInc = {
                coroutineScope.launch {
                    viewModel.deleteBatch()
                    navigateStart()
                }
            }
        )
    }
}


@SuppressLint("SimpleDateFormat")
@Composable
fun BatchDaysContainer(
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    incubator: List<Value>,
    projectTable: BatchUiState,
    onValueChange: (BatchUiState) -> Unit = {},
    navigateDayEdit: (Pair<Long, Int>) -> Unit,
    navigateOvos: (Pair<Int, String>) -> Unit,
    saveInEnd: () -> Unit,
    saveInArh: () -> Unit,
    deleteInc: () -> Unit,
) {
    val scrollState = rememberLazyListState()
    val myFormat = SimpleDateFormat("dd.MM.yyyy")
    val calendar = Calendar.getInstance()

    val openEndDialog = remember { mutableStateOf(false) }
    var endBoolean by remember { mutableStateOf(false) }

    if (openEndDialog.value) {
        EndIncubator(
            openEndDialog = openEndDialog,
            endAdvance = endBoolean,
            projectTable = projectTable,
            onValueChange = onValueChange,
            saveInEnd = saveInEnd,
            saveInArh = saveInArh,
            deleteInc = deleteInc,
            dateEnd = myFormat.format(calendar.timeInMillis)
        )
    }

    var day by rememberSaveable { mutableIntStateOf(0) }

    if (projectTable.data != "") {
        LaunchedEffect(Unit) {
            val dateBefore: Date = myFormat.parse(projectTable.data)
            val dateAfter: Date = myFormat.parse(myFormat.format(calendar.time))
            day = (TimeUnit.DAYS.convert(
                dateAfter.time - dateBefore.time,
                TimeUnit.MILLISECONDS
            )).toInt()
            scrollState.scrollToItem(day)
            endBoolean = isIncubationFinished(projectTable.type, day)
            if (endBoolean) openEndDialog.value = true
        }
    }

    LazyColumn(
        state = scrollState,
        modifier = modifier
            .padding(horizontal = 14.dp),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        items(incubator.size) {
            val borderStroke = if (day == it) BorderStroke(2.dp, Color.Black) else null
            MyRowIncubatorSettting(
                incubator[it],
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .clickable { navigateDayEdit(Pair(projectTable.id, it + 1)) },
                borderStroke = borderStroke,
                typeBird = projectTable.type,
                navigateOvos = { navigateOvos(Pair(it + 1, projectTable.type)) }
            )
        }

        item {
            Button(
                onClick = { openEndDialog.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(text = "Завершить")
            }
        }
    }
}


@Composable
fun MyRowIncubatorSettting(
    incubator: Value,
    modifier: Modifier = Modifier,
    borderStroke: BorderStroke?,
    typeBird: String,
    navigateOvos: () -> Unit,
) {
    var ovoscop by rememberSaveable { mutableStateOf(false) }
    ovoscop = setOvoskop(typeBird, incubator.day)

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(),
        border = borderStroke
    ) {
        Text(
            text = "День ${incubator.day}",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(if (ovoscop) 0.85f else 1f)
            ) {
                Text(
                    text = "Температура: ${incubator.temp.formatTempOrDash()}°C",
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .padding(6.dp)
                )
                Text(
                    text = "Влажность: ${incubator.damp.formatDampOrDash()}%",
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .padding(6.dp)
                )

                Text(
                    text = "Переворачивать: ${incubator.over}",
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .padding(6.dp)
                )
                Text(
                    text = "Проветривать: ${incubator.airing}",
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .padding(6.dp)
                )

                if (incubator.note != "") {
                    Text(
                        text = "Примечание: ${incubator.note}",
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .padding(6.dp)
                    )
                }
            }
            if (ovoscop) {
                IconButton(onClick = navigateOvos) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ovos2),
                        contentDescription = "Овоскоп",
                        modifier = Modifier.size(36.dp)

                    )
                }
            }
        }
    }
}

@Composable
fun EndIncubator(
    openEndDialog: MutableState<Boolean>,
    endAdvance: Boolean,
    projectTable: BatchUiState,
    onValueChange: (BatchUiState) -> Unit = {},
    dateEnd: String,
    saveInEnd: () -> Unit,
    saveInArh: () -> Unit,
    deleteInc: () -> Unit,
) {
    var isErrorCount by rememberSaveable { mutableStateOf(false) }
    fun validateCount(text: String) {
        isErrorCount = text == ""
    }

    fun errorBoolean(): Boolean {
        isErrorCount = projectTable.eggAllEND == ""
        return !isErrorCount
    }

    Dialog(
        onDismissRequest = { openEndDialog.value = false },
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

                if (endAdvance) {
                    Text(
                        "Поздравлем с появлением птенцов!",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Закладка будет добавлена в архив!\n"
                                + "Сколько птенцов у Вас вылупилось из ${projectTable.eggAll} яиц?",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
                        fontSize = 15.sp, textAlign = TextAlign.Justify
                    )

                    OutlinedTextField(
                        value = projectTable.eggAllEND,
                        onValueChange = {
                            onValueChange(
                                projectTable.copy(
                                    eggAllEND = it.replace(
                                        Regex("[^\\d.]"),
                                        ""
                                    ).replace(",", ".")
                                )
                            )
                            validateCount(projectTable.eggAllEND)
                        },
                        label = { Text("Кол-во птенцов") },
                        modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                        suffix = { Text(text = "Шт.") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ovos),
                                contentDescription = "Птенцы"
                            )
                        },
                        supportingText = {
                            if (isErrorCount) {
                                Text(
                                    text = "Не указано кол-во яиц",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        isError = isErrorCount
                    )

                    TextButton(
                        onClick = {
                            if (errorBoolean()) {
                                openEndDialog.value = false
                                onValueChange(
                                    projectTable.copy(
                                        arhive = "1",
                                        dateEnd = dateEnd
                                    )
                                )
                                saveInEnd()
                            }
                        },
                        modifier = Modifier.padding(4.dp),
                    ) { Text("Завершить") }

                } else {
                    Text(
                        "Завершить ${projectTable.title}?",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Сейчас еще рано,чтобы завершать закладку.\nВы можете удалить или добавить в архив, если Вам в будующем нужны будут эти данные",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
                        fontSize = 15.sp,
                    )
                    Spacer(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val eventParameters: MutableMap<String, Any> = HashMap()
                        eventParameters["Имя"] = projectTable.title
                        eventParameters["Тип"] = projectTable.type
                        eventParameters["Кол-во"] = projectTable.eggAll
                        eventParameters["Дата начала"] = projectTable.data
                        eventParameters["Дата кончания"] = dateEnd
                        TextButton(
                            onClick = {
                                openEndDialog.value = false
                                deleteInc()
                                AppMetrica.reportEvent("Досрочное удаление", eventParameters)
                            },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text("Удалить")
                        }

                        TextButton(
                            onClick = {
                                openEndDialog.value = false
                                onValueChange(projectTable.copy(arhive = "1", dateEnd = dateEnd))
                                saveInArh()
                                AppMetrica.reportEvent("Досрочное завершение", eventParameters)
                            },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text("В архив")
                        }
                    }
                }
            }
        }
    }
}

