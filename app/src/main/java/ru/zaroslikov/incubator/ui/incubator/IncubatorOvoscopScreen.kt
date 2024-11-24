package ru.zaroslikov.incubator.ui.incubator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.TopAppBarEdit
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination


object IncubatorOvoscopDestination : NavigationDestination {
    override val route = "IncubatorOvoscopScreen"
    override val titleRes = R.string.app_name
    const val itemIdArg = "itemDay"
    const val itemIdArgTwo = "itemType"
    val routeWithArgs = "$route/{$itemIdArg}/{$itemIdArgTwo}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorOvoscopScreen(
    navigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncubatorOvoscopViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).padding(contentPadding),
        topBar = {
            TopAppBarEdit(
                title = "Овоскопирование",
                navigateUp = navigateBack,
                scrollBehavior = scrollBehavior
            )
        }) { innerPadding ->
        IncubatorEditDayContainer(
            day = viewModel.dayVM,
            typeBird = viewModel.typeBirds,
            modifier = Modifier
                .padding(innerPadding)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            onNavigateUp = onNavigateUp
        )
    }

}

@Composable
fun IncubatorEditDayContainer(
    day: Int,
    typeBird: String,
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit
) {

    val image = setOvoskopImage(day, typeBird)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(400.dp)
                .clip(shape = RoundedCornerShape(20.dp))
                .background(color = Color.White)
                .padding(top = 10.dp)
        ) {
            Image(
                painter = painterResource(id = image),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Card(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = "На $day день яйцо должно выглядеть так, если нет, его нужно убрать из инкубатора",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Внимание",
                    modifier = Modifier.padding(10.dp)
                )
                Text(
                    text = "Картинка предназначена для ознакомительных целей.\nДля получения более подробной информации обратитесь к другим источникам",
                    modifier = Modifier.padding(5.dp)
                )
            }
        }



        Button(
            onClick = onNavigateUp,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Понятно")
        }
    }
}

fun setOvoskopImage(day: Int, typeBird: String): Int {
    return when (typeBird) {
        "Курицы" -> {
            when (day) {
                7 -> R.drawable.chiken1
                11 -> R.drawable.chiken2
                16 -> R.drawable.chiken3
                else -> R.drawable.chiken1
            }
        }

        "Индюки", "Утки" -> {
            when (day) {
                8 -> R.drawable.turkeys1
                14 -> R.drawable.turkeys2
                25 -> R.drawable.turkeys3
                else -> R.drawable.turkeys1
            }
        }

        "Гуси" -> {
            when (day) {
                9 -> R.drawable.goose1
                15 -> R.drawable.goose2
                21 -> R.drawable.goose3
                else -> R.drawable.goose1
            }
        }

        "Перепела" -> {
            when (day) {
                6 -> R.drawable.quail2
                13 -> R.drawable.quail2
                else -> R.drawable.quail1
            }
        }

        else -> R.drawable.chicken
    }
}