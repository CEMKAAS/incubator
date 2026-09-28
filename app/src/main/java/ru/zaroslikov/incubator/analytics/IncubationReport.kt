package ru.zaroslikov.incubator.analytics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.stats.StatsSlice
import ru.zaroslikov.incubator.domain.stats.incubatorStats
import ru.zaroslikov.incubator.ui.daysBetween
import ru.zaroslikov.incubator.ui.parseDate

/**
 * Отчёт об инкубаторе по итогам закладки — событие [Events.INCUBATION_REPORT].
 *
 * Шлётся всякий раз, когда инкубация чем-то кончилась: доведена до срока, прервана
 * досрочно или прервана архивом своего инкубатора. Всё, ради чего событие заведено, —
 * связать **устройство** (бренд, модель, вместимость) с **результатом** (вывод этой
 * закладки и накопленная эффективность устройства). Ни того, ни другого по отдельности
 * для вопроса «какая модель выводит лучше» не хватает.
 *
 * Ничего нового в базе под это не появилось и не должно: и бренд с моделью, и
 * эффективность уже посчитаны — первое лежит в [Incubator], вторая считается
 * `incubatorStats` в `:domain`, тем же кодом, что рисует вкладку «Статистика». Второй
 * счёт эффективности разошёлся бы с первым, и отчёт спорил бы с экраном.
 */

/**
 * Собирает и отправляет отчёт по каждой из только что законченных [finished].
 *
 * Список, а не одна закладка, потому что путей завершения три, и один из них
 * групповой: архив инкубатора прерывает разом все идущие в нём закладки. Устройство,
 * его закладки и овоскопирования читаются **один раз на все** — иначе три прерванные
 * закладки означали бы девять чтений базы ради одного нажатия.
 *
 * **Вызывать после записи в базу.** Эффективность считается по тем же закладкам, что
 * лежат в базе сейчас, и закладка, чей итог ещё не записан, занизила бы её собственным
 * нулём — то есть отчёт о ней самой врал бы о ней самой.
 *
 * Сбой чтения отчёт не отменяет: событие уйдёт с тем, что удалось прочитать, — итог
 * закладки известен и без базы. Отмену корутины при этом пропускаем наружу, а не
 * глотаем как ошибку чтения: [runCatching] здесь был бы той же самой ловушкой, из-за
 * которой `ReminderWorker` однажды принял штатную остановку за «закладки не существует».
 */
suspend fun ItemsRepository.reportIncubationOutcomes(
    incubatorId: Long,
    finished: List<Batch>,
) {
    if (finished.isEmpty()) return
    val incubator = readOrNull { getIncubator(incubatorId).first() }
    val batches = readOrNull { getBatchesFor(incubatorId).first() } ?: finished
    val candlings = readOrNull { getCandlingsFor(incubatorId).first() }.orEmpty()

    finished.forEach { batch ->
        Analytics.report(
            Events.INCUBATION_REPORT,
            incubationReport(batch, incubator, batches, candlings),
        )
    }
}

/** Чтение, которое не обязано получиться, но обязано не проглотить отмену. */
private suspend fun <T> readOrNull(read: suspend () -> T): T? = try {
    read()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    null
}

/**
 * Параметры отчёта: итог закладки плюс вложенный блок «Инкубатор».
 *
 * Чистая функция — потому её и проверяет `IncubationReportTest` обычным JVM-тестом,
 * без эмулятора и без AppMetrica.
 *
 * Вложенный блок, а не плоский список из пятнадцати ключей: в отчёте AppMetrica он
 * разворачивается деревом, и «Инкубатор → Модель» читается как один разрез. Заодно это
 * снимает столкновение имён — «Автопереворот» у закладки означает «переворачивал
 * инкубатор сам», а у устройства «умеет переворачивать», и в одной плоской карте два
 * этих смысла пришлось бы различать формулировкой ключа.
 *
 * Флаги автоматики стоят у закладки, а не у устройства, там, где речь о результате:
 * закладка помнит, что использовали **при её** генерации, и переписывать это задним
 * числом нельзя (см. [Batch]). У устройства то же самое — только возможность, и она
 * могла успеть измениться.
 *
 * @param batches все закладки инкубатора, уже с записанным итогом [batch].
 * @param candlings овоскопирования всех закладок инкубатора вперемешку; лишние не
 *        помешают, [incubatorStats] раскладывает их по идентификаторам.
 */
internal fun incubationReport(
    batch: Batch,
    incubator: Incubator?,
    batches: List<Batch>,
    candlings: List<Candling>,
): Map<String, Any?> {
    val stats = incubatorStats(batches, candlings)
    val rejected = batch.eggRejected + candlings.filter { it.idPT == batch.id }.sumOf { it.rejected }

    return buildMap {
        put("Исход", outcomeOf(batch))
        // Причина есть только у прерванной, и она же отличает «сняли руками» от
        // «инкубатор ушёл в архив»: у второго причина своя, константой.
        put("Причина", batch.endReason)
        put("Вид", batch.type)
        put("Порода", batch.breed)
        put("Заложено", batch.eggAll)
        put("Выведено", batch.eggAllEND)
        put("Отбраковано", rejected)
        // Процент этой закладки; яиц не заложено — процента нет, а не ноль.
        percent(batch.eggAllEND, batch.eggAll)?.let { put("Вывод, %", it) }
        daysRun(batch)?.let { put("Дней", it) }
        put("Автопереворот", batch.over.toBoolean())
        put("Автопроветривание", batch.airing.toBoolean())

        if (incubator != null) {
            put("Инкубатор", incubatorBlock(incubator, batch, stats.total))
        }
    }
}

/**
 * Блок об устройстве: чем оно является и что успело вывести.
 *
 * «Эффективность, %» — [StatsSlice.rate], то самое число, которое вкладка
 * «Статистика» показывает как эффективность инкубатора: процент
 * вывода **по завершённым** закладкам. Незавершённые в знаменатель не идут — у идущей
 * закладки птенцов ноль, и она вдвое занизила бы любой показатель. Нет ни одной
 * завершённой — параметра нет: «неизвестно» и «ноль» это разные ответы.
 *
 * «Загрузка, %» — сколько яиц этой закладки от вместимости устройства. Вопрос «полный
 * инкубатор выводит хуже полупустого» без неё не задать, а нового поля она не требует:
 * оба числа уже есть.
 */
private fun incubatorBlock(
    incubator: Incubator,
    batch: Batch,
    total: StatsSlice,
): Map<String, Any> = buildMap {
    put("Название", incubator.name)
    put("Бренд", incubator.brand)
    put("Модель", incubator.model)
    if (incubator.capacity > 0) {
        put("Вместимость", incubator.capacity)
        percent(batch.eggAll, incubator.capacity)?.let { put("Загрузка, %", it) }
    }
    put("Умеет переворачивать", incubator.autoTurn)
    put("Умеет проветривать", incubator.autoAiring)
    total.rate?.let { put("Эффективность, %", it) }
    put("Закладок завершено", total.finishedBatches)
    put("Яиц завершено", total.finishedEggs)
    put("Птенцов всего", total.hatched)
    put("В архиве", incubator.hidden)
}.filterValues { it != "" }

/**
 * Как кончилась инкубация — три слова, по которым режется всё остальное.
 *
 * [BatchStatus.Active] здесь не бывает: отчёт шлётся после записи итога. Если всё же
 * попался — так и написано, вместо того чтобы выдать его за один из двух исходов.
 */
private fun outcomeOf(batch: Batch): String = when (batch.status) {
    BatchStatus.Hatched -> "В срок"
    BatchStatus.Stopped -> "Досрочно"
    BatchStatus.Active -> "Не завершена"
}

/**
 * Сколько дней закладка простояла в инкубаторе — от даты закладки до даты завершения.
 *
 * Не срок вида и не «День N»: у прерванной закладки интересно именно то, сколько она
 * успела пройти. `null` — дат в базе нет или они не читаются (закладки первых версий).
 */
private fun daysRun(batch: Batch): Int? {
    val start = parseDate(batch.data) ?: return null
    val end = parseDate(batch.dateEnd) ?: return null
    return daysBetween(start, end).coerceAtLeast(0)
}

/** Целый процент [part] от [whole]; `null` — знаменателя нет, то есть ответа тоже. */
private fun percent(part: Int, whole: Int): Int? =
    if (whole > 0) part * 100 / whole else null
