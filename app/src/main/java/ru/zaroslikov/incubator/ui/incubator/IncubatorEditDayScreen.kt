package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination


object IncubatorEditDayScreenDestination : NavigationDestination {
    override val route = "IncubatorEditDayScreen"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemId"
    const val itemIdArgTwo = "itemCategory"
    val routeWithArgs = "$route/{$itemIdArg}/{$itemIdArgTwo}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorEditDayScreen(
    navigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: IncubatorEditDayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val modifier = Modifier
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarEdit(
                scrollBehavior = scrollBehavior,
                title = "День ${viewModel.day}",
                navigateUp = navigateBack,
            )
        }
    ) { innerPadding ->
        IncubatorEditDayContainer(
            incubator = viewModel.incubatorState,
            modifier = Modifier
                .padding(innerPadding)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            saveDay = {
                coroutineScope.launch {
                    viewModel.saveEdit()
                    onNavigateUp()
                }
            },
            onValueChange = viewModel::updateUiState
        )
    }
}

@Composable
fun IncubatorEditDayContainer(
    incubator: ValueUiState,
    modifier: Modifier = Modifier,
    saveDay: () -> Unit,
    onValueChange: (ValueUiState) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = incubator.temp,
            onValueChange = {
                onValueChange(
                    incubator.copy(temp = it.replace(Regex("[^\\d.]"), "").replace(",", "."))
                )
            },
            label = { Text("Температура") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                Text("Укажите температуру")
            },
            suffix = { Text(text = "°C") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(
                        FocusDirection.Down
                    )
                }
            )
        )
        OutlinedTextField(
            value = incubator.damp.replace(Regex("[^\\d.]"), "").replace(",", "."),
            onValueChange = {
                onValueChange(
                    incubator.copy(damp = it)
                )
            },
            label = { Text("Влажность") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                Text("Укажите влажность")
            },
            suffix = { Text(text = "%") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(
                        FocusDirection.Down
                    )
                }
            )
        )
        OutlinedTextField(
            value = incubator.over,
            onValueChange = {
                onValueChange(
                    incubator.copy(over = it)
                )
            },
            label = { Text("Переворот") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                Text("Укажите кол-во переворачиваний")
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(
                        FocusDirection.Down
                    )
                }
            )
        )
        OutlinedTextField(
            value = incubator.airing,
            onValueChange = {
                onValueChange(
                    incubator.copy(airing = it)
                )
            },
            label = { Text("Проветривание") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            supportingText = {
                Text("Укажите кол-во проветриваний")
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(
                        FocusDirection.Down
                    )
                }
            )
        )
        OutlinedTextField(
            value = incubator.note,
            onValueChange = {
                onValueChange(
                    incubator.copy(note = it)
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

        Button(
            onClick = saveDay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_create_24),
                contentDescription = " Обновить "
            )
            Text(text = "Обновить")
        }
    }
}
