package ru.zaroslikov.incubator.ui.start

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarStart2
import ru.zaroslikov.incubator.data.ferma.IncubatorTable
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit


object StartDestination : NavigationDestination {
    override val route = "Start"
    override val titleRes = R.string.app_name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    navigateToItemAdd: () -> Unit,
    navigateToItemIncubator: (Long) -> Unit,
    navigateToItemIncubatorArh: (Long) -> Unit,
    viewModel: StartScreenViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var expanded by remember { mutableStateOf(false) }
    val incubatorList by viewModel.getAllProjectAct.collectAsState()
    var infoBottomSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val context = LocalContext.current
    val vkGroupUrl = "https://vk.com/myfermaapp"
    val modifier = Modifier

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarStart2(
                title = "Инкубатор",
                infoBottomSheet = {
                    infoBottomSheet = true
                    AppMetrica.reportEvent("Информация")
                },
                scrollBehavior = scrollBehavior,
                archiveButton = {
                    expanded = !expanded
                },
                boolean = expanded
            )
        }, floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = navigateToItemAdd,
                icon = { Icon(Icons.Filled.Add, "Добавить") },
                text = { Text(text = "Добавить") }
            )
        }
    ) { innerPadding ->
        StartScreenContainer(
            modifier = modifier.fillMaxWidth(),
            contentPadding = innerPadding,
            incubatorList = incubatorList.projectList,
            navigateToItemIncubator = navigateToItemIncubator,
            navigateToItemIncubatorArh = navigateToItemIncubatorArh,
            boolean = expanded
        )

        if (infoBottomSheet) {
            InfoBottomSheet(
                infoBottomSheet = { infoBottomSheet = false },
                infoButtonSheet = {
                    AppMetrica.reportEvent("Переход в группу")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(vkGroupUrl))
                    context.startActivity(intent)
                    infoBottomSheet = false
                },
                sheetState = sheetState
            )
        }
    }
}

@Composable
fun StartScreenContainer(
    modifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    navigateToItemIncubator: (Long) -> Unit,
    navigateToItemIncubatorArh: (Long) -> Unit,
    incubatorList: List<IncubatorTable>,
    boolean: Boolean
) {
    if (incubatorList.isNotEmpty()) {
        LazyColumn(
            modifier = modifier.padding(horizontal = 14.dp),
            contentPadding = contentPadding
        ) {
            items(items = incubatorList, key = { it.id }) { item ->
                if (boolean) {
                    IncubatorCard(
                        incubator = item, modifier = Modifier
                            .clickable {
                                if (item.arhive == "0") navigateToItemIncubator(item.id) else navigateToItemIncubatorArh(
                                    item.id
                                )
                            },
                        colorFilter = if (item.arhive == "0") null else ColorFilter.tint(Color.Gray)
                    )
                } else {
                    if (item.arhive == "0") {
                        IncubatorCard(
                            incubator = item, modifier = Modifier
                                .clickable { navigateToItemIncubator(item.id) },
                            colorFilter = null
                        )
                    }
                }
            }
        }

    } else {
        Column (modifier.padding(contentPadding).padding(14.dp)){
            Text(
                text = "Добро пожаловать!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                fontSize = 20.sp,
            )
            Text(
                text = "Для начала работы добавьте инкубатор!",
                textAlign = TextAlign.Justify,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                fontSize = 20.sp,
            )
            Text(
                text = "Нажмите + чтобы добавить",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 20.sp,
            )
        }
    }
}




@Composable
fun IncubatorCard(
    incubator: IncubatorTable,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter?
) {
    Card(
        modifier = modifier.padding(vertical = 7.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            val image = setImageAndDayIncubatorCard(incubator)

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = image.first),
                        contentDescription = "delete",
                        colorFilter = colorFilter
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text(
                            text = incubator.title,
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(6.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Row {
                            Icon(imageVector = Icons.Default.DateRange, contentDescription = "Дата")
                            Text(
                                text = incubator.data,
                                modifier = Modifier
                                    .wrapContentSize()
                                    .padding(vertical = 3.dp, horizontal = 6.dp)
                            )
                        }

                    }
                }
            }
            Text(
                text = image.second,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBottomSheet(
    infoBottomSheet: () -> Unit,
    infoButtonSheet: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = infoBottomSheet,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Инкубатор v1.00",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Присоединяйcя к нашей группе ВКонтакте! Это отличный способ оставаться в курсе новостей, делиться впечатлениями и предлагать идеи!",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                fontSize = 15.sp,
                textAlign = TextAlign.Justify
            )

            Button(
                onClick = infoButtonSheet, modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)

            ) {
                Text(text = "Присоединиться!")
            }
        }
    }
}


fun setImageAndDayIncubatorCard(incubatorTable: IncubatorTable): Pair<Int, String> {

    val myFormat = SimpleDateFormat("dd.MM.yyyy")
    val calendar = Calendar.getInstance()

    val image = when (incubatorTable.type) {
        "Курицы" -> R.drawable.chicken
        "Гуси" -> R.drawable.external_goose_birds_icongeek26_outline_icongeek26
        "Перепела" -> R.drawable.quail
        "Утки" -> R.drawable.duck
        "Индюки" -> R.drawable.turkeycock
        else -> R.drawable.chicken
    }

    val day = if (incubatorTable.arhive == "0") {
        val dateBefore: Date = myFormat.parse(incubatorTable.data)
        val dateAfter: Date = myFormat.parse(myFormat.format(calendar.time))
        "${TimeUnit.DAYS.convert(dateAfter.time - dateBefore.time, TimeUnit.MILLISECONDS) + 1} день"
    } else "Завершён"

    return Pair(image, day)
}



