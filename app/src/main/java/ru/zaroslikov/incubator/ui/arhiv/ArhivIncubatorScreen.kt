package ru.zaroslikov.incubator.ui.arhiv

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.data.ferma.Value
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.IncubatorUiState
import ru.zaroslikov.incubator.ui.incubator.IncubatorViewModel
import ru.zaroslikov.incubator.ui.incubator.MyRowIncubatorSettting
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination


object IncubatorArhivDestination : NavigationDestination {
    override val route = "IncubatorArhiv"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemId"
    val routeWithArgs = "$route/{$itemIdArg}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorArhivScreen(
    navigateBack: () -> Unit,
    navigateStart: () -> Unit,
    viewModel: IncubatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val incubator by viewModel.incubatorUiState.collectAsState()

    val project = viewModel.itemUiState
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val modifier = Modifier
    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(contentPadding),
        topBar = {
            TopAppBarEdit(
                title = project.title,
                navigateUp = navigateBack,
                scrollBehavior = scrollBehavior
            )
        }) { innerPadding ->
        IncubatorContainer(
            modifier = modifier
                .padding(14.dp),
            contentPadding = innerPadding,
            incubator = incubator.itemList,
            projectTable = project,
            unArchiveInc = {
                coroutineScope.launch {
                    viewModel.updateUiState(project.copy(arhive = "0"))
                    viewModel.saveIncubator()
                    navigateStart()
                }
            },
            deleteInc = {
                coroutineScope.launch {
                    viewModel.deleteIncubator()
                    navigateStart()
                }
            }
        )
    }
}


@SuppressLint("SimpleDateFormat")
@Composable
fun IncubatorContainer(
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    incubator: List<Value>,
    projectTable: IncubatorUiState,
    unArchiveInc: () -> Unit,
    deleteInc: () -> Unit
) {

    LazyColumn(
        modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = contentPadding
    ) {
        item {
            IncubatorCard(
                projectTable, modifier = Modifier
                    .padding(6.dp)
            )
        }
        items(incubator.size) {
            MyRowIncubatorSettting(
                incubator[it],
                modifier = Modifier
                    .padding(6.dp),
                borderStroke = null,
                typeBird = projectTable.type,
                navigateOvos = {}
            )
        }

        item {
            OutlinedButton(
                onClick = unArchiveInc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_unarchive_24),
                    contentDescription = "Убрать из архива"
                )
                Text(text = "Убрать из архива")
            }
        }

        item {
            Button(
                onClick = deleteInc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_delete_24),
                    contentDescription = "Удалить"
                )
                Text(text = "Удалить")
            }
        }
    }
}


@Composable
fun IncubatorCard(projectTable: IncubatorUiState, modifier: Modifier) {

    val modifierText = Modifier
        .wrapContentSize()
        .padding(vertical = 3.dp, horizontal = 6.dp)

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Text(
            text = "Данные",
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Text(text = "Вид птицы: ${projectTable.type}", modifier = modifierText)
        Text(text = "Заложено яиц: ${projectTable.eggAll}", modifier = modifierText)
        Text(text = "Вылупилось птенцов: ${projectTable.eggAllEND}", modifier = modifierText)
        Text(text = "Дата закладки: ${projectTable.data}", modifier = modifierText)
        Text(text = "Дата вылупения: ${projectTable.dateEnd}", modifier = modifierText)
        Text(text = "Примечание: ${projectTable.note}", modifier = modifierText)
    }
}

