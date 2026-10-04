package ru.zaroslikov.incubator.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Даты приложения: разбор, запись и арифметика по дням. Дата закладки хранится текстом «dd.MM.yyyy».
 *
 * 1. **Одна локаль на запись и на чтение** — `Locale("ru")`, как уже лежит в базе у пользователей.
 * 2. **Разбор строгий** (`isLenient = false`): «5.8.2025» читается, «32.13.2025» даёт `null`, а не
 *    тихо подставленную дату, которую никто не назначал.
 * 3. **Форматтер в [ThreadLocal]**: `SimpleDateFormat` не потокобезопасен, а функции зовутся и из
 *    композиции, и из `viewModelScope`.
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
 * Дата для `DatePickerState` — **полночь по UTC**, как её ждёт и возвращает Material 3.
 * `null` — разобрать не удалось; вызывающий подставляет сегодняшний день.
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
