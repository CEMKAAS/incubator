package ru.zaroslikov.incubator.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Даты приложения: разбор, запись и арифметика по дням.
 *
 * Дата закладки хранится текстом «dd.MM.yyyy» (см. корневой CLAUDE.md), и до этого
 * файла её читали и писали в шести местах шестью разными способами: часть с
 * `Locale("ru")`, часть с `Locale.getDefault()` — то есть строка могла быть записана
 * одним форматтером и прочитана другим, — а форма закладки резала её руками через
 * `split(".")` и `toInt()` и падала на всём, что не «дд.мм.гггг».
 *
 * Три правила, ради которых файл и заведён:
 *
 * 1. **Одна локаль на запись и на чтение.** `Locale("ru")` — тот вариант, который уже
 *    лежит в базе у пользователей; менять его на `Locale.US` значило бы объявить
 *    существующие строки чужими.
 * 2. **Разбор нестрогий по ширине, но строгий по смыслу** (`isLenient = false`).
 *    «5.8.2025» разбирается — разделители явные, — а «32.13.2025» превращается в
 *    `null`, а не в 1 февраля 2026 года, как это делал прежний lenient-разбор. Тихо
 *    подставленная дата хуже отсутствующей: закладка получила бы срок, который никто
 *    не назначал.
 * 3. **Форматтер живёт в [ThreadLocal].** `SimpleDateFormat` не потокобезопасен, а эти
 *    функции зовутся и из композиции, и из `viewModelScope`. Заодно это снимает
 *    аллокации: карточка закладки строит до пяти форматтеров на перерисовку, а список
 *    из двадцати карточек — сотню.
 */

/** Формат даты в базе. Менять нельзя: так лежат все существующие строки. */
const val DATE_PATTERN = "dd.MM.yyyy"

/** Формат времени в базе — час закладки и час замера. */
const val TIME_PATTERN = "HH:mm"

private val RU: Locale = Locale.forLanguageTag("ru")

private val dateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat(DATE_PATTERN, RU).apply { isLenient = false }
}

private val shortDateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("d MMM", RU)
}

private val clockFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat(TIME_PATTERN, RU)
}

/**
 * Разбирает дату из базы. `null` — строка пустая или не является датой; на такой
 * ответ рассчитан каждый вызывающий, и ни один из них не должен падать.
 */
fun parseDate(text: String): Date? {
    if (text.isBlank()) return null
    return try {
        dateFormat.get()!!.parse(text)
    } catch (e: Exception) {
        null
    }
}

/** Записывает дату в том виде, в каком её читает [parseDate]. */
fun formatDate(date: Date): String = dateFormat.get()!!.format(date)

/** Сегодняшняя дата в том же формате — единственная точка, где берётся «сегодня» текстом. */
fun todayText(): String = formatDate(Date())

/** Текущее время «ЧЧ:ММ» — час замера, проставляемый по умолчанию. */
fun clockText(): String = clockText(Date())

/** «ЧЧ:ММ» заданного момента — конец таймера проветривания в карточке и в уведомлении. */
fun clockText(date: Date): String = clockFormat.get()!!.format(date)

/** Сегодняшний день без времени суток: с ним считается номер дня инкубации. */
fun today(): Date = parseDate(todayText()) ?: Date()

/** Разница в целых сутках. */
fun daysBetween(from: Date, to: Date): Int =
    java.util.concurrent.TimeUnit.DAYS.convert(to.time - from.time, java.util.concurrent.TimeUnit.MILLISECONDS)
        .toInt()

fun Date.plusDays(days: Int): Date = Calendar.getInstance().let {
    it.time = this
    it.add(Calendar.DAY_OF_YEAR, days)
    it.time
}

/** «10 авг.» — как в макете. */
fun shortDate(date: Date): String = shortDateFormat.get()!!.format(date)

/**
 * Дата для `DatePickerState`, то есть **полночь по UTC** — именно её ждёт Material 3,
 * и именно её возвращает обратно `selectedDateMillis`. Прежняя версия этой функции
 * брала текущее время суток и оставляла его в результате, отчего выбранный день мог
 * не совпасть с показанным.
 *
 * `null` — дату разобрать не удалось; вызывающий подставляет сегодняшний день. Раньше
 * на этом месте было `sd[0].toInt()`, и пустая строка роняла приложение по нажатию на
 * поле даты.
 */
fun dateToPickerMillis(text: String): Long? {
    val date = parseDate(text) ?: return null
    val local = Calendar.getInstance().apply { time = date }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Обратный перевод: миллисекунды из `DatePickerState` в строку базы. Считается по UTC —
 * пикер отдаёт полночь по нулевому меридиану, и локальный форматтер западнее UTC
 * показал бы предыдущий день.
 */
fun pickerMillisToDate(millis: Long): String =
    SimpleDateFormat(DATE_PATTERN, RU).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
