package ru.zaroslikov.incubator.ui.start

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.appmetrica.analytics.AppMetrica
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.AddIncubatorSheet
import ru.zaroslikov.incubator.ui.navigation.NavigationDestination
import ru.zaroslikov.incubator.ui.theme.DesignPalette
import ru.zaroslikov.incubator.ui.theme.DesignType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


object StartDestination : NavigationDestination {
    override val route = "Start"
    override val titleRes = R.string.app_name
}

/** Надзаголовок из макета. */
private const val EYEBROW = "МОЯ ПАСЕКА"

/** Отступ по краям экрана из макета. */
private val ScreenPadding = 20.dp

/** Запас снизу под плавающую кнопку — в макете под неё оставлено 112. */
private val FabReserve = 112.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    navigateToIncubator: (Long) -> Unit,
    /** На первом запуске форма инкубатора открывается сразу: без устройства работать не с чем. */
    openAddOnStart: Boolean = false,
    viewModel: StartScreenViewModel = viewModel(factory = AppViewModelProvider.Factory),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.getAllProjectAct.collectAsState()
    var infoBottomSheet by remember { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(openAddOnStart) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val vkGroupUrl = "https://vk.com/myfermaapp"

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = DesignPalette.Accent,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, "Добавить") },
                text = { Text(text = "Инкубатор") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 8.dp,
                bottom = FabReserve
            )
        ) {
            item {
                ScreenHeader(onInfo = {
                    infoBottomSheet = true
                    AppMetrica.reportEvent("Информация")
                })
            }

            item {
                Spacer(Modifier.height(24.dp))
                StatsRow(activeCount = uiState.activeCount, eggsInWork = uiState.eggsInWork)
                Spacer(Modifier.height(24.dp))
            }

            if (uiState.cards.isEmpty())
                item { EmptyState() }
             else
                items(items = uiState.cards, key = { it.incubator.id }) { card ->
                    IncubatorCard(
                        card = card,
                        onClick = { navigateToIncubator(card.incubator.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
        }

        if (showAddSheet) {
            AddIncubatorSheet(
                incubatorId = 0,
                onDismiss = { showAddSheet = false },
                onSaved = { id ->
                    showAddSheet = false
                    AppMetrica.reportEvent("Инкубатор создан")
                    navigateToIncubator(id)
                },
            )
        }

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

/**
 * Шапка из макета: надзаголовок и крупный заголовок.
 *
 * Иконки «инфо» в макете нет — но экрану она нужна, раз TopAppBar убран.
 * Переключатель архива отсюда ушёл: архивируются закладки, а не инкубаторы,
 * поэтому он живёт на экране закладок.
 */
@Composable
private fun ScreenHeader(onInfo: () -> Unit) {
    Column {
        Text(
            text = EYEBROW,
            style = DesignType.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Инкубаторы",
                style = DesignType.ScreenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Информация",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(activeCount: Int, eggsInWork: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            value = activeCount.toString(),
            label = "Активных закладок",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = eggsInWork.toString(),
            label = "Яиц в работе",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = DesignType.StatValue,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Карточка инкубатора: агрегаты по всем его активным закладкам. */
@Composable
fun IncubatorCard(
    card: IncubatorCardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val incubator = card.incubator
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(2.dp), onClick = onClick
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = incubator.name,
                        style = DesignType.CardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = modelLine(incubator.brand, incubator.model),
                        style = DesignType.Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right_design),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(20.dp),
                )
            }

            if (card.species.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SpeciesChips(card.species)
            }

            Spacer(Modifier.height(16.dp))
            CapacityBlock(eggs = card.eggs, capacity = incubator.capacity)

            val hatch = card.nearestHatchMillis?.let(::formatHatchDate)
            if (hatch != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Ближайший вывод — ")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Medium,
                                color = DesignPalette.DateEmphasis,
                            )
                        ) { append(hatch) }
                    },
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Круглые чипы видов, налезающие друг на друга — как в макете. */
@Composable
private fun SpeciesChips(species: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        species.forEach { bird ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(speciesChipColor(bird)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = speciesEmoji(bird), fontSize = 15.sp)
            }
        }
    }
}

/**
 * Яйца против вместимости инкубатора.
 *
 * Вместимость обязательна, но у инкубатора, созданного при миграции с первой версии,
 * её взять было неоткуда — там ноль. В этом случае вместо процента показываем просьбу
 * заполнить: полоса без вместимости бессмысленна.
 */
@Composable
private fun CapacityBlock(eggs: Int, capacity: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_egg_design),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (capacity > 0) "$eggs / $capacity мест" else "$eggs яиц",
                style = DesignType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (capacity > 0) {
            Text(
                text = "${(eggs.toFloat() / capacity * 100).roundToInt()}%",
                style = DesignType.MonoEmphasis,
                color = DesignPalette.Accent,
            )
        } else {
            Text(
                text = "укажите вместимость",
                style = DesignType.Caption,
                color = DesignPalette.DateEmphasis,
            )
        }
    }

    if (capacity > 0) {
        Spacer(Modifier.height(6.dp))
        // Полоса упирается в 100 %, хотя подпись может показывать больше:
        // в макете 108 из 72 мест — это 150 %, и полоса залита целиком.
        val fill = (eggs.toFloat() / capacity).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(DesignPalette.ProgressTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(DesignPalette.Accent)
            )
        }
    }
}

/** Пустого состояния в макете нет — текст свой. */
@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Добро пожаловать!",
            style = DesignType.CardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Добавьте инкубатор, а внутри него — закладки яиц.",
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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


/** «Бренд Модель» одной строкой; у инкубатора с миграции они пустые. */
fun modelLine(brand: String, model: String): String =
    listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { "Модель не указана" }

/** Эмодзи вида — как в макете, вместо прежних PNG-картинок птиц. */
fun speciesEmoji(bird: String): String = when (bird) {
    "Курицы" -> "🐔"
    "Гуси" -> "🪿"
    "Перепела" -> "🐦"
    "Индюки" -> "🦃"
    "Утки" -> "🦆"
    else -> "🥚"
}

fun speciesChipColor(bird: String): Color = when (bird) {
    "Курицы" -> DesignPalette.ChipChicken
    "Гуси" -> DesignPalette.ChipGoose
    "Перепела" -> DesignPalette.ChipQuail
    "Индюки" -> DesignPalette.ChipTurkey
    "Утки" -> DesignPalette.ChipDuck
    else -> DesignPalette.ChipQuail
}

fun formatHatchDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale("ru")).format(Date(millis))
