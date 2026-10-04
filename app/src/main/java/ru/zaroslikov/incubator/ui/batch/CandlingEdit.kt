package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.ui.parseDate
import java.util.Date

/**
 * Одно овоскопирование в форме правки закладки: день, его номер по счёту и сколько на
 * нём выбраковали.
 *
 * [rejected] — строка, как все числовые поля формы, и пустая значит «не проводили»:
 * запись за этот день при сохранении удаляется. «0» — другое: овоскопирование было, и
 * всё оказалось цело, — тот же ответ, что даёт экран овоскопирования. [saved] — запись,
 * какой она лежит в базе; `null` — за этот день её ещё нет.
 */
@Immutable
data class CandlingEditRow(
    val day: Int,
    val stage: Int,
    val rejected: String,
    val saved: Candling?,
)

/**
 * Строки овоскопирования для формы правки: каждый день овоскопирования вида, который
 * закладка уже прожила, плюс любой день, за который запись уже есть.
 *
 * Будущие дни не показываются: итог овоскопирования, которого не было, — выдумка.
 * Докуда закладка дожила, решает статус: доведённая до срока — весь срок, прерванная —
 * до даты остановки, идущая — до сегодня, тем же [incubationDay], что и «День N» в
 * шторке. Запись в день, который вид овоскопированием уже не считает, остаётся в списке:
 * она входит в отбраковку, и её нужно уметь исправить.
 */
internal fun candlingEditRows(
    batch: Batch,
    catalog: SpeciesCatalog,
    saved: List<Candling>,
    now: Date,
): List<CandlingEditRow> {
    val total = catalog.incubationDays(batch.type)
    val lastDay = when {
        total == null -> 0
        batch.status == BatchStatus.Hatched -> total
        // По датам, как `BatchDetailViewModel.load` считает «День N» прерванной: о моменте
        // остановки известно число, и час закладки к нему отнял бы у неё последний день.
        batch.status == BatchStatus.Stopped ->
            incubationDay(parseDate(batch.data), total, parseDate(batch.dateEnd) ?: now)
        else -> incubationDay(batchStartMoment(batch), total, now)
    }
    val byDay = saved.associateBy { it.day }
    val days = ((1..lastDay).filter { catalog.isCandlingDay(batch.type, it) } + byDay.keys)
        .distinct()
        .sorted()
    return days.map { day ->
        val record = byDay[day]
        CandlingEditRow(
            day = day,
            stage = catalog.candlingStage(batch.type, day),
            rejected = record?.rejected?.toString().orEmpty(),
            saved = record,
        )
    }
}

/** Выбраковано по всем строкам, как это сохранится; пустая строка — ноль. */
internal fun List<CandlingEditRow>.rejectedSum(): Int =
    sumOf { it.rejected.toIntOrNull() ?: 0 }

/** Что записать и что удалить, чтобы база совпала со строками формы. */
internal data class CandlingWrites(val save: List<Candling>, val delete: List<Candling>) {
    val isEmpty: Boolean get() = save.isEmpty() && delete.isEmpty()
}

/**
 * Разница между строками формы и базой; неизменённая строка не пишется. Новая запись
 * получает дату [today] — день, когда итог внесли, как и у экрана овоскопирования.
 */
internal fun candlingWrites(
    batchId: Long,
    rows: List<CandlingEditRow>,
    today: String,
): CandlingWrites {
    val save = mutableListOf<Candling>()
    val delete = mutableListOf<Candling>()
    rows.forEach { row ->
        val value = row.rejected.toIntOrNull()
        when {
            value == null -> row.saved?.let(delete::add)
            row.saved == null ->
                save += Candling(idPT = batchId, day = row.day, date = today, rejected = value)
            row.saved.rejected != value -> save += row.saved.copy(rejected = value)
        }
    }
    return CandlingWrites(save, delete)
}

/**
 * Что набрано в диалоге «Отбраковано яиц»: ручная отбраковка ([manual], строкой, как в
 * поле) и овоскопирования по дням. Итог внизу диалога — [total].
 */
@Immutable
data class RejectedDraft(
    val manual: String,
    val candlings: List<CandlingEditRow>,
) {
    val manualCount: Int get() = manual.toIntOrNull() ?: 0
    val candlingSum: Int get() = candlings.rejectedSum()
    val total: Int get() = manualCount + candlingSum
}
