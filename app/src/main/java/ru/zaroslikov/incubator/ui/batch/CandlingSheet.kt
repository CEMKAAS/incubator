package ru.zaroslikov.incubator.ui.batch

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.R
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.LoadingSpinner
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetDraft
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

private val CardRadius = 22.dp
private val TileRadius = 16.dp
private val InnerRadius = 12.dp
private val CardBorderWidth = 0.8.dp

/** Фон снимков овоскопирования, `BG` в `tools/CandlingImages.java`. */
private val CandlingPhotoBackground = Color(0xFF0E0907)

/**
 * Овоскопирование — шторка поверх закладки: с закладкой не расставаться, когда яйца в руках.
 * Маршрута нет: идентификаторы приходят параметрами и уезжают в [CandlingIntent.Load].
 *
 * Хозяин — [BatchDetailSheet]; вызов стоит рядом с `ModalBottomSheet`, а не внутри него,
 * чтобы шторка попала в своё окно поверх.
 *
 * Макета нет: собрано из тех же кирпичей, что и остальные шторки.
 *
 * @param draft черновик: набранная выбраковка переживает сворачивание шторки, но не
 * крестик и не закрытие закладки под ней. См. [SheetDraft].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandlingSheet(
    batchId: Long,
    day: Int,
    type: String,
    /**
     * Какое это овоскопирование по счёту. Приходит от хозяина, а не считается здесь:
     * у своего вида дни овоскопирования лежат в базе, и знает их каталог в состоянии
     * закладки — заводить второе чтение ради одной цифры незачем.
     */
    stage: Int,
    draft: SheetDraft,
    onDismiss: () -> Unit,
    viewModel: CandlingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val torch = rememberTorch()
    var showResult by remember { mutableStateOf(false) }

    // Состояние перечитывается при каждом открытии, а поле выбраковки сбрасывается
    // только у отменённого черновика: свёрнутую шторку открывают ровно с тем числом,
    // которое в ней успели набрать. См. [SheetDraft].
    LaunchedEffect(batchId, day, type, stage) {
        viewModel.onIntent(
            CandlingIntent.Load(
                batchId = batchId,
                day = day,
                type = type,
                stage = stage,
                resetInput = draft.claim(batchId, day),
            )
        )
    }

    // «Записано» — эффект, а не callback у кнопки: и диалог, и шторка закрываются
    // ровно один раз, ровно тогда, когда запись состоялась.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            CandlingEffect.Saved -> {
                showResult = false
                draft.discard()
                onDismiss()
            }
        }
    }

    // Крестик — отказ, в отличие от свайпа вниз, которым шторку сворачивают.
    val close = {
        draft.discard()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Шторка во всю высоту: «Фонарик» и «Завершить» стоят под прокруткой, а не в
        // её конце, и без фиксированной высоты они прыгали бы вслед за содержимым.
        Column(modifier = Modifier.fillMaxHeight().clearFocusOnTap()) {
            Column(modifier = Modifier.padding(horizontal = SheetPadding)) {
                SheetHeader(title = "Овоскопирование", onClose = close)
            }

            CandlingContent(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SheetPadding)
                    .padding(top = 16.dp, bottom = 16.dp),
            )

            CandlingActions(
                torchOn = torch.enabled,
                torchAvailable = torch.available,
                onToggleTorch = torch::toggle,
                onFinish = {
                    // Фонарик гасим на входе в итог: яйца уже в лотке, а вспышка за
                    // время овоскопирования успевает нагреться.
                    torch.turnOff()
                    showResult = true
                },
                ready = state.loaded,
            )
        }
    }

    // Диалог — сосед шторки, а не её содержимое: иначе он лёг бы внутрь её окна.
    if (showResult) {
        CandlingResultDialog(
            state = state,
            onRejectedChange = { input ->
                viewModel.onIntent(CandlingIntent.UpdateRejected(input))
            },
            onDismiss = { showResult = false },
            onSave = { viewModel.onIntent(CandlingIntent.Save) },
        )
    }
}

/**
 * Содержимое шторки: что это за овоскопирование, эталонная картинка, порядок действий
 * и памятка.
 *
 * Порядок именно такой: сперва картинка — с ней сравнивают, ради неё шторку и открыли, —
 * и только потом текст. Пункты вместо сплошного абзаца, потому что их выполняют по
 * одному, отрываясь на яйцо между каждым.
 */
@Composable
private fun CandlingContent(
    state: CandlingState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StageCard(state)

        Spacer(Modifier.height(12.dp))
        ReferenceCard(day = state.day, stage = state.stage)

        Spacer(Modifier.height(12.dp))
        StepsCard()

        Spacer(Modifier.height(12.dp))
        RemindersCard()

        Spacer(Modifier.height(12.dp))
        DisclaimerCard()
    }
}

/**
 * Какое это овоскопирование по счёту, какой день и что на нём ищут.
 *
 * Заголовок — порядковый номер («Первое овоскопирование»), под ним день и вид птицы.
 */
@Composable
private fun StageCard(state: CandlingState) {
    CandlingCardSurface {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stageTitle(state.stage),
                style = DesignType.CardHeading,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf("День ${state.day}", state.type)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = DesignType.Caption,
                color = DesignPalette.DateEmphasis,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = stageGoal(state.stage),
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )

            state.saved?.let { saved ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(InnerRadius),
                    color = DesignPalette.InsightSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Итог за этот день уже записан: отбраковано " +
                            "${saved.rejected} ${eggsWord(saved.rejected)}. " +
                            "«Завершить» перепишет его.",
                        style = DesignType.Caption,
                        color = DesignPalette.Accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** Эталонная картинка и подпись к ней — то, с чем сравнивают яйцо на просвет. */
@Composable
private fun ReferenceCard(day: Int, stage: Int) {
    CandlingCardSurface {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(TileRadius))
                    // Цвет фона самих снимков: по бокам, где `Fit` оставляет поля, рамка
                    // продолжает тёмную комнату, а не режет её светлой полосой.
                    .background(CandlingPhotoBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = candlingImage(stage)),
                    contentDescription = "Как должно выглядеть яйцо на $day день",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "На $day день яйцо должно выглядеть так. Если нет — его нужно " +
                    "убрать из инкубатора.",
                style = DesignType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** «Как проводить» — пункты по порядку, потому что и делают их по одному. */
@Composable
private fun StepsCard() {
    CandlingCardSurface {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Как проводить",
                style = DesignType.CardHeading,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            candlingSteps.forEachIndexed { index, step ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                NumberedStep(number = index + 1, text = step)
            }
        }
    }
}

/** Номер в кружке и текст пункта рядом. */
@Composable
private fun NumberedStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(DesignPalette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = DesignType.PillLabel,
                color = DesignPalette.OnAccent,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * «Что важно помнить» — не порядок действий, а то, из-за чего страдает кладка. Поэтому
 * карточка тёплая, как предупреждения в остальном приложении, а не белая, как инструкция.
 */
@Composable
private fun RemindersCard() {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = DesignPalette.SpeciesTileSelected,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Что важно помнить",
                style = DesignType.CardHeading,
                color = DesignPalette.DateEmphasis,
            )
            Spacer(Modifier.height(12.dp))
            candlingReminders.forEachIndexed { index, note ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(DesignPalette.DateEmphasis),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = note,
                        style = DesignType.Body,
                        color = DesignPalette.DateEmphasis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Оговорка про картинку — та же, что была на прежнем экране. */
@Composable
private fun DisclaimerCard() {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ovos2),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Картинка ознакомительная: у разных пород просвет отличается. " +
                "Если сомневаетесь — сверьтесь с другими источниками.",
            style = DesignType.Note,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Нижняя полоса шторки: «Фонарик» и «Завершить».
 *
 * Под прокруткой, а не в её конце: яйцо просвечивают, держа телефон в руке, и мотать до
 * низа в этот момент нечем. Отступ от навигационной панели даёт сама `ModalBottomSheet`
 * — её `windowInsets` по умолчанию включают нижние системные полосы, — поэтому здесь
 * только собственные 12 dp.
 *
 * Фонарик — переключатель, поэтому включённый он залит, а не просто обведён: иначе по
 * кнопке не понять, горит он уже или ещё нет. На аппарате без вспышки кнопка не
 * пропадает, а гаснет: пустое место слева от «Завершить» читалось бы как сбой вёрстки.
 */
@Composable
private fun CandlingActions(
    torchOn: Boolean,
    torchAvailable: Boolean,
    onToggleTorch: () -> Unit,
    onFinish: () -> Unit,
    /**
     * Закладка прочитана — то есть известно, сколько яиц в лотке.
     *
     * Пока нет, «Завершить» гаснет: диалог итога открылся бы с «Было 0», и потолок
     * ввода тоже был бы нулём — то есть выбраковать нельзя было бы ни одного яйца.
     */
    ready: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        // Волосяная черта сверху: полоса лежит поверх прокрутки, и без неё текст
        // подъезжает под кнопки без всякой границы.
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (torchOn) {
                Button(
                    onClick = onToggleTorch,
                    enabled = torchAvailable,
                    shape = RoundedCornerShape(TileRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignPalette.DateEmphasis,
                        // Не белый: в тёмной теме [DesignPalette.DateEmphasis] сам
                        // светлый, и белая надпись на нём пропала бы. [OnAccent]
                        // подходит к обеим заливкам — белый на светлой, тёмный на тёмной.
                        contentColor = DesignPalette.OnAccent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text(text = "Фонарик горит", style = DesignType.ButtonLabel, maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = onToggleTorch,
                    enabled = torchAvailable,
                    shape = RoundedCornerShape(TileRadius),
                    border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DesignPalette.Surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text(text = "Фонарик", style = DesignType.ButtonLabel, maxLines = 1)
                }
            }

            Button(
                onClick = onFinish,
                enabled = ready,
                shape = RoundedCornerShape(TileRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = DesignPalette.OnAccent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                if (ready) {
                    Text(text = "Завершить", style = DesignType.ButtonLabel, maxLines = 1)
                } else {
                    LoadingSpinner(size = 20.dp)
                }
            }
        }
    }
}

/**
 * Итог овоскопирования: было — отбраковано — останется.
 *
 * Три числа рядом, а не одно введённое: смысл выбраковки в том, сколько яиц осталось в
 * инкубаторе, и увидеть это надо до сохранения, а не после. «Останется» пересчитывается
 * прямо во время набора.
 *
 * Выбраковка — одно число на закладку: в закладке одна порода, и делить его не по чему.
 *
 * Больше, чем было, ввести нельзя — [CandlingIntent.UpdateRejected] обрезает значение
 * по потолку. Ноль — тоже итог: «овоскопировали, всё цело» это запись, а не её
 * отсутствие, поэтому кнопка сохранения не гаснет на пустом поле. Сохранение
 * закрывает и шторку: овоскопирование на этом кончилось.
 */
@Composable
private fun CandlingResultDialog(
    state: CandlingState,
    onRejectedChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val rejectedCount = state.rejectedCount
    val canSave = state.canSave
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Итог овоскопирования", style = DesignType.SectionTitle)
        },
        text = {
            // Прокручивается, как и оба диалога завершения: с открытой клавиатурой и
            // крупным шрифтом три числа, поле и подпись в экран уже не встают.
            Column(Modifier.clearFocusOnTap().verticalScroll(scrollState)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ResultFigure(
                        label = "Было",
                        value = state.eggsBefore.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    ResultDivider()
                    ResultFigure(
                        label = "Отбраковано",
                        value = rejectedCount.toString(),
                        color = if (rejectedCount > 0) DesignPalette.Expense
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ResultDivider()
                    ResultFigure(
                        label = "Останется",
                        value = state.remaining.toString(),
                        color = DesignPalette.Accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel(text = "Сколько отбраковано")
                SheetTextField(
                    value = state.rejected,
                    onValueChange = onRejectedChange,
                    placeholder = "0",
                    numeric = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = resultHint(state),
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(
                    text = "Сохранить",
                    color = if (canSave) DesignPalette.Accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/**
 * Подпись под полями: откуда взялся потолок — или что запись этого дня уже есть.
 *
 * Про переписывание говорится раньше про потолок: это единственное, чего нельзя
 * увидеть в самих числах, и узнать об этом надо до нажатия, а не после.
 */
private fun resultHint(state: CandlingState): String = when {
    state.saved != null -> "Итог за этот день уже записан — сохранение перепишет его."
    else -> "Больше ${state.eggsBefore} ${eggsWord(state.eggsBefore)} указать нельзя: " +
        "столько сейчас в инкубаторе."
}

/** Ячейка полосы итога — то же устройство, что у полосы в сводке закладки. */
@Composable
private fun ResultFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.BottomStart) {
            Text(
                text = value,
                style = DesignType.AnalyticsValue,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = DesignType.Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(CardBorderWidth)
            .height(28.dp)
            .background(DesignPalette.CardBorder)
    )
}

/** Белая карточка со скруглением 22 и волосяной рамкой — как в шторке закладки. */
@Composable
private fun CandlingCardSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = DesignPalette.Surface,
        border = BorderStroke(CardBorderWidth, DesignPalette.CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

/** «1 яйцо», «3 яйца», «12 яиц» — итог не должен читаться как машинный вывод. */
internal fun eggsWord(count: Int): String {
    if (count % 100 in 11..14) return "яиц"
    return when (count % 10) {
        1 -> "яйцо"
        2, 3, 4 -> "яйца"
        else -> "яиц"
    }
}

/** Заголовок по номеру овоскопирования; ноль — шторку открыли в день, где его нет. */
internal fun stageTitle(stage: Int): String = when (stage) {
    1 -> "Первое овоскопирование"
    2 -> "Второе овоскопирование"
    3 -> "Третье овоскопирование"
    4 -> "Четвёртое овоскопирование"
    5 -> "Пятое овоскопирование"
    // У своего вида овоскопирований столько, сколько дней отметили в конструкторе;
    // числительным дальше пятого никто не пользуется, а цифра читается всегда.
    else -> if (stage > 5) "$stage-е овоскопирование" else "Овоскопирование"
}

/**
 * Что именно ищут на этом овоскопировании.
 *
 * Знание общее для всех видов и потому не в `:domain` рядом с `setOvoskop`: сроки у
 * курицы и гуся разные, а картина на просвет на первом, втором и третьем просмотре
 * одна и та же.
 */
internal fun stageGoal(stage: Int): String = when (stage) {
    1 -> "Проверяем, что развитие началось. В живом яйце видна сеть кровеносных " +
        "сосудов и тёмное пятно зародыша. Прозрачное яйцо без сосудов — неоплод, " +
        "красное кольцо вокруг желтка — зародыш замер."

    2 -> "Проверяем рост. Зародыш занимает уже большую часть яйца, сосуды густые, " +
        "в остром конце смыкается аллантоис. Светлое яйцо и оборванные сосуды значат, " +
        "что развитие остановилось."

    // Третье и все дальнейшие — у своего вида их может быть больше трёх, и каждое
    // после второго смотрит на то же: тёмное яйцо и растущую воздушную камеру.
    else -> "Последняя проверка перед выводом. Яйцо тёмное почти целиком, просвечивает " +
        "только воздушная камера — она стала больше, её граница неровная. Иногда видно, " +
        "как зародыш шевелится."
}

/** Порядок действий — то, что делают руками, по одному пункту за раз. */
private val candlingSteps = listOf(
    "Затемните комнату: на свету разница между живым и замершим яйцом почти не видна.",
    "Включите овоскоп или фонарик и дайте лампе разгореться.",
    "Достаньте яйцо из лотка тупым концом вверх — там воздушная камера.",
    "Приложите яйцо к свету и медленно поверните вокруг оси, разглядывая на просвет.",
    "Сравните увиденное с картинкой выше и решите: оставить или убрать.",
    "Годное яйцо верните в лоток той же стороной, отбракованное отложите и посчитайте.",
    "Закончив, нажмите «Завершить» и запишите, сколько яиц убрали.",
)

/** Памятка: то, из-за чего страдает кладка, а не порядок действий. */
private val candlingReminders = listOf(
    "Всё овоскопирование должно занять не больше 5–10 минут: яйца остывают.",
    "Руки должны быть чистыми и сухими — скорлупа пористая и впитывает всё подряд.",
    "Не трясите яйцо и не переворачивайте его резко: воздушная камера и сосуды " +
        "этого не любят.",
    "Убирать нужно неоплод, яйца с кровяным кольцом и с оборванными сосудами.",
    "Сомневаетесь — оставьте до следующего овоскопирования: выбросить успеете всегда.",
)

/**
 * Фонарик телефона: им и просвечивают, когда овоскопа под рукой нет.
 *
 * `setTorchMode` не требует разрешения с API 23, поэтому ни `CAMERA` в манифесте, ни
 * запроса у пользователя тут нет — иначе овоскопирование просило бы доступ к камере,
 * который ему не нужен.
 *
 * Гаснет фонарик сам при закрытии шторки: забытый включённым — это разряженный телефон
 * и горячая вспышка в кармане.
 */
private class TorchController(context: Context) {

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /**
     * Камера со вспышкой, по возможности задняя: у фронтальной «вспышка» на многих
     * аппаратах — это подсветка экраном, и `setTorchMode` на ней просто ничего не делает.
     */
    private val cameraId: String? = runCatching {
        val ids = manager?.cameraIdList.orEmpty()
        ids.firstOrNull { id -> manager.hasFlash(id) && manager.isBackFacing(id) }
            ?: ids.firstOrNull { id -> manager.hasFlash(id) }
    }.getOrNull()

    val available: Boolean get() = cameraId != null

    var enabled by mutableStateOf(false)
        private set

    fun toggle() = setTorch(!enabled)

    fun turnOff() {
        if (enabled) setTorch(false)
    }

    /**
     * Ошибку глотаем и возвращаем кнопку в «выключено»: камеру может держать другое
     * приложение, и падать из-за фонарика посреди овоскопирования — последнее, что
     * тут нужно.
     */
    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        val result = runCatching { manager?.setTorchMode(id, on) }
        enabled = result.isSuccess && on
    }
}

private fun CameraManager?.hasFlash(id: String): Boolean =
    this?.getCameraCharacteristics(id)?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

private fun CameraManager?.isBackFacing(id: String): Boolean =
    this?.getCameraCharacteristics(id)?.get(CameraCharacteristics.LENS_FACING) ==
        CameraCharacteristics.LENS_FACING_BACK

@Composable
private fun rememberTorch(): TorchController {
    val context = LocalContext.current
    val torch = remember(context) { TorchController(context) }
    DisposableEffect(torch) {
        onDispose { torch.turnOff() }
    }
    return torch
}

/**
 * Эталонная картинка — по номеру овоскопирования, а не по виду птицы и дню.
 *
 * Снимки отрисованы `tools/CandlingImages.java`: яйцо на лампе в тёмной комнате, цвет —
 * поглощение света скорлупой, тканями и кровью по каналам, как на настоящем овоскопе.
 * Лежат в `drawable-nodpi`: 1000 px в обычной `drawable` считались бы 1000 dp, и
 * на трёхкратном экране декодировались бы втрое больше.
 *
 * Прежняя `setOvoskopImage(day, type)` перебирала виды и дни, но выбирала из набора,
 * где `chiken1`, `goose1`, `turkeys1`, `quail1` и `duck1` были одним и тем же файлом
 * байт в байт — как и все четыре картинки третьего просмотра. Развилка по видам ничего
 * не решала: сроки у курицы и гуся разные, а яйцо на просвет в первый, второй и третий
 * раз выглядит одинаково. Что действительно различается — номер просмотра, и его уже
 * считает `ovoskopStage` в `:domain`.
 *
 * Заодно это чинит перепелов: у них день 6 показывал `quail2` — картинку **третьего**
 * овоскопирования, потому что оба их дня были выписаны на один файл.
 *
 * Живёт в `:app`, а не в `:domain`, потому что разрешается через `R`. Но добавление
 * новой птицы её больше не касается: хватает `setOvoskop` и `ovoskopStage`.
 */
@DrawableRes
internal fun candlingImage(stage: Int): Int = when (stage) {
    1 -> R.drawable.img_candling_1
    2 -> R.drawable.img_candling_2
    // Третьей картинкой показываются и четвёртое, и пятое: у своего вида
    // овоскопирований столько, сколько отметили, а картина на просвет после
    // второго просмотра уже не меняется — только темнеет.
    else -> R.drawable.img_candling_3
}
