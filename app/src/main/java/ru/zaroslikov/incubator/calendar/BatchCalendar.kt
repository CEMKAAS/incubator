package ru.zaroslikov.incubator.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Важные даты закладки для системного календаря — то, что предлагают записать сразу
 * после того, как закладку создали.
 *
 * Файл без Android: какие даты важны и как они называются, решает арифметика над
 * датой закладки, сроком вида и планом по дням, и `BatchCalendarTest` проверяет её на
 * JVM. Запись в календарь — `CalendarWriter` рядом.
 *
 * Даты три рода, и все три знает уже сама форма закладки:
 * — **овоскопирования** — из каталога видов (`SpeciesCatalog.candlingStage`), с номером;
 * — **прекращение переворота** — первый день хвоста плана, где переворотов ноль
 *   (у кур — 19-й): перекладка на вывод, самый частый пропуск в инкубации;
 * — **вывод** — дата закладки плюс срок вида, тот же момент, что у подсказки
 *   «Инкубация завершена» (`batchFinishMoment`).
 *
 * N-й день инкубации — это дата закладки плюс N−1 сутки, как в `incubationDay`.
 */
enum class CalendarDateKind { Candling, StopTurning, Hatch }

/**
 * Одна важная дата.
 *
 * @param day день инкубации; у вывода — срок вида: вывод наступает, когда он истёк.
 * @param stage номер овоскопирования, у остальных — ноль.
 */
data class CalendarDate(
    val kind: CalendarDateKind,
    val day: Int,
    val date: LocalDate,
    val stage: Int = 0,
)

/**
 * Предложение записать даты в календарь — едет в эффекте сохранения формы, как сводка
 * праздника едет в эффекте завершения: форма и так держит всё, из чего даты считаются.
 *
 * @param title название закладки без хвоста породы: у партии из нескольких пород дата
 *   закладки, вид и режим общие, и события на них одни.
 */
data class CalendarOffer(
    val title: String,
    val species: String,
    val term: Int,
    val dates: List<CalendarDate>,
)

/**
 * Важные даты закладки, начатой [start], в порядке наступления; прошедшие (раньше
 * [today]) отброшены — закладку можно завести задним числом, а
 * событие во вчера никому ни о чём не напомнит.
 *
 * @param term срок вида в сутках.
 * @param candlingStage номер овоскопирования в день инкубации, ноль — его нет.
 * @param plannedTurns план переворотов по дням, первый элемент — первый день.
 */
fun batchCalendarDates(
    start: LocalDate,
    term: Int,
    candlingStage: (day: Int) -> Int,
    plannedTurns: List<Int?>,
    today: LocalDate,
): List<CalendarDate> {
    val dates = mutableListOf<CalendarDate>()
    for (day in 1..term) {
        val stage = candlingStage(day)
        if (stage > 0) dates += CalendarDate(CalendarDateKind.Candling, day, dayDate(start, day), stage)
    }
    stopTurningDay(plannedTurns)?.takeIf { it <= term }?.let { day ->
        dates += CalendarDate(CalendarDateKind.StopTurning, day, dayDate(start, day))
    }
    dates += CalendarDate(CalendarDateKind.Hatch, term, start.plusDays(term.toLong()))
    return dates.filter { !it.date.isBefore(today) }.sortedWith(compareBy({ it.date }, { it.kind }))
}

/** Дата N-го дня инкубации. */
private fun dayDate(start: LocalDate, day: Int): LocalDate = start.plusDays((day - 1).toLong())

/**
 * Первый день хвоста плана, где не переворачивают: все дни от него до конца — ноль.
 *
 * `null`, когда такого хвоста нет, когда переворотов не было вовсе, и когда в хвосте
 * есть пустая клетка: пустая — это «квоты нет», а не «не переворачивать», и выдавать
 * по ней дату значило бы угадывать.
 */
internal fun stopTurningDay(plannedTurns: List<Int?>): Int? {
    val lastTurning = plannedTurns.indexOfLast { it != null && it > 0 }
    if (lastTurning < 0 || lastTurning == plannedTurns.lastIndex) return null
    val tail = plannedTurns.subList(lastTurning + 1, plannedTurns.size)
    if (tail.any { it != 0 }) return null
    return lastTurning + 2
}

private val RU: Locale = Locale.forLanguageTag("ru")
private val lineDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EE", RU)

/** Что это за дата — строка в диалоге. */
fun CalendarDate.label(): String = when (kind) {
    CalendarDateKind.Candling -> "Овоскопирование №$stage"
    CalendarDateKind.StopTurning -> "Прекратить переворот"
    CalendarDateKind.Hatch -> "Вывод птенцов"
}

/** Когда — вторая строка в диалоге: «7 октября, вт · 7-й день». */
fun CalendarDate.whenLine(): String {
    val date = lineDate.format(date)
    return if (kind == CalendarDateKind.Hatch) "$date · срок $day сут." else "$date · $day-й день"
}

/** Заголовок события в календаре — с названием закладки: закладок в работе бывает несколько. */
fun CalendarDate.eventTitle(offer: CalendarOffer): String = "${label()} — ${offer.title}"

/** Описание события: что делать в этот день, коротко. */
fun CalendarDate.eventDescription(offer: CalendarOffer): String {
    val what = when (kind) {
        CalendarDateKind.Candling ->
            "Просветите яйца и уберите неоплодотворённые и замершие."
        CalendarDateKind.StopTurning ->
            "Перестаньте переворачивать яйца и переложите их на вывод; " +
                "влажность — по плану этого дня."
        CalendarDateKind.Hatch ->
            "Срок инкубации истёк — пора ждать птенцов. Не забудьте записать итог в приложении."
    }
    val dayLine = if (kind == CalendarDateKind.Hatch) {
        "Срок ${offer.term} сут."
    } else {
        "День $day из ${offer.term}."
    }
    return "«${offer.title}», ${offer.species}. $dayLine\n$what"
}
