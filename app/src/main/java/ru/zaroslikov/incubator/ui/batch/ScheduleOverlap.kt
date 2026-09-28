package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.parseDate
import java.util.Date

/**
 * Сравнение режима новой закладки с режимом закладок, уже идущих в том же приборе, —
 * без Compose и без базы, чтобы `ScheduleOverlapTest` гонял его на JVM.
 *
 * Один инкубатор — один воздух: температура и влажность в нём общие на всех, кто в нём
 * лежит. Закладывая индюков к курицам, которые на одиннадцатом дне, человек на первые
 * одиннадцать дней получает не два режима, а один компромисс, и увидеть это надо в
 * таблице, пока она правится, а не через неделю по замерам. Поэтому клетки температуры
 * и влажности красятся по тому, насколько их план расходится с планом соседей на ту же
 * *дату*: зелёная — совпадает, жёлтая — допустимо, красная — расходится. С дня, когда
 * соседей в приборе уже не будет, цвета нет — таблица там такая же, как была.
 *
 * Переворот и проветривание не сравниваются намеренно: воздух общий, а руки к каждому
 * лотку свои — курицам можно переворачивать трижды, а индюкам рядом шесть раз, и
 * расхождением это не является.
 */

/**
 * Идущая закладка-сосед и её дни, как они лежат в базе.
 *
 * `@Immutable` над моделями `:domain` с полями `var` — то же обещание, что у
 * `AddBatchState`: строки соседа читаются и никогда не правятся на месте; в
 * `setAutoIncubator`, единственное место, которое правит [Value] на месте, они не попадают.
 */
@Immutable
data class NeighbourSchedule(val batch: Batch, val rows: List<Value>)

/**
 * Приговор одной строки: отдельно по температуре и по влажности, потому что одна из
 * двух может быть пустой или не заданной ни у кого из соседей — тогда у неё `null`,
 * «сравнивать не с чем», а не «в норме». [neighbours] — сколько соседей лежат в приборе
 * в этот день; клетка называет его в своём описании для `TalkBack`, поскольку цвет
 * скринридер не читает. Ноль здесь не бывает — у дня без соседей приговора нет вовсе.
 */
@Immutable
data class CellVerdicts(
    val temp: Severity?,
    val damp: Severity?,
    val neighbours: Int,
) {
    /** Есть хоть одна заливка — иначе строка белая, хоть сосед и есть. */
    val tinted: Boolean get() = temp != null || damp != null
}

/**
 * Пороги — те же, что у плиток «Температура» / «Влажность» в шторке закладки
 * (`severityOf(delta, 0.2, 0.5)` и `severityOf(delta, 3.0, 7.0)`): «в норме» там и
 * «совпадает» здесь — один и тот же вопрос о том, заметит ли эмбрион разницу.
 */
internal const val OVERLAP_TEMP_MINOR = 0.2
internal const val OVERLAP_TEMP_MAJOR = 0.5
internal const val OVERLAP_DAMP_MINOR = 3.0
internal const val OVERLAP_DAMP_MAJOR = 7.0

/**
 * Приговоры по строкам таблицы — по одному на строку [rows], в её порядке; `null` там,
 * где в этот день ни один сосед в приборе не лежит.
 *
 * День N новой закладки и день соседа на ту же дату отличаются на постоянное смещение —
 * целые сутки между двумя датами закладки, `daysBetween(его дата, наша)`, та же
 * арифметика, что у [incubationDay]: день соседа для строки N это `N + смещение`, и
 * строка с этим номером есть его план. Одно вычитание на соседа, а не дата на каждую
 * строку: считается это на каждый набранный символ, и `Calendar` на тридцать строк тут
 * ни к чему; заодно обе половины расчёта идут одной арифметикой — `plusDays` через
 * `Calendar` считал бы календарные сутки, `daysBetween` — усечённые миллисекунды, и на
 * переводе часов они разошлись бы на день. Номер за пределами расписания соседа — в
 * этот день его в приборе нет (он ещё не заложен или уже вынут), и в сравнение он не
 * входит. Цель — среднее по соседям, по каждому полю среди тех, у кого оно задано, по
 * тому же правилу, что `averagePlan` в шторке замеров инкубатора: один термометр, и
 * держать его можно только между тем, что просят закладки.
 *
 * Температура строки приходит в градусах экрана и переводится в Цельсии, в которых
 * лежат планы соседей и написаны пороги. Дата закладки, которую не удалось разобрать,
 * даёт пустой список: без даты не сказать, какие дни у кого совпадут.
 */
internal fun overlapVerdicts(
    rows: List<ValueUiState>,
    start: Date?,
    neighbours: List<NeighbourSchedule>,
    unit: TemperatureUnit,
): List<CellVerdicts?> {
    if (start == null || neighbours.isEmpty() || rows.isEmpty()) return List(rows.size) { null }
    val shifted = neighbours.mapNotNull { n ->
        val theirStart = parseDate(n.batch.data) ?: return@mapNotNull null
        daysBetween(theirStart, start) to n.rows.associateBy { it.day }
    }
    if (shifted.isEmpty()) return List(rows.size) { null }
    return rows.map { row ->
        val plans = shifted.mapNotNull { (shift, byDay) -> byDay[row.day + shift] }
        if (plans.isEmpty()) return@map null
        val tempTarget = plans.mapNotNull { it.temp }.takeIf { it.isNotEmpty() }?.average()
        val dampTarget = plans.mapNotNull { it.damp }.takeIf { it.isNotEmpty() }?.average()
        // Разница округляется до сотых — до точности ввода — прежде чем сравниваться
        // с порогом: 38.0 − 37.8 в двоичной арифметике чуть больше 0.2, и без округления
        // ровно пороговая разница, самая частая в таблице с одним знаком после точки,
        // выпадала бы из «совпадает» в «допустимо».
        CellVerdicts(
            temp = deltaOf(row.temp.toCelsiusOrNull(unit), tempTarget)
                ?.let { severityOf(round2(it), OVERLAP_TEMP_MINOR, OVERLAP_TEMP_MAJOR) },
            damp = deltaOf(row.damp.toMeasureOrNull(), dampTarget)
                ?.let { severityOf(round2(it), OVERLAP_DAMP_MINOR, OVERLAP_DAMP_MAJOR) },
            neighbours = plans.size,
        )
    }
}

/**
 * Что клетка говорит скринридеру вместо цвета: приговор и число соседей, с которыми он
 * посчитан. `null` — клетка без приговора, и описания у неё нет, как нет и заливки.
 */
internal fun Severity?.overlapDescription(neighbours: Int): String? {
    val verdict = when (this) {
        Severity.Normal -> "совпадает"
        Severity.Minor -> "допустимое расхождение"
        Severity.Major -> "расходится"
        null -> return null
    }
    return "$verdict с ${neighbours.pluralBatches()}"
}

private fun Int.pluralBatches(): String {
    val word = if (this % 10 == 1 && this % 100 != 11) "закладкой" else "закладками"
    return "$this $word"
}
