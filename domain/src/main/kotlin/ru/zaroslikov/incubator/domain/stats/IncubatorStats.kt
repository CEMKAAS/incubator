package ru.zaroslikov.incubator.domain.stats

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.status

/**
 * Статистика инкубатора: всё, что вкладка «Статистика» показывает, посчитано здесь.
 *
 * Функции чистые и живут в `:domain`, а не рядом с экраном, по той же причине, что и
 * [ru.zaroslikov.incubator.domain.model.status]: считать проценты вывода — арифметика
 * над закладками, а не работа Compose, и проверяется она обычным JVM-тестом за секунды
 * (`gradlew :domain:test`), без эмулятора. Новых полей в базе под это не заведено:
 * всё берётся из уже сохранённых закладок и овоскопирований.
 */

/** Порода не заполнена — в разрезах такие закладки собираются в одну строку. */
const val NO_BREED = "Без породы"

/*
 * Порода — одно поле закладки (`Batch.breed`): в одной закладке одна порода, и разрез по
 * породам — это группировка закладок внутри вида, ровно как разрез по видам — группировка
 * закладок инкубатора. Никакого второго учёта внутри закладки нет.
 */

/**
 * Одна строка разреза — по виду птицы, по породе внутри вида или по всему инкубатору.
 *
 * [eggs] считается по всем закладкам разреза, включая идущие: «сколько яиц этого вида
 * прошло через инкубатор» — вопрос про заложенное, и незаконченная закладка на него
 * отвечает наравне с остальными.
 *
 * [rate] — наоборот, только по завершённым ([finishedEggs] как знаменатель). У идущей
 * закладки [Batch.eggAllEND] ещё ноль, и она занизила бы процент до бессмыслицы. Пока
 * завершённых в разрезе нет, [rate] равен `null` — это «неизвестно», а не «ноль».
 *
 * Разница двух знаменателей — [activeEggs] и [activeBatches] — вынесена наружу не для
 * красоты: экран обязан её показывать. Иначе «700 яиц» рядом с «429 вылупилось» читается
 * как арифметическая ошибка, хотя это просто два разных вопроса.
 */
data class StatsSlice(
    val name: String,
    /** Заложено яиц — по всем закладкам разреза. */
    val eggs: Int = 0,
    /** Выведено птенцов — по всем закладкам разреза. */
    val hatched: Int = 0,
    /** Отбраковано: графа закладки плюс итоги её овоскопирований. */
    val rejected: Int = 0,
    /** Сколько закладок в разрезе всего. */
    val batches: Int = 0,
    /** Из них завершённых — доведённых до срока и прерванных вместе. */
    val finishedBatches: Int = 0,
    /** Яиц в завершённых закладках; знаменатель [rate]. */
    val finishedEggs: Int = 0,
    /** Птенцов в завершённых закладках; числитель [rate]. */
    val finishedHatched: Int = 0,
    /**
     * Вид птицы, внутри которого посчитан разрез, — пусто у разреза по виду и у «Всего».
     *
     * Заполнен у породы. Одна строка — одна порода **одного** вида: породы считаются
     * внутри вида, а не по всему инкубатору, поэтому вид здесь ровно один и вопрос
     * «а это чья порода» на строке всегда имеет ответ.
     */
    val species: String = "",
) {
    /**
     * Процент вывода по завершённым закладкам; `null` — завершённых ещё нет.
     *
     * Числитель тоже завершённый, а не общий [hatched], хотя сегодня это одно и то же
     * число: у идущей закладки птенцов ноль. Стоит когда-нибудь начать записывать
     * вывод до завершения — и общий числитель над завершённым знаменателем выдал бы
     * проценты больше ста.
     */
    val rate: Int? get() = if (finishedEggs > 0) finishedHatched * 100 / finishedEggs else null

    /** Яйца в ещё идущих закладках: [eggs] минус [finishedEggs]. */
    val activeEggs: Int get() = eggs - finishedEggs

    /** Идущие закладки разреза. */
    val activeBatches: Int get() = batches - finishedBatches
}

/**
 * Вид птицы со своими породами. [breeds] отсортированы так же, как и виды — по яйцам.
 *
 * Это единственный разрез по породам, который считает [incubatorStats]: и диаграмма, и
 * карточка «Эффективность по породам» разворачивают один и тот же список, только вторая
 * показывает все виды сразу, а первая — по нажатию.
 */
data class SpeciesStats(
    val slice: StatsSlice,
    val breeds: List<StatsSlice>,
)

/**
 * Строка «Истории выводов» — одна завершённая закладка.
 *
 * Прерванные попадают сюда наравне с доведёнными до срока: «сорвалось» — тоже итог, и
 * история, из которой его вычеркнули, врёт про инкубатор. Отличает их [status], по
 * которому строка и красится.
 */
data class HatchRecord(
    val batchId: Long,
    val title: String,
    /**
     * Название инкубатора закладки; **пустое, когда разрез — один инкубатор**.
     *
     * Экран инкубатора имён не передаёт: в его истории все строки об одном устройстве, и
     * названное в каждой оно превратилось бы в столбец одинакового текста, отодвинувший
     * породу и дату. «Аналитике» же оно нужно в каждой строке — там история хозяйства, и
     * «где это было» её первый вопрос. Решает это вызывающий, один раз, картой в
     * [incubatorStats]; здесь и на экране остаётся простое «пусто — не печатаем».
     */
    val incubator: String,
    val species: String,
    /** Порода закладки; пусто, когда её не указали. */
    val breed: String,
    /** Дата завершения «dd.MM.yyyy»; пустая у закладок, завершённых до появления поля. */
    val dateEnd: String,
    val eggs: Int,
    val hatched: Int,
    val rejected: Int,
    val status: BatchStatus,
) {
    /** Процент вывода этой закладки; `null` — яиц не заложено (в базе так бывает). */
    val rate: Int? get() = if (eggs > 0) hatched * 100 / eggs else null
}

/**
 * Всё, что показывает вкладка: четыре числа сверху, разрезы и история.
 *
 * [rejected] — сумма двух разных учётов отбраковки, и это осознанно: [Batch.eggRejected]
 * записывают руками в форме закладки за яйца, убранные между овоскопированиями, а
 * [Candling.rejected] — итог самого овоскопирования. Одно другого не заменяет, ровно
 * как в «Осталось» на карточке закладки.
 *
 * Верхние числа — это [total] целиком, а не четыре отдельных поля: экрану нужны из него
 * ещё и знаменатели ([StatsSlice.finishedEggs], [StatsSlice.activeEggs]), чтобы под
 * каждой плиткой написать, из чего число сложилось. Четыре прежних поля остались
 * вычисляемыми — по ним читается и пишется весь экран.
 */
data class IncubatorStats(
    val total: StatsSlice = StatsSlice("Всего"),
    val bySpecies: List<SpeciesStats> = emptyList(),
    val history: List<HatchRecord> = emptyList(),
) {
    val totalEggs: Int get() = total.eggs
    val hatched: Int get() = total.hatched
    val rejected: Int get() = total.rejected

    /** Эффективность инкубатора: процент вывода по завершённым; `null` — таких нет. */
    val hatchRate: Int? get() = total.rate

    /**
     * Все породы инкубатора одним списком — для вопроса «какая выводится лучше».
     *
     * Вычисляемое, а не своя группировка: породы уже посчитаны внутри видов, и второй
     * счёт по тем же закладкам рано или поздно разошёлся бы с первым — на экране
     * получились бы два разных процента у одной и той же породы.
     */
    val breeds: List<StatsSlice> get() = bySpecies.flatMap { it.breeds }
}

/**
 * Считает статистику инкубатора по его закладкам и их овоскопированиям.
 *
 * [candlings] — записи всех закладок инкубатора вперемешку; раскладываются по
 * [Candling.idPT]. Лишние (от чужих закладок) не помешают: суммируется только то, что
 * нашлось по идентификаторам из [batches].
 *
 * Спрятанные в архив закладки сюда приходят наравне с остальными — прятать закладку
 * значит убрать её с глаз, а не сделать небывшей, и все показатели инкубатора считают
 * её как всякую другую.
 */
fun incubatorStats(
    batches: List<Batch>,
    candlings: List<Candling>,
    /**
     * Названия инкубаторов по их идентификаторам — только для строк «Истории выводов».
     *
     * Пустая карта (и она же по умолчанию) — разрез в один инкубатор: [HatchRecord.incubator]
     * тогда пуст, и строка выглядит ровно как прежде. Карта, а не поле у закладки: имя
     * устройства не свойство закладки, и класть его в [Batch] значило бы носить его через
     * весь домен ради одной подписи.
     */
    incubatorNames: Map<Long, String> = emptyMap(),
): IncubatorStats {
    if (batches.isEmpty()) return IncubatorStats()

    val candlingsByBatch = candlings.groupBy { it.idPT }
    val rejectedOf = { batch: Batch ->
        batch.eggRejected + (candlingsByBatch[batch.id]?.sumOf { it.rejected } ?: 0)
    }

    val total = sliceOf("Всего", batches, rejectedOf)

    val bySpecies = batches
        .groupBy { it.type }
        .map { (type, rows) ->
            SpeciesStats(
                slice = sliceOf(type, rows, rejectedOf),
                breeds = rows
                    .groupBy { breedKey(it.breed) }
                    .map { (breed, lines) -> sliceOf(breed, lines, rejectedOf, species = type) }
                    .sortedWith(sliceOrder),
            )
        }
        .sortedWith(compareBy(sliceOrder) { it.slice })

    val history = batches
        .filter { it.status != BatchStatus.Active }
        .map { batch ->
            HatchRecord(
                batchId = batch.id,
                title = batch.title.ifBlank { batch.type },
                incubator = incubatorNames[batch.incubatorId].orEmpty(),
                species = batch.type,
                breed = batch.breed,
                dateEnd = batch.dateEnd,
                eggs = batch.eggAll,
                hatched = batch.eggAllEND,
                rejected = rejectedOf(batch),
                status = batch.status,
            )
        }
        // Свежие сверху. Дата — текст «dd.MM.yyyy», сортировать его как строку нельзя,
        // поэтому пересобираем в ISO; пустая (закладки, завершённые до появления поля)
        // уезжает вниз, а не наверх, как это сделала бы пустая строка сама по себе.
        .sortedByDescending { isoOrBlank(it.dateEnd) }

    return IncubatorStats(
        total = total,
        bySpecies = bySpecies,
        history = history,
    )
}

/**
 * Порядок разрезов: сперва по заложенным яйцам, потом по имени.
 *
 * Имя вторым ключом не косметика: цвет столбца диаграммы задан позицией в списке, и
 * без него два вида с одинаковым числом яиц меняли бы цвета местами при каждом
 * пересчёте потока.
 */
private val sliceOrder: Comparator<StatsSlice> =
    compareByDescending<StatsSlice> { it.eggs }.thenBy { it.name }

/** Ключ разреза по породе: пустая и пробельная порода — одна строка [NO_BREED]. */
private fun breedKey(name: String): String = name.trim().ifBlank { NO_BREED }

private fun sliceOf(
    name: String,
    rows: List<Batch>,
    rejectedOf: (Batch) -> Int,
    species: String = "",
): StatsSlice {
    val finished = rows.filter { it.status != BatchStatus.Active }
    return StatsSlice(
        name = name,
        eggs = rows.sumOf { it.eggAll },
        hatched = rows.sumOf { it.eggAllEND },
        rejected = rows.sumOf { rejectedOf(it) },
        batches = rows.size,
        finishedBatches = finished.size,
        finishedEggs = finished.sumOf { it.eggAll },
        finishedHatched = finished.sumOf { it.eggAllEND },
        species = species,
    )
}

/**
 * «dd.MM.yyyy» → «yyyy-MM-dd» для сравнения; мусор и пустое — пустая строка.
 *
 * Общая на весь пакет: по дате завершения сортируется и история выводов, и список
 * закладок в «Финансах» ([incubatorFinance]).
 */
internal fun isoOrBlank(date: String): String =
    if (date.length == 10) "${date.substring(6)}-${date.substring(3, 5)}-${date.substring(0, 2)}"
    else ""
