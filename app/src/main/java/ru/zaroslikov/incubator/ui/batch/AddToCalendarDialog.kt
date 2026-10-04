package ru.zaroslikov.incubator.ui.batch

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zaroslikov.incubator.calendar.CalendarDate
import ru.zaroslikov.incubator.calendar.CalendarDateKind
import ru.zaroslikov.incubator.calendar.CalendarOffer
import ru.zaroslikov.incubator.calendar.CalendarWriter
import ru.zaroslikov.incubator.calendar.DeviceCalendar
import ru.zaroslikov.incubator.calendar.label
import ru.zaroslikov.incubator.calendar.whenLine
import ru.zaroslikov.incubator.design.components.LoadingSpinner
import ru.zaroslikov.incubator.design.theme.DesignPalette
import ru.zaroslikov.incubator.design.theme.DesignType
import ru.zaroslikov.incubator.ui.AppViewModelProvider
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.mvi.CollectEffects
import java.time.LocalDate

/** Час, к которому провайдер присылает напоминание накануне, — см. `CalendarWriter`. */
private const val REMINDER_HOUR = 18

/**
 * Ключ ViewModel диалога — из содержимого предложения, а не из `hashCode`: у перечислений
 * он меняется от процесса к процессу, а ключ должен пережить восстановление. Своя
 * ViewModel на предложение — чтобы шаг одной закладки не протёк в диалог другой.
 */
private fun CalendarOffer.viewModelKey(): String =
    "calendar-offer:$title:$species:$term:" +
        dates.joinToString(",") { "${it.kind.name}${it.date.toEpochDay()}" }

/**
 * «Добавить даты в календарь?» — вопрос сразу после того, как закладку создали.
 *
 * Показывает экран инкубатора поверх открывшейся закладки; предложение едет в эффекте
 * сохранения формы (`AddBatchEffect.Saved.calendar`). Шаги, запись и аналитика — в
 * [CalendarOfferViewModel]; здесь только платформенное: запрос разрешения и
 * `ACTION_INSERT` на шаге «по одной».
 *
 * Шаги: **вопрос** (даты с галочками, все отмечены) → **выбор календаря** (только когда
 * пишущих несколько) → **запись** → **готово**; при отказе в доступе, без календаря или
 * при сбое записи — **по одной**: каждая дата открывает календарь на заполненном событии.
 *
 * Разрешение спрашивается по нажатию «Добавить», а не при открытии: без объяснения,
 * зачем оно, на системный вопрос отвечают «нет».
 */
@Composable
internal fun AddToCalendarDialog(
    offer: CalendarOffer,
    onDone: () -> Unit,
    viewModel: CalendarOfferViewModel = viewModel(
        key = offer.viewModelKey(),
        factory = AppViewModelProvider.Factory,
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step = state.step
    val busy = state.busy
    val selected = state.selected

    LaunchedEffect(viewModel, offer) { viewModel.onIntent(CalendarOfferIntent.Load(offer)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onIntent(CalendarOfferIntent.PermissionResult(result)) }

    CollectEffects(viewModel) { effect ->
        when (effect) {
            CalendarOfferEffect.RequestPermission -> permissionLauncher.launch(CalendarWriter.PERMISSIONS)
            is CalendarOfferEffect.OpenInsert -> try {
                context.startActivity(effect.intent)
                viewModel.onIntent(CalendarOfferIntent.Opened(effect.index))
            } catch (e: ActivityNotFoundException) {
                viewModel.onIntent(CalendarOfferIntent.NoCalendarApp)
            }
            CalendarOfferEffect.Closed -> onDone()
        }
    }

    // Что значит закрытие — отказ, «готово» по одной или просто «понятно», — решает шаг.
    val dismiss = { viewModel.onIntent(CalendarOfferIntent.Dismiss) }

    AlertDialog(
        onDismissRequest = dismiss,
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
                        unchecked = state.unchecked,
                        onToggle = { viewModel.onIntent(CalendarOfferIntent.Toggle(it)) },
                    )

                    CalendarStep.Pick -> PickContent(
                        calendars = state.calendars.orEmpty(),
                        chosen = state.chosenCalendar,
                        onChoose = { viewModel.onIntent(CalendarOfferIntent.Choose(it)) },
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
                        reason = state.reason,
                        offer = offer,
                        dates = selected,
                        opened = state.opened,
                        noCalendarApp = state.noCalendarApp,
                        onOpen = { index -> viewModel.onIntent(CalendarOfferIntent.Open(index)) },
                    )

                    CalendarStep.Done -> Text(
                        text = doneText(offer, selected, state.added),
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
                    onClick = { viewModel.onIntent(CalendarOfferIntent.Add) },
                ) { Text(text = "Добавить", color = accentOrDim(selected.isNotEmpty() && !busy)) }

                CalendarStep.Pick -> TextButton(
                    enabled = state.chosenCalendar >= 0 && !busy,
                    onClick = { viewModel.onIntent(CalendarOfferIntent.Write) },
                ) { Text(text = "Записать", color = accentOrDim(state.chosenCalendar >= 0 && !busy)) }

                CalendarStep.Writing -> Unit

                CalendarStep.Manual -> TextButton(onClick = dismiss) {
                    Text(text = "Готово", color = DesignPalette.Accent)
                }

                CalendarStep.Done -> TextButton(onClick = dismiss) {
                    Text(text = "Понятно", color = DesignPalette.Accent)
                }
            }
        },
        dismissButton = {
            if (step == CalendarStep.Ask || step == CalendarStep.Pick) {
                TextButton(enabled = !busy, onClick = dismiss) {
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
    unchecked: Set<Int>,
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
    offer: CalendarOffer,
    dates: List<CalendarDate>,
    opened: Set<Int>,
    noCalendarApp: Boolean,
    onOpen: (Int) -> Unit,
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
        val index = offer.dates.indexOf(date)
        val done = index in opened
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DateLines(date, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onOpen(index) }) {
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
