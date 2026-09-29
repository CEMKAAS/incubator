package ru.zaroslikov.incubator.ui.batch

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import ru.zaroslikov.incubator.design.components.LoadingSpinner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.calendar.CalendarDate
import ru.zaroslikov.incubator.calendar.CalendarDateKind
import ru.zaroslikov.incubator.calendar.CalendarOffer
import ru.zaroslikov.incubator.calendar.CalendarWriter
import ru.zaroslikov.incubator.calendar.DeviceCalendar
import ru.zaroslikov.incubator.calendar.label
import ru.zaroslikov.incubator.calendar.whenLine
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.incubator.plural
import java.time.LocalDate

/** Шаг диалога. Имя шага сохраняется в `Bundle` строкой. */
private enum class CalendarStep { Ask, Pick, Writing, Manual, Done }

/** Почему даты добавляются по одной — от этого зависит первая фраза шага [CalendarStep.Manual]. */
private enum class ManualReason { Denied, NoCalendar, Failed }

/** Час, к которому провайдер присылает напоминание накануне, — см. `CalendarWriter`. */
private const val REMINDER_HOUR = 18

/**
 * «Добавить даты в календарь?» — вопрос сразу после того, как закладку создали.
 *
 * Спрашивает каждый раз, и только при создании: даты закладки становятся известны в
 * эту минуту, и в эту же минуту человек думает о ней. Показывает его экран инкубатора,
 * поверх открывшейся закладки, — предложение едет в эффекте сохранения формы
 * (`AddBatchEffect.Saved.calendar`).
 *
 * Шаги:
 * 1. **Вопрос** — даты с галочками, все отмечены: вывод нужен всем, а овоскопирование
 *    кто-то не делает вовсе, и снять галочку дешевле, чем удалять событие из календаря.
 * 2. **Выбор календаря** — только когда пишущих календарей на телефоне несколько
 *    (личный, рабочий, семейный); с одним спрашивать не о чем.
 * 3. **Запись** — сохраняемый шаг, и ради одного: `applyBatch` блокирующий, отмена
 *    корутины его не прерывает, и поворот посреди записи оставлял события в календаре,
 *    а диалог — на «Добавить». Второе нажатие записало бы всё дважды. Восстановленный
 *    «Запись» без живой корутины значит «записано» и ведёт на «Готово».
 * 4. **По одной** — запасной путь, если в доступе отказали, календаря нет или запись
 *    не удалась: каждая дата открывает календарь на заполненном событии. Кнопка,
 *    которую нажали, должна что-то сделать.
 * 5. **Готово** — сколько дат записано и когда календарь о них напомнит.
 *
 * Разрешение спрашивается по нажатию «Добавить», а не при открытии: вопрос системы о
 * доступе к календарю без объяснения, зачем он, — это вопрос, на который отвечают «нет».
 */
@Composable
internal fun AddToCalendarDialog(
    offer: CalendarOffer,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stepName by rememberSaveable { mutableStateOf(CalendarStep.Ask.name) }
    val step = CalendarStep.valueOf(stepName)
    var reasonName by rememberSaveable { mutableStateOf(ManualReason.Denied.name) }
    // Индексы снятых галочек — не отмеченных: по умолчанию отмечено всё.
    var unchecked by rememberSaveable { mutableStateOf(IntArray(0)) }
    // Какие даты на шаге «по одной» уже открывали в календаре.
    var opened by rememberSaveable { mutableStateOf(IntArray(0)) }
    var chosenCalendar by rememberSaveable { mutableLongStateOf(-1L) }
    var added by rememberSaveable { mutableIntStateOf(0) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>?>(null) }
    // Идёт чтение календарей или запрос разрешения: кнопки глухи, второй тап не
    // запускает второго запроса. Не сохраняется — после поворота ничего не идёт.
    var busy by remember { mutableStateOf(false) }
    // Корутина записи жива в этой композиции. Шаг «Запись» без неё — наследство поворота.
    var writing by remember { mutableStateOf(false) }
    var noCalendarApp by remember { mutableStateOf(false) }

    val selected = offer.dates.filterIndexed { index, _ -> index !in unchecked }

    fun goManual(reason: ManualReason) {
        reasonName = reason.name
        stepName = CalendarStep.Manual.name
    }

    fun write(calendarId: Long) {
        if (writing) return
        writing = true
        stepName = CalendarStep.Writing.name
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    CalendarWriter.insert(context, calendarId, offer, selected)
                }
                added = count
                Analytics.report(
                    Events.CALENDAR_ADDED,
                    mapOf("Дат" to count, "Способ" to "напрямую", "Вид" to offer.species),
                )
                stepName = CalendarStep.Done.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                goManual(ManualReason.Denied)
            } catch (e: Exception) {
                goManual(ManualReason.Failed)
            } finally {
                writing = false
            }
        }
    }

    /** Календари для записи; `null` — доступа нет (его могли отозвать, пока диалог висел). */
    suspend fun loadCalendars(): List<DeviceCalendar>? = try {
        withContext(Dispatchers.IO) { CalendarWriter.calendars(context) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        emptyList()
    }

    /** Один календарь — пишем сразу, несколько — спрашиваем, куда. */
    fun route(list: List<DeviceCalendar>?) {
        when {
            list == null -> goManual(ManualReason.Denied)
            list.isEmpty() -> goManual(ManualReason.NoCalendar)
            list.size == 1 -> write(list.single().id)
            else -> {
                calendars = list
                if (list.none { it.id == chosenCalendar }) chosenCalendar = list.first().id
                stepName = CalendarStep.Pick.name
            }
        }
    }

    fun proceed() {
        busy = true
        scope.launch {
            val list = loadCalendars()
            busy = false
            route(list)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        busy = false
        when {
            // Пустой ответ — запрос прервали (или он был вторым подряд): это не отказ,
            // вопрос остаётся, и «Добавить» можно нажать снова.
            result.isEmpty() -> Unit
            result.values.all { it } -> proceed()
            else -> goManual(ManualReason.Denied)
        }
    }

    LaunchedEffect(step) {
        when (step) {
            // Поворот посреди записи: транзакция провайдера завершилась без нас.
            CalendarStep.Writing -> if (!writing) {
                added = selected.size
                stepName = CalendarStep.Done.name
            }
            // Поворот посреди выбора: список не лежит в `Bundle`, его проще перечитать.
            CalendarStep.Pick -> if (calendars == null) route(loadCalendars())
            else -> Unit
        }
    }

    val decline = {
        Analytics.report(Events.CALENDAR_DECLINED, mapOf("Дат" to offer.dates.size))
        onDone()
    }
    val finishManual = {
        // «Открыто», а не «Дат»: сохранил ли человек событие в чужом приложении, отсюда
        // не видно, и смешивать это число с записанными напрямую нельзя.
        if (opened.isNotEmpty()) {
            Analytics.report(
                Events.CALENDAR_ADDED,
                mapOf("Открыто" to opened.size, "Способ" to "по одной", "Вид" to offer.species),
            )
        } else {
            Analytics.report(
                Events.CALENDAR_DECLINED,
                mapOf("Дат" to offer.dates.size, "Этап" to "по одной"),
            )
        }
        onDone()
    }

    AlertDialog(
        onDismissRequest = {
            when (step) {
                CalendarStep.Ask, CalendarStep.Pick -> if (!busy) decline()
                CalendarStep.Writing -> Unit
                CalendarStep.Manual -> finishManual()
                CalendarStep.Done -> onDone()
            }
        },
        title = {
            Text(
                text = when (step) {
                    CalendarStep.Ask -> "Добавить даты в календарь?"
                    CalendarStep.Pick -> "В какой календарь?"
                    CalendarStep.Writing -> "Записываем…"
                    CalendarStep.Manual -> "Добавить по одной"
                    CalendarStep.Done -> "Даты в календаре"
                },
                style = DesignType.SectionTitle,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    CalendarStep.Ask -> AskContent(
                        offer = offer,
                        unchecked = unchecked,
                        onToggle = { index ->
                            unchecked = if (index in unchecked) {
                                unchecked.filter { it != index }.toIntArray()
                            } else {
                                unchecked + index
                            }
                        },
                    )

                    CalendarStep.Pick -> PickContent(
                        calendars = calendars.orEmpty(),
                        chosen = chosenCalendar,
                        onChoose = { chosenCalendar = it },
                    )

                    CalendarStep.Writing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingSpinner()
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Даты закладки уходят в календарь.",
                            style = DesignType.Body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    CalendarStep.Manual -> ManualContent(
                        reason = ManualReason.valueOf(reasonName),
                        dates = selected,
                        opened = opened,
                        noCalendarApp = noCalendarApp,
                        onOpen = { date ->
                            try {
                                context.startActivity(CalendarWriter.insertIntent(offer, date))
                                val index = offer.dates.indexOf(date)
                                if (index !in opened) opened = opened + index
                            } catch (e: ActivityNotFoundException) {
                                noCalendarApp = true
                            }
                        },
                        openedIndex = { date -> offer.dates.indexOf(date) },
                    )

                    CalendarStep.Done -> Text(
                        text = doneText(offer, selected, added),
                        style = DesignType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                CalendarStep.Ask -> TextButton(
                    enabled = selected.isNotEmpty() && !busy,
                    onClick = {
                        if (busy) return@TextButton
                        if (CalendarWriter.hasPermission(context)) {
                            proceed()
                        } else {
                            busy = true
                            permissionLauncher.launch(CalendarWriter.PERMISSIONS)
                        }
                    },
                ) { Text(text = "Добавить", color = accentOrDim(selected.isNotEmpty() && !busy)) }

                CalendarStep.Pick -> TextButton(
                    enabled = chosenCalendar >= 0 && !busy,
                    onClick = { if (!busy) write(chosenCalendar) },
                ) { Text(text = "Записать", color = accentOrDim(chosenCalendar >= 0 && !busy)) }

                CalendarStep.Writing -> Unit

                CalendarStep.Manual -> TextButton(onClick = finishManual) {
                    Text(text = "Готово", color = DesignPalette.Accent)
                }

                CalendarStep.Done -> TextButton(onClick = onDone) {
                    Text(text = "Понятно", color = DesignPalette.Accent)
                }
            }
        },
        dismissButton = {
            if (step == CalendarStep.Ask || step == CalendarStep.Pick) {
                TextButton(enabled = !busy, onClick = decline) {
                    Text(text = "Не нужно", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    )
}

/**
 * Итог записи. Обещание «напомнит накануне в 18:00» не для всех дат правда: у
 * сегодняшней, а после шести вечера и у завтрашней, этот вечер уже прошёл, и провайдер
 * прошедшее напоминание не покажет. Такие даты называются отдельно.
 */
private fun doneText(offer: CalendarOffer, dates: List<CalendarDate>, added: Int): String {
    val now = java.time.LocalDateTime.now()
    val missed = dates.count { it.date.minusDays(1).atTime(REMINDER_HOUR, 0) <= now }
    val base = "Записали в календарь ${plural(added, "дату", "даты", "дат")} закладки " +
        "«${offer.title}»."
    return when {
        missed == 0 -> "$base Накануне каждой календарь напомнит в $REMINDER_HOUR:00."
        missed >= dates.size -> "$base Вечер накануне у них уже прошёл, так что календарь " +
            "покажет их, но заранее не напомнит."
        else -> "$base Накануне каждой календарь напомнит в $REMINDER_HOUR:00. Без " +
            "напоминания — ${plural(missed, "ближайшая дата", "ближайшие даты", "ближайших дат")}: " +
            "вечер накануне уже прошёл."
    }
}

@Composable
private fun accentOrDim(enabled: Boolean) =
    if (enabled) DesignPalette.Accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

@Composable
private fun AskContent(
    offer: CalendarOffer,
    unchecked: IntArray,
    onToggle: (Int) -> Unit,
) {
    Text(
        text = "Календарь телефона напомнит о них накануне в 18:00 — даже если " +
            "приложение не открывать.",
        style = DesignType.Body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    offer.dates.forEachIndexed { index, date ->
        val checked = index !in unchecked
        // Строка — переключатель целиком, как у получателей замера: попасть в строку
        // проще, чем в квадратик 20 dp, и читалка экрана называет дату и её состояние
        // одной фразой.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(index) })
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateLines(date, Modifier.weight(1f))
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = DesignPalette.Accent),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "События попадут в календарь с названием закладки «${offer.title}». " +
            "Если сроки сдвинутся, поправить их придётся в самом календаре.",
        style = DesignType.Note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DateLines(date: CalendarDate, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "${kindEmoji(date.kind)}  ${date.label()}",
            style = DesignType.Body,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = date.whenLine(),
            style = DesignType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun kindEmoji(kind: CalendarDateKind): String = when (kind) {
    CalendarDateKind.Candling -> "🔦"
    CalendarDateKind.StopTurning -> "✋"
    CalendarDateKind.Hatch -> "🐣"
}

@Composable
private fun PickContent(
    calendars: List<DeviceCalendar>,
    chosen: Long,
    onChoose: (Long) -> Unit,
) {
    Text(
        text = "На телефоне несколько календарей. Даты попадут в тот, что выбран.",
        style = DesignType.Body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    calendars.forEach { calendar ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .selectable(
                    selected = calendar.id == chosen,
                    role = Role.RadioButton,
                    onClick = { onChoose(calendar.id) },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = calendar.id == chosen,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = DesignPalette.Accent),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = calendar.name.ifBlank { "Без названия" },
                    style = DesignType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Имя аккаунта — только когда оно что-то добавляет: у локального
                // календаря оно часто совпадает с названием.
                if (calendar.account.isNotBlank() && calendar.account != calendar.name) {
                    Text(
                        text = calendar.account,
                        style = DesignType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualContent(
    reason: ManualReason,
    dates: List<CalendarDate>,
    opened: IntArray,
    noCalendarApp: Boolean,
    onOpen: (CalendarDate) -> Unit,
    openedIndex: (CalendarDate) -> Int,
) {
    val why = when (reason) {
        ManualReason.Denied -> "Без доступа к календарю приложение не может записать даты само."
        ManualReason.NoCalendar -> "На телефоне нет календаря, в который можно записывать."
        ManualReason.Failed -> "Записать даты не получилось."
    }
    Text(
        text = "$why Их можно добавить по одной: календарь откроется с уже заполненным " +
            "событием — останется сохранить.",
        style = DesignType.Body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    dates.forEach { date ->
        val done = openedIndex(date) in opened
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DateLines(date, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onOpen(date) }) {
                Text(
                    text = if (done) "✓ Ещё раз" else "Открыть",
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else DesignPalette.Accent,
                )
            }
        }
    }
    if (noCalendarApp) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "На телефоне нет приложения календаря, которое умеет создавать события.",
            style = DesignType.Note,
            color = DesignPalette.Expense,
        )
    }
}

/**
 * Предложение в `Bundle` — строками, как `HatchSummariesSaver`: поворот экрана не
 * должен снимать вопрос, а своего `Parcelable` ради одного диалога проект не заводит.
 */
internal val CalendarOfferSaver: Saver<CalendarOffer?, Any> = listSaver(
    save = { offer ->
        if (offer == null) {
            emptyList()
        } else {
            listOf(offer.title, offer.species, offer.term.toString()) +
                offer.dates.flatMap {
                    listOf(it.kind.name, it.day.toString(), it.date.toEpochDay().toString(), it.stage.toString())
                }
        }
    },
    restore = { flat ->
        if (flat.size < 3) {
            null
        } else {
            CalendarOffer(
                title = flat[0],
                species = flat[1],
                term = flat[2].toInt(),
                dates = flat.drop(3).chunked(4).map { f ->
                    CalendarDate(
                        kind = CalendarDateKind.valueOf(f[0]),
                        day = f[1].toInt(),
                        date = LocalDate.ofEpochDay(f[2].toLong()),
                        stage = f[3].toInt(),
                    )
                },
            )
        }
    },
)
