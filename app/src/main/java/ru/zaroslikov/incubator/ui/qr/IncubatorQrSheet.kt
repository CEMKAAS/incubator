package ru.zaroslikov.incubator.ui.qr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.design.components.ChoiceChip
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.LoadingBox
import ru.zaroslikov.incubator.design.components.SheetDragHandle
import ru.zaroslikov.incubator.design.components.SheetHeader
import ru.zaroslikov.incubator.design.components.SheetPadding
import ru.zaroslikov.incubator.design.components.TruncatedText
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.qr.QrModules
import ru.zaroslikov.incubator.qr.appIconBitmap
import ru.zaroslikov.incubator.qr.logoLayout
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.ui.start.modelLine

private val CardRadius = 22.dp

/**
 * Смена режима: примечание раскрывается под названием, подсказка — под карточкой. Та же
 * длительность, что у ряда фильтров и сетки видов: это то же движение — блок приходит и уходит.
 */
private const val ModeSwitchMillis = 180

/** Сторона кода на экране: читается с руки и не занимает всю шторку. Кнопки в кадре держит не она, а неподвижный низ шторки. */
private val QrSideMax = 260.dp

/** Сторона растра значка для экрана — стандартный xxxhdpi-размер лаунчер-иконки. */
private const val LogoPx = 192

/**
 * Шторка «QR-код инкубатора»: код, который печатают и клеят на прибор. Камера телефона или сканер
 * приложения открывает по нему «Замеры за сегодня» (см. `QrLink`). Хозяин шторки — экран: инкубатора
 * (из шапки) и главный (из меню карточки).
 *
 * Код на **белой** карточке с чёрными модулями в любой теме: его фотографируют и печатают. Под
 * кодом — название и модель, чтобы не перепутать наклейки. Две кнопки — «сохранить» и «отправить» —
 * как у файла расписания и копии базы: это разные намерения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncubatorQrSheet(
    incubatorId: Long,
    onDismiss: () -> Unit,
    viewModel: IncubatorQrViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val send = viewModel::onIntent
    val context = LocalContext.current
    // Логотип посреди кода — иконка с рабочего стола; в пределах установки она одна, и
    // пересобирать растр на каждой перерисовке незачем.
    val logo = remember(context.packageName) {
        appIconBitmap(context, LogoPx).asImageBitmap()
    }

    // Итог записи: «сохранено» либо почему нет. Свой диалог, как у файла расписания.
    // `rememberSaveable`: сообщение приходит эффектом, один раз, и заново его не получить —
    // поворот не должен уносить «не удалось» прежде, чем его прочли.
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(incubatorId) {
        send(IncubatorQrIntent.Load(incubatorId))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null) send(IncubatorQrIntent.SaveTo(uri))
    }

    CollectEffects(viewModel) { effect ->
        when (effect) {
            IncubatorQrEffect.Saved -> result = "Картинка с QR-кодом сохранена."
            is IncubatorQrEffect.ReadyToShare -> shareQrImage(context, effect.uri)
            is IncubatorQrEffect.Failed -> result = effect.message
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { SheetDragHandle() },
    ) {
        // Шапка и кнопки неподвижны, прокручивается только середина — код и пояснения.
        // Кнопки — то, ради чего шторку открыли, и на невысоком экране (или с крупным
        // шрифтом) они не должны уходить под сгиб: `weight(1f, fill = false)` отдаёт
        // середине ровно остаток высоты окна, а пока всё помещается, не растягивает её.
        Column(
            modifier = Modifier
                .padding(horizontal = SheetPadding)
                .padding(bottom = 32.dp)
        ) {
            SheetHeader(title = "QR-код инкубатора", onClose = onDismiss)

            val modules = state.modules
            if (!state.loaded || modules == null) {
                FormSpacer(20.dp)
                LoadingBox()
                return@Column
            }

            val incubator = state.incubator
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                FormSpacer(16.dp)
                val note = incubator?.note?.trim().orEmpty()
                // Без примечания «Подробно» нечего добавить к «Кратко», и выбор между двумя
                // одинаковыми карточками — не выбор: переключателя тогда нет вовсе.
                AnimatedVisibility(
                    visible = note.isNotEmpty(),
                    enter = expandVertically(tween(ModeSwitchMillis), expandFrom = Alignment.Top) +
                        fadeIn(tween(ModeSwitchMillis)),
                    exit = shrinkVertically(tween(ModeSwitchMillis), shrinkTowards = Alignment.Top) +
                        fadeOut(tween(ModeSwitchMillis)),
                ) {
                    Column {
                        QrModeRow(mode = state.mode, onSelect = { send(IncubatorQrIntent.SetMode(it)) })
                        FormSpacer(12.dp)
                    }
                }
                QrCard(
                    modules = modules,
                    logo = logo,
                    title = incubator?.name.orEmpty(),
                    subtitle = incubator?.let { modelLine(it.brand, it.model) }.orEmpty(),
                    note = if (state.effectiveMode == QrMode.Detailed) note else "",
                )

                FormSpacer(16.dp)
                Text(
                    text = "Распечатайте код и наклейте на инкубатор. Наведите на него камеру " +
                        "телефона или сканер в приложении — сразу откроются «Замеры за сегодня» " +
                        "этого инкубатора.",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormSpacer(8.dp)
                Text(
                    text = "Код привязан к инкубатору в этом приложении: переименование его не " +
                        "меняет, а на другом телефоне он не откроется.",
                    style = DesignType.Note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FormSpacer(20.dp)
            Button(
                onClick = {
                    Analytics.report(Events.QR_EXPORT, mapOf("Режим" to modeLabel(state.effectiveMode)))
                    saveLauncher.launch(
                        IncubatorQrViewModel.fileNameFor(incubator?.name.orEmpty())
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.Accent,
                    contentColor = DesignPalette.OnAccent,
                ),
            ) {
                Text("Сохранить картинку", style = DesignType.ButtonLabel)
            }
            FormSpacer(8.dp)
            Button(
                onClick = {
                    Analytics.report(Events.QR_SHARE, mapOf("Режим" to modeLabel(state.effectiveMode)))
                    send(IncubatorQrIntent.Share)
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesignPalette.IncomeSurface,
                    contentColor = DesignPalette.Accent,
                ),
            ) {
                Text(
                    text = "Отправить в другое приложение",
                    style = DesignType.ButtonLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    result?.let { text ->
        // Заголовок — по тексту: удача формулируется одной фразой, а всё остальное начинается
        // с «Не удалось».
        val title = if (text.startsWith("Не удалось")) "Не получилось" else "Готово"
        AlertDialog(
            onDismissRequest = { result = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                Text(
                    text = title,
                    style = DesignType.SheetTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = text,
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { result = null }) {
                    Text(text = "Понятно", style = DesignType.ButtonLabel, color = DesignPalette.Accent)
                }
            },
        )
    }
}

private fun modeLabel(mode: QrMode): String = when (mode) {
    QrMode.Compact -> "кратко"
    QrMode.Detailed -> "подробно"
}

/**
 * «Кратко» / «Подробно» — два равных чипа на всю ширину, как градусы в «Настройках».
 * Чипы, а не `SlidingTabSwitcher`: тому нужен значок на вкладку, а здесь выбор — это слова.
 */
@Composable
private fun QrModeRow(mode: QrMode, onSelect: (QrMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ChoiceChip(
            text = "Кратко",
            selected = mode == QrMode.Compact,
            onClick = { onSelect(QrMode.Compact) },
            modifier = Modifier.weight(1f),
        )
        ChoiceChip(
            text = "Подробно",
            selected = mode == QrMode.Detailed,
            onClick = { onSelect(QrMode.Detailed) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Белая карточка с кодом и подписью — то, что увидит камера и что уйдёт на печать.
 * [note] непуст только в режиме «Подробно»: под чертой, с заголовком, целиком — на
 * бумаге его не дочитать по нажатию, и на экране оно показано так же, как будет напечатано.
 */
@Composable
private fun QrCard(modules: QrModules, logo: ImageBitmap, title: String, subtitle: String, note: String) {
    Card(
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.8.dp, DesignPalette.CardBorder),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrImage(
                modules = modules,
                logo = logo,
                // Сначала потолок, потом заполнение: в обратном порядке `fillMaxWidth`
                // зафиксировал бы ширину карточки, и потолок не действовал бы вовсе.
                modifier = Modifier
                    .widthIn(max = QrSideMax)
                    .fillMaxWidth(),
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                TruncatedText(
                    text = title,
                    style = DesignType.CardTitle,
                    color = Color.Black,
                    maxLines = 2,
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                TruncatedText(
                    text = subtitle,
                    style = DesignType.Mono,
                    color = Color.DarkGray,
                    maxLines = 1,
                )
            }
            // Уходящее примечание дорисовывается своим текстом, пока блок сворачивается, —
            // иначе он схлопнулся бы пустым, и анимации не было бы видно.
            // Простой держатель, не `State`: запись в состояние посреди композиции вызывала бы
            // её же повтор, а читать последнее значение нужно только здесь.
            val lastNote = remember { arrayOf(note) }
            if (note.isNotEmpty()) lastNote[0] = note
            val shownNote = lastNote[0]
            AnimatedVisibility(
                visible = note.isNotEmpty(),
                enter = expandVertically(tween(ModeSwitchMillis), expandFrom = Alignment.Top) +
                    fadeIn(tween(ModeSwitchMillis)),
                exit = shrinkVertically(tween(ModeSwitchMillis), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(ModeSwitchMillis)),
            ) {
                // Отступ над чертой — внутри анимации: снаружи он держал бы место под
                // названием и в режиме «Кратко».
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.8.dp, color = Color.LightGray)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "ПРИМЕЧАНИЕ",
                        style = DesignType.Micro,
                        color = Color.DarkGray,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = shownNote,
                        style = DesignType.Body,
                        color = Color.Black,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Сетка QR-кода на `Canvas`: чёрные модули по белому, тихая зона в два модуля, посреди —
 * логотип приложения на белой подложке по [logoLayout], той же, что и на печати.
 *
 * Каждый модуль рисуется на полпикселя шире, чем ячейка сетки: при дробном размере
 * ячейки между соседними квадратами иначе просвечивают волосяные щели, и сплошные
 * угловые метки читаются камерой как пунктир.
 */
@Composable
fun QrImage(modules: QrModules, logo: ImageBitmap?, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
    ) {
        val quiet = 2
        val cell = size.minDimension / (modules.size + quiet * 2)
        val origin = cell * quiet
        val side = cell + 0.5f
        for (y in 0 until modules.size) {
            for (x in 0 until modules.size) {
                if (!modules[x, y]) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(origin + x * cell, origin + y * cell),
                    size = Size(side, side),
                )
            }
        }
        if (logo != null) {
            val layout = logoLayout()
            val codePx = cell * modules.size
            val padSide = codePx * layout.padSide
            val padStart = origin + codePx * layout.padStart
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(padStart, padStart),
                size = Size(padSide, padSide),
                cornerRadius = CornerRadius(padSide * 0.18f),
            )
            val logoSide = (codePx * layout.logoSide).toInt()
            val logoStart = (origin + codePx * layout.logoStart).toInt()
            drawImage(
                image = logo,
                srcSize = IntSize(logo.width, logo.height),
                dstOffset = IntOffset(logoStart, logoStart),
                dstSize = IntSize(logoSide, logoSide),
            )
        }
    }
}

/**
 * Отдаёт картинку системному окну «Поделиться» — так же, как файл расписания:
 * `createChooser`, `FLAG_GRANT_READ_URI_PERMISSION`, потому что адрес от FileProvider
 * сам по себе прав на чтение не даёт.
 */
private fun shareQrImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, "Отправить QR-код"))
    } catch (e: Exception) {
        // Отправлять нечем — ни одно приложение не принимает картинки. Сохранение на
        // устройство остаётся первой кнопкой той же шторки.
    }
}

