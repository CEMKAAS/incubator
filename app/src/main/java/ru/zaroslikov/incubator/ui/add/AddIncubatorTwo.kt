package ru.zaroslikov.incubator.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.data.ferma.Time
import ru.zaroslikov.incubator.data.ferma.Value


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIncubatorContainerTwo(
    name: String,
    navigateBack: () -> Unit,
    navigateContinue: (MutableList<Value>) -> Unit,
    list: MutableList<Value>,
    firstLaunch:Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val modifier = Modifier
    var openFirstDialog by rememberSaveable { mutableStateOf(firstLaunch) }

    if (openFirstDialog) {
        AlertDialogExample(
            onDismissRequest = { openFirstDialog = false },
            onConfirmation = {
                openFirstDialog = false
            },
            dialogTitle = "Справка!",
            dialogText = "Если показания вашего инкубатора отклоняются от нормы, вы можете настроить температуру вручную или использовать рекомендованные параметры, которые можно корректировать в процессе работы." +
                    " Обязательно ведите ежедневный журнал и аккуратно записывайте все данные, чтобы потом можно было легко найти их в архиве.",
            icon = Icons.Default.Info
        )
    }


    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarEdit(title = name, navigateUp = navigateBack, scrollBehavior = scrollBehavior)
        }
    ) { innerPadding ->
        AddIncubatorTwoContaine(
            modifier = modifier
                .padding(innerPadding),
            list = list,
            navigateContinue = navigateContinue
        )
    }
}


@Composable
fun AddIncubatorTwoContaine(
    modifier: Modifier,
    navigateContinue: (MutableList<Value>) -> Unit,
    list: MutableList<Value>
) {

    LazyColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Card(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Внимание",
                        modifier = Modifier.padding(10.dp)
                    )
                    Text(
                        text = "Значения в таблице являются ориентировочными. Корректируйте её по своему усмотрению, в будущем значения могут быть изменены.",
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        item { HorizontalDivider(thickness = 1.dp, color = Color.DarkGray) }
        item {
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "День",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.15f)
                        .padding(6.dp)
                )
                VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
                Text(
                    text = "°C",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.16f)
                        .padding(6.dp)
                )
                VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
                Text(
                    text = "%",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.16f)
                        .padding(6.dp)
                )
                VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
                Text(
                    text = "Поворот",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.34f)
                        .padding(6.dp)
                )
                VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
                Text(
                    text = "Проветривание",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                )
            }
        }
        item { HorizontalDivider(thickness = 2.dp, color = Color.DarkGray) }
        items(list.size) { MyRowIncubatorAdd(list[it]) }
        item {
            Button(
                onClick = { navigateContinue(list) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 14.dp)
            ) {
                Text(text = "Запустить")
            }
        }
    }
}


@Composable
fun MyRowIncubatorAdd(
    incubator: Value
) {
    var tempDay by remember { mutableStateOf(incubator.temp) }
    var dampDay by remember { mutableStateOf(incubator.damp) }
    var overDay by remember { mutableStateOf(incubator.over) }
    var airingDay by remember { mutableStateOf(incubator.airing) }

    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = incubator.day.toString(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.15f)
                .padding(6.dp),
        )
        VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
        BasicTextField(
            value = tempDay,
            onValueChange = {
                tempDay = it.replace(Regex("[^\\d.]"), "").replace(",", ".")
                incubator.temp = it
            },
            textStyle = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth(0.16f)
                .padding(6.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Number
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            }
            )
        )
        VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
        BasicTextField(
            value = dampDay,
            onValueChange = {
                dampDay = it.replace(Regex("[^\\d.]"), "").replace(",", ".")
                incubator.damp = it
            },
            textStyle = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth(0.16f)
                .padding(6.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Number,
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            }
            )
        )
        VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
        BasicTextField(
            value = overDay,
            onValueChange = {
                overDay = it
                incubator.over = it
            },
            textStyle = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth(0.34f)
                .padding(6.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            }
            )
        )
        VerticalDivider(thickness = 1.dp, color = Color.DarkGray)
        BasicTextField(
            value = airingDay,
            onValueChange = {
                airingDay = it
                incubator.airing = it
            },
            textStyle = TextStyle(textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(
                    FocusDirection.Down
                )
            }
            )
        )

    }
    HorizontalDivider(thickness = 1.dp, color = Color.DarkGray)
}


fun setIncubator(typeIncubator: String): MutableList<Value> {
    val incubator: MutableList<Value> = mutableListOf()
    when (typeIncubator) {
        "Курицы" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 5 минут",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.3",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = "37.3",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = "37.0",
                    damp = "70",
                    over = "нет",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = "37.0",
                    damp = "70",
                    over = "нет",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = "37.0",
                    damp = "70",
                    over = "нет",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Гуси" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "38.0",
                    damp = "65",
                    over = "3-4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "37.8",
                    damp = "65",
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "37.8",
                    damp = "65",
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "37.6",
                    damp = "70",
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "37.6",
                    damp = "70",
                    over = "6",
                    airing = "1 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "37.6",
                    damp = "70",
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "37.6",
                    damp = "70",
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.6",
                    damp = "70",
                    over = "6",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.6",
                    damp = "70",
                    over = "10",
                    airing = "2 раз по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = "37.3",
                    damp = "75",
                    over = "10",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = "37.3",
                    damp = "75",
                    over = "нет",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 29,
                    temp = "37.3",
                    damp = "75",
                    over = "нет",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 30,
                    temp = "37.3",
                    damp = "75",
                    over = "нет",
                    airing = "3 раза по 45 мин",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }


        "Перепела" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "38.0",
                    damp = "55",
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "38.0",
                    damp = "55",
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.7",
                    damp = "55",
                    over = "3-6",
                    airing = "1 раз по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.5",
                    damp = "37,5",
                    over = "3-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.5",
                    damp = "37,5",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.5",
                    damp = "37,5",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Индюки" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "38.0",
                    damp = "60",
                    over = "6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.7",
                    damp = "45",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.7",
                    damp = "65",
                    over = "6",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = "37.5",
                    damp = "65",
                    over = "4",
                    airing = "4 раза по 10 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = "37.5",
                    damp = "65",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = "37.5",
                    damp = "65",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = "37.5",
                    damp = "65",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        "Утки" -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "38.0",
                    damp = "75",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "37.8",
                    damp = "60",
                    over = "4",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.8",
                    damp = "60",
                    over = "4-6",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 22,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 23,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 24,
                    temp = "37.8",
                    damp = "60",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 25,
                    temp = "37.5",
                    damp = "90",
                    over = "6",
                    airing = "2 раза по 15 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 26,
                    temp = "37.5",
                    damp = "90",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 27,
                    temp = "37.5",
                    damp = "90",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 28,
                    temp = "37.5",
                    damp = "90",
                    over = "нет",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }

        else -> {
            incubator.add(
                Value(
                    day = 1,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 2,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 3,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 4,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 5,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 6,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 7,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 8,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 9,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 10,
                    temp = "37.9",
                    damp = "66",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 11,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "нет",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 12,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 13,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 14,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 15,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 16,
                    temp = "37.5",
                    damp = "60",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 17,
                    temp = "37.3",
                    damp = "47",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 18,
                    temp = "37.3",
                    damp = "47",
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 19,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 20 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 20,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            incubator.add(
                Value(
                    day = 21,
                    temp = "37.0",
                    damp = "70",
                    over = "2-3",
                    airing = "2 раза по 5 мин",
                    note = "",
                    idPT = 0
                )
            )
            return incubator
        }
    }
}

fun setIdPT(list: MutableList<Value>, idPT: Long): MutableList<Value> {
    list.forEach {
        it.id = 0
        it.idPT = idPT
    }
    return list
}

fun setIdPTTime(list: MutableList<Time>, idPT: Long): MutableList<Time> {
    list.forEach {
        it.id = 0
        it.idPT = idPT
    }
    return list
}

fun setAutoIncubator(
    list: MutableList<Value>,
    airing: Boolean,
    over: Boolean
): MutableList<Value> {

    if (airing) list.forEach { it.airing = "Авто" }
    if (over) list.forEach { it.over = "Авто" }

    return list
}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText, textAlign = TextAlign.Justify)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Отлично!")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArhivIncubatorChoice(
    openDialog: () -> Unit,
    projectList: List<IncubatorTable>,
    incubatorArh: (Long) -> Unit,
    incubatorArh2: () -> Unit
) {

    BasicAlertDialog(
        onDismissRequest = openDialog,
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
            Text(
                "Выбрать данные из архива?",
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                fontSize = 19.sp
            )
            Text(
                "Введённые Вами данные останутся неизменными. Значения температуры, влажности, поворота и проветривания будут взяты из выбранного архива.",
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
                textAlign = TextAlign.Justify
            )
            Spacer(modifier = Modifier.padding(vertical = 10.dp))

            val (selectedOption, onOptionSelected) = remember { mutableStateOf(projectList[0]) }

            incubatorArh(selectedOption.id)
            Column(Modifier.selectableGroup()) {
                projectList.forEach { text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (text == selectedOption),
                                onClick = { onOptionSelected(text) },
                                role = Role.RadioButton
                            )
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (text == selectedOption),
                            onClick = null
                        )
                        Text(text.title, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = openDialog, modifier = Modifier.padding(8.dp)
                ) {
                    Text("Отмена")
                }

                TextButton(
                    onClick = { incubatorArh2() },
                    modifier = Modifier.padding(8.dp)
                )
                { Text("Выбрать") }
            }
        }
    }
}
