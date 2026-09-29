package ru.zaroslikov.incubator.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.zaroslikov.incubator.domain.model.HatchOutcome
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.LocalUnits
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import ru.zaroslikov.incubator.design.components.FieldLabel
import ru.zaroslikov.incubator.design.components.FormSpacer
import ru.zaroslikov.incubator.design.components.PriceRow
import ru.zaroslikov.incubator.design.components.PriceSummaryTile
import ru.zaroslikov.incubator.design.components.SheetTextField
import ru.zaroslikov.incubator.design.components.TileRow
import ru.zaroslikov.incubator.design.components.tileWeight
import ru.zaroslikov.incubator.design.components.clearFocusOnTap
import ru.zaroslikov.incubator.ui.incubator.formatMoney
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType

/**
 * Два диалога завершения инкубации — их открывает кнопка внизу «Обзора» в
 * [BatchDetailSheet]. Какой именно откроется, решает [BatchDetailUiState.readyToFinish]:
 * вышел срок или нет.
 *
 * Макета у них нет: собраны из тех же кирпичиков, что и формы в шторках
 * ([FieldLabel], [SheetTextField], [PriceRow]), чтобы не выглядеть чужими.
 *
 * Почему вопросы разные. Досрочное завершение означает, что птенцов не будет вовсе:
 * спрашивать «сколько вывелось» бессмысленно, зато нужна причина — через полгода по
 * одной дате не вспомнить, почему партия кончилась ничем, — и стоит сказать прямо, что
 * все яйца уходят в расход. Завершение в срок наоборот: причины нет, а есть итог, ради
 * которого закладку и заводили, то есть птенцы и их цена.
 *
 * Отдельный файл, а не низ [BatchDetailSheet]: тот и без них перевалил за две с
 * половиной тысячи строк.
 */

/**
 * Выбирает, каким из двух диалогов завершать закладку, и шлёт событие в аналитику.
 *
 * Общая точка входа для обоих мест, откуда закладку завершают: кнопки внизу «Обзора» в
 * [BatchDetailSheet] и пункта «Убрать в архив» в меню карточки на экране инкубатора.
 * Раньше выбор ветки лежал в шторке — оттуда меню карточки его бы не достало, а второй
 * такой же `if` неминуемо разошёлся бы с первым.
 *
 * Ничего не рисует, пока закладка не прочитана или уже завершена: завершать нечего.
 *
 * @param rejected сколько яиц убрано за все овоскопирования — из него и заложенного
 *        получается остаток, который диалоги показывают и списывают.
 */
@Composable
internal fun FinishBatchDialogs(
    state: BatchDetailUiState,
    rejected: Int,
    onDismiss: () -> Unit,
    onFinish: (outcome: HatchOutcome) -> Unit,
    onFinishEarly: (reason: String) -> Unit,
) {
    if (!state.loaded || state.finished) return
    val remaining = (state.eggAll - rejected).coerceAtLeast(0)

    if (state.readyToFinish) {
        FinishOnTimeDialog(
            state = state,
            remaining = remaining,
            onDismiss = onDismiss,
            onConfirm = { outcome ->
                // Имя события с прежнего экрана закладки — аналитика остаётся сравнимой.
                Analytics.report(Events.FINISH_ON_TIME, finishParams(state, outcome.hatched))
                onFinish(outcome)
                // Просьба оценить приложение отсюда ушла: после записи над экраном
                // встаёт поздравление с салютом (`HatchCelebrationDialog`), и окно
                // RuStore поверх него было бы окном поверх праздника. Она приходит по
                // «Отлично» в поздравлении — см. `IncubatorScreen`.
            },
        )
    } else {
        FinishEarlyDialog(
            state = state,
            remaining = remaining,
            onDismiss = onDismiss,
            onConfirm = { reason ->
                Analytics.report(Events.FINISH_EARLY, finishParams(state, 0, reason))
                onFinishEarly(reason)
            },
        )
    }
}

/**
 * Те же диалоги для того, у кого нет под рукой прочитанной закладки, — меню карточки на
 * экране инкубатора.
 *
 * Собственный [BatchDetailViewModel] под отдельным ключом: шторка закладки на том же
 * экране держит свой, и без ключа они оказались бы одним и тем же объектом — открытое
 * меню карточки перезагружало бы шторку под собой. Ключ постоянный, а не по
 * идентификатору: [BatchDetailIntent.Load] и так сбрасывает всё состояние, а ключ на
 * каждую закладку копил бы в хранилище по объекту на каждую открытую карточку.
 *
 * [onFinished] вызывается уже после записи в базу; список закладок под меню на потоке и
 * перечитается сам. Сводка в нём — для поздравления, и она есть только у закладки,
 * доведённой до срока с птенцами (см. `BatchDetailEffect.Finished`).
 *
 * @param hide убрать закладку в архив тем же сохранением. Так завершает «Убрать в архив»
 *        из меню карточки: пункт обещает архив, и оставлять после него завершённую
 *        карточку в списке — значит требовать второго такого же нажатия. Подсказка
 *        «Инкубация завершена» этот флаг не ставит: она про итог, а не про список.
 */
@Composable
internal fun FinishBatchHost(
    batchId: Long,
    onDismiss: () -> Unit,
    onFinished: (hatched: HatchSummary?) -> Unit,
    hide: Boolean = false,
    viewModel: BatchDetailViewModel = viewModel(
        key = "batch-finish",
        factory = AppViewModelProvider.Factory,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(batchId) { viewModel.onIntent(BatchDetailIntent.Load(batchId)) }

    // [onFinished] приходит эффектом, а не лямбдой в `finish`: сообщить «записано» надо
    // один раз и после самой записи — отчёт об инкубаторе успевает уйти до него, см.
    // `BatchDetailViewModel.archive`.
    CollectEffects(viewModel) { effect ->
        when (effect) {
            is BatchDetailEffect.Finished -> onFinished(effect.hatched)
        }
    }

    FinishBatchDialogs(
        state = state.summary,
        rejected = state.rejectedTotal,
        onDismiss = onDismiss,
        onFinish = { outcome -> viewModel.onIntent(BatchDetailIntent.Finish(outcome, hide)) },
        onFinishEarly = { reason ->
            viewModel.onIntent(BatchDetailIntent.FinishEarly(reason, hide))
        },
    )
}

/**
 * Завершение партии — закладок, заложенных одним нажатием на разные породы, — одним
 * диалогом. Открывает его подсказка «Инкубация завершена», когда срок вышел сразу у
 * нескольких таких закладок: человек закладывал один лоток и итог вносит по нему.
 *
 * Собственная ViewModel под своим ключом — по той же причине, что у [FinishBatchHost].
 *
 * @param batchIds закладки партии; те, что к моменту чтения уже не идут, просто не
 *        попадают в диалог.
 * @param onFinished записано; в нём — итог каждой завершённой породы, для поздравления
 *        (пустой список — ничего не завершили).
 */
@Composable
internal fun FinishGroupHost(
    incubatorId: Long,
    batchIds: List<Long>,
    onDismiss: () -> Unit,
    onFinished: (summaries: List<HatchSummary>) -> Unit,
    viewModel: FinishGroupViewModel = viewModel(
        key = "batch-finish-group",
        factory = AppViewModelProvider.Factory,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(incubatorId, batchIds) {
        viewModel.onIntent(FinishGroupIntent.Load(incubatorId, batchIds))
    }

    CollectEffects(viewModel) { effect ->
        when (effect) {
            // Просьба оценить — не здесь, а по «Отлично» в поздравлении, как и у одной
            // закладки; условие то же: птенцы вывелись хоть по одной породе.
            is FinishGroupEffect.Finished -> onFinished(effect.summaries)
        }
    }

    // Состояние прежнего открытия доживает до первого `Load`: чужие строки не рисуем.
    if (!state.loaded || state.items.any { it.batch.id !in batchIds }) return
    if (state.items.isEmpty()) {
        // Все породы успели завершить из другого места — спрашивать не о чем.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    FinishGroupDialog(
        items = state.items,
        onDismiss = onDismiss,
        onConfirm = { outcomes -> viewModel.onIntent(FinishGroupIntent.Finish(outcomes)) },
    )
}

/**
 * Итог партии: по полю вывода и цене на каждую породу.
 *
 * **Пустое поле — не ноль, а «ещё не знаю».** Вывод у пород идёт не минута в минуту, и
 * заставлять вписывать обе значило бы либо ждать последнего птенца второй, либо
 * выдумать ей число. Поэтому завершаются только заполненные породы, а пустые остаются в
 * инкубации — подсказка вернётся к ним при следующем заходе, уже одной закладкой. Кнопка
 * гаснет, только пока не заполнено ни одного поля, и сама говорит, сколько пород уйдёт.
 *
 * Поле и цена у каждой породы — те же, что в [FinishOnTimeDialog]: итог одной породы не
 * должен зависеть от того, из какого диалога его внесли.
 */
@Composable
internal fun FinishGroupDialog(
    items: List<FinishGroupItem>,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, HatchOutcome>) -> Unit,
) {
    val currency = LocalUnits.current.currency
    // По идентификатору, а не по месту в списке: порода, завершённая из другого места,
    // уходит из диалога и не должна сдвигать набранное остальным.
    var rows by rememberSaveable(stateSaver = FinishRowsSaver) {
        mutableStateOf(emptyMap<Long, FinishRow>())
    }
    val scrollState = rememberScrollState()

    val filled = items.filter { rows[it.batch.id]?.hatched?.isNotBlank() == true }
    val partial = filled.isNotEmpty() && filled.size < items.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Завершить инкубацию", style = DesignType.SectionTitle) },
        text = {
            Column(Modifier.clearFocusOnTap().verticalScroll(scrollState)) {
                Text(
                    text = "Сколько птенцов вывелось по каждой породе?",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FormSpacer(4.dp)
                Text(
                    text = "Породу, по которой вывод ещё идёт, оставьте пустой — она " +
                        "останется в инкубации.",
                    style = DesignType.Note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                items.forEachIndexed { index, item ->
                    val batch = item.batch
                    val row = rows[batch.id] ?: FinishRow()
                    FormSpacer(16.dp)
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.8.dp, color = DesignPalette.CardBorder)
                        FormSpacer(16.dp)
                    }
                    Text(
                        text = batch.breed.ifBlank { batch.title.ifBlank { batch.type } },
                        style = DesignType.CardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FormSpacer(8.dp)
                    FieldLabel(text = "Выведено птенцов")
                    SheetTextField(
                        value = row.hatched,
                        onValueChange = {
                            rows = rows + (batch.id to row.copy(hatched = clampCount(it, batch.eggAll)))
                        },
                        placeholder = "—",
                        numeric = true,
                    )
                    FormSpacer(6.dp)
                    Text(
                        text = hatchedHint(batch.eggAll, item.remaining),
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FormSpacer(12.dp)
                    ChickPriceField(
                        row = row,
                        onChange = { rows = rows + (batch.id to it) },
                        label = "Стоимость птенцов, ${currency.symbol}",
                    )
                    // Плитки — только когда есть что считать: у трёх пород подряд пустые
                    // «0 ₽» растянули бы диалог на экран ради ничего.
                    if (row.priceValue > 0) {
                        FormSpacer(8.dp)
                        ChickPriceSummary(
                            price = row.priceValue,
                            perHead = row.perHead,
                            hatched = row.outcome.hatched,
                        )
                    }
                }

                if (partial) {
                    FormSpacer(16.dp)
                    Text(
                        text = "Без итога: " +
                            items.filter { it !in filled }.joinToString {
                                it.batch.breed.ifBlank { it.batch.title }
                            } +
                            ". Останется в инкубации — итог внесёте позже.",
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(filled.associate { it.batch.id to rows.getValue(it.batch.id).outcome })
                },
                enabled = filled.isNotEmpty(),
            ) {
                Text(
                    text = if (partial) "Завершить ${filled.size} из ${items.size}" else "Завершить",
                    color = if (filled.isNotEmpty()) DesignPalette.Accent
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
 * Параметры события завершения — те же поля, что слал прежний экран закладки, чтобы
 * старые и новые события ложились в один отчёт. [reason] добавляется только у
 * досрочного: у завершения в срок причины нет.
 */
private fun finishParams(
    state: BatchDetailUiState,
    hatched: Int,
    reason: String = "",
): Map<String, Any> = buildMap {
    put("Имя", state.title)
    put("Тип", state.type)
    put("Кол-во", state.eggAll)
    put("Кол-во пос", hatched)
    put("День", state.day)
    if (reason.isNotBlank()) put("Причина", reason)
}

/** Готовые причины: то, из-за чего закладку бросают чаще всего. Тап подставляет текст. */
private val EarlyReasons = listOf(
    "Нет оплода",
    "Замерли эмбрионы",
    "Сбой инкубатора",
    "Отключали свет",
)

/**
 * Досрочное завершение: предупреждение, обязательная причина и цена вопроса.
 *
 * Кнопка подтверждения погашена, пока причина пуста. Это единственное, что здесь
 * спрашивают, и завершить «просто так» значило бы потерять её навсегда: дописать потом
 * уже некуда, закладка уходит в архив.
 *
 * Сумма расхода берётся из стоимости яиц ([BatchDetailUiState.eggsCost]) — той самой,
 * что ввели в форме закладки. Не указывали — так и говорим, а не рисуем «0 ₽»:
 * бесплатных яиц не бывает, бывает неизвестная цена.
 *
 * @param remaining сколько яиц дожило до этого дня — заложенные минус выбракованные
 *        на овоскопированиях. Списываются именно они.
 */
@Composable
internal fun FinishEarlyDialog(
    state: BatchDetailUiState,
    remaining: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Завершить досрочно?", style = DesignType.SectionTitle) },
        text = {
            Column(Modifier.clearFocusOnTap().verticalScroll(scrollState)) {
                Text(
                    text = buildAnnotatedString {
                        append("До конца инкубации ")
                        withStyle(SpanStyle(color = DesignPalette.Expense)) {
                            append(daysLeftText(state.daysLeft))
                        }
                        append(". Птенцы из этой закладки уже не выведутся: она уйдёт ")
                        append("в архив с нулевым выводом, а все ")
                        append("$remaining ${eggsWord(remaining)}")
                        append(" спишутся в расход.")
                    },
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(12.dp)
                ExpenseTile(eggsCost = state.eggsCost, eggs = remaining)

                FormSpacer(16.dp)
                FieldLabel(text = "Причина завершения", required = true)
                SheetTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = "Что случилось с закладкой",
                    singleLine = false,
                    minHeight = 72.dp,
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                )

                FormSpacer(8.dp)
                ReasonChips(onPick = { reason = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank(),
            ) {
                Text(
                    text = "Завершить досрочно",
                    color = if (reason.isNotBlank()) DesignPalette.Expense
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
 * Завершение в срок: сколько птенцов вывелось и почём они.
 *
 * Птенцов не может быть больше, чем заложено яиц, поэтому поле зажимает введённое
 * число, а не отказывается его принимать: молча проглоченная цифра читается как
 * поломка клавиатуры. Ноль — тоже результат, и кнопка от него не гаснет; гаснет она
 * только на пустом поле, потому что «не указали» и «вывелось ноль» — разные вещи.
 *
 * Одно поле вывода и одна цена: в закладке одна порода. Лоток с двумя породами — это
 * две закладки, так что «какая порода выводится лучше и какая дороже уходит» считается
 * по закладкам без второго учёта внутри. Подсказка «Инкубация завершена» спрашивает их
 * вместе — [FinishGroupDialog], — но и там у каждой породы своё поле и своя цена.
 *
 * Цена спрашивается ровно так же, как стоимость яиц в форме закладки: сумма плюс
 * переключатель «за птенца / за всех», и хранится дословно — пересчёт одного в другое
 * при сохранении потерял бы, что именно ввёл человек. Цену можно не заполнять вовсе —
 * её отсутствие «Финансы» умеют называть вслух.
 */
@Composable
internal fun FinishOnTimeDialog(
    state: BatchDetailUiState,
    remaining: Int,
    onDismiss: () -> Unit,
    onConfirm: (outcome: HatchOutcome) -> Unit,
) {
    val currency = LocalUnits.current.currency
    // Одна строка — вывод и цена вместе: так набранное переживает поворот одним
    // объектом, а в итог их превращает `outcome`.
    var row by rememberSaveable(stateSaver = FinishRowSaver) { mutableStateOf(FinishRow()) }
    val scrollState = rememberScrollState()

    val outcome = row.outcome
    val filled = row.hatched.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Завершить инкубацию", style = DesignType.SectionTitle) },
        text = {
            Column(Modifier.clearFocusOnTap().verticalScroll(scrollState)) {
                Text(
                    text = "Закладка уйдёт в архив. Сколько птенцов вывелось?",
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(16.dp)
                FieldLabel(text = "Выведено птенцов", required = true)
                SheetTextField(
                    value = row.hatched,
                    onValueChange = { row = row.copy(hatched = clampCount(it, state.eggAll)) },
                    placeholder = "0",
                    numeric = true,
                )
                FormSpacer(6.dp)
                Text(
                    text = hatchedHint(state.eggAll, remaining),
                    style = DesignType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FormSpacer(16.dp)
                ChickPriceField(
                    row = row,
                    onChange = { row = it },
                    label = "Стоимость птенцов, ${currency.symbol}",
                )
                FormSpacer(8.dp)
                ChickPriceSummary(
                    price = row.priceValue,
                    perHead = row.perHead,
                    hatched = outcome.hatched,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(outcome) },
                enabled = filled,
            ) {
                Text(
                    text = "Завершить",
                    color = if (filled) DesignPalette.Accent
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
 * Итог в диалоге: вывод и цена его птенцов, как их набирают. [outcome] превращает
 * набранное в то, что понимает `:domain`.
 */
private data class FinishRow(
    val hatched: String = "",
    val price: String = "",
    val perHead: Boolean = true,
) {
    val priceValue: Int get() = price.toIntOrNull() ?: 0

    val outcome: HatchOutcome
        get() = HatchOutcome(
            hatched = hatched.toIntOrNull() ?: 0,
            chickPrice = priceValue,
            chickPricePerHead = perHead,
        )
}

/**
 * Строка переживает поворот экрана, как переживали его три отдельных поля до неё.
 * `Bundle` знает только простые значения, поэтому она уезжает тремя подряд: без своего
 * `Parcelable` ради трёх полей.
 */
private val FinishRowSaver = listSaver<FinishRow, String>(
    save = { row -> listOf(row.hatched, row.price, row.perHead.toString()) },
    restore = { flat -> FinishRow(flat[0], flat[1], flat[2].toBoolean()) },
)

/**
 * Строки партии — по четыре строки на закладку: идентификатор и три поля [FinishRow].
 * Карта, а не список, по той же причине, по какой её держит [FinishGroupDialog].
 */
private val FinishRowsSaver = listSaver<Map<Long, FinishRow>, String>(
    save = { rows ->
        rows.flatMap { (id, row) -> listOf(id.toString(), row.hatched, row.price, row.perHead.toString()) }
    },
    restore = { flat ->
        flat.chunked(4).associate { (id, hatched, price, perHead) ->
            id.toLong() to FinishRow(hatched, price, perHead.toBoolean())
        }
    },
)

/** Цена птенцов — то же поле с переключателем, что и стоимость яиц в форме закладки. */
@Composable
private fun ChickPriceField(row: FinishRow, onChange: (FinishRow) -> Unit, label: String) {
    FieldLabel(text = label)
    PriceRow(
        price = row.price,
        perUnit = row.perHead,
        perUnitLabel = "за птенца",
        totalLabel = "за всех",
        onPriceChange = { onChange(row.copy(price = it.filter(Char::isDigit))) },
        onModeChange = { onChange(row.copy(perHead = it)) },
        // Ширина диалога вдвое меньше формы: половина под переключатель обрезала бы
        // «за птенца».
        evenSplit = false,
    )
}

// --- Части диалогов ---------------------------------------------------------------------------

/**
 * Оставляет в поле только цифры и не даёт числу перевалить за [max].
 *
 * Именно зажимает значение, а не отбрасывает лишний символ: то же решение, что и в
 * [CandlingIntent.UpdateRejected] — набранные «50» при сорока яйцах становятся «40»,
 * и сразу видно, где предел.
 */
private fun clampCount(input: String, max: Int): String {
    val digits = input.filter(Char::isDigit)
    if (digits.isBlank()) return ""
    val value = digits.toIntOrNull() ?: return max.toString()
    return value.coerceAtMost(max).toString()
}

/** Подпись под полем: откуда взялся предел и сколько яиц дожило до вывода. */
private fun hatchedHint(eggAll: Int, remaining: Int): String =
    if (remaining in 1 until eggAll) {
        "Заложено $eggAll, после овоскопирования осталось $remaining"
    } else {
        "Больше $eggAll — заложенного количества — указать нельзя"
    }

private fun daysLeftText(daysLeft: Int?): String = when {
    daysLeft == null -> "срок неизвестен"
    daysLeft == 1 -> "остался 1 день"
    daysLeft in 2..4 -> "осталось $daysLeft дня"
    else -> "осталось $daysLeft дней"
}

/** Во что обошлись списываемые яйца. Цены нет — так и написано, вместо честного нуля. */
@Composable
private fun ExpenseTile(eggsCost: Int, eggs: Int) {
    val currency = LocalUnits.current.currency
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DesignPalette.ExpenseSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append("В расход: ")
                withStyle(
                    SpanStyle(
                        fontFamily = DesignType.MonoEmphasis.fontFamily,
                        fontWeight = DesignType.MonoEmphasis.fontWeight,
                        color = DesignPalette.Expense,
                    )
                ) {
                    append(
                        if (eggsCost > 0) formatMoney(eggsCost, currency)
                        else "$eggs ${eggsWord(eggs)}"
                    )
                }
                if (eggsCost <= 0) append(" — стоимость закладки не указана")
            },
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Подсказки-причины: тап заполняет поле, дописать своё всё равно можно. */
@Composable
private fun ReasonChips(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EarlyReasons.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { text -> ReasonChip(text) { onPick(text) } }
            }
        }
    }
}

@Composable
private fun ReasonChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DesignPalette.PillSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Две плитки под ценой птенцов — те же, что под стоимостью яиц в форме закладки.
 *
 * Пока птенцов не ввели, вторая плитка не может назвать их количество и остаётся просто
 * «За всех»: подставлять туда заложенные яйца было бы враньём.
 */
@Composable
private fun ChickPriceSummary(price: Int, perHead: Boolean, hatched: Int) {
    val currency = LocalUnits.current.currency
    val perHeadValue = if (perHead) price else if (hatched > 0) price / hatched else 0
    val totalValue = if (perHead) price * hatched else price

    // Те же две плитки одной высоты, что и под ценой яиц, и по той же причине: в
    // диалоге ряд ещё уже, и правая половина переносится первой.
    TileRow {
        PriceSummaryTile(
            label = "За одного: ",
            value = "$perHeadValue ${currency.symbol}",
            modifier = tileWeight(),
        )
        PriceSummaryTile(
            label = if (hatched > 0) "За всех $hatched: " else "За всех: ",
            value = "$totalValue ${currency.symbol}",
            modifier = tileWeight(),
        )
    }
}
