package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit

/**
 * Одна строка «Породы» в форме закладки.
 *
 * При создании строк может быть несколько, и тогда **каждая станет отдельной
 * закладкой**: у закладки одна порода (`Batch.breed`), и лоток с двумя породами — это две
 * закладки под одним расписанием, а не одна с двумя строками внутри. Поэтому строка
 * несёт ровно то, чем закладки одного лотка друг от друга отличаются: имя породы, свои
 * яйца и свою цену. Всё остальное — название, вид, дата, автоматика, режим,
 * напоминания — у них общее и берётся из [BatchUiState].
 *
 * С одной строкой [eggs] и [price] не показываются: те же величины спрашивают поля
 * самой закладки — «Количество яиц» и «Стоимость», — как и до появления строк. При
 * правке строка всегда одна — породу можно переименовать, но не добавить: у закладки
 * уже есть свои яйца, свой режим и свои замеры, и разделить их надвое задним числом
 * нельзя.
 */
data class BreedUiState(
    val name: String = "",
    val eggs: String = "",
    val price: String = "",
    val pricePerEgg: Boolean = true,
)

/**
 * Состояние формы закладки. Числа и флаги живут здесь строками и `Boolean`,
 * а в [Batch] уезжают в том виде, в каком их хранит база.
 */
data class BatchUiState(
    val id: Long = 0,
    val title: String = "",
    val type: String = "",
    val data: String = "",
    val eggAll: String = "",
    val eggAllEND: String = "",
    val airing: Boolean = false,
    val over: Boolean = false,
    val arhive: String = "",
    val dateEnd: String = "",
    val note: String = "",
    val incubatorId: Long = 0,
    /**
     * Породы под сеткой видов — по строке на будущую закладку, см. [BreedUiState]. Одна
     * пустая строка и закладка без породы — одно и то же: имя, которое не вписали, в
     * базу не уезжает. При правке строка ровно одна.
     */
    val breeds: List<BreedUiState> = listOf(BreedUiState()),
    /** Введённая стоимость; пустая строка — «не указана». */
    val price: String = "",
    val pricePerEgg: Boolean = true,
    /**
     * Итог завершения. Форма закладки этих полей не показывает — их спрашивает диалог
     * завершения в шторке, — но возит их через себя: правка закладки идёт через
     * [toBatch], и не перенеси она их, редактирование уже завершённой закладки стёрло
     * бы и причину, и цену птенцов.
     */
    val endReason: String = "",
    val chickPrice: String = "",
    val chickPricePerHead: Boolean = true,
    /** «Убрана в архив» — спрятана из списка инкубатора. Форма её тоже только возит. */
    val hidden: Boolean = false,
    /** Время закладки «ЧЧ:ММ», пара к [data]; пусто — у старой закладки его не спрашивали. */
    val time: String = "",
    /**
     * Отбраковано яиц помимо овоскопирований. Форма показывает это поле только при
     * правке: у закладки, которую ещё не заложили, отбраковывать нечего.
     */
    val eggRejected: String = "",
) {
    /** Порода закладки — первая строка; остальные при создании становятся своими закладками. */
    val breed: String get() = breeds.firstOrNull()?.name?.trim().orEmpty()

    /** Имена строк не повторяются (без учёта регистра и краёв): одна порода — одна закладка. */
    val breedNamesDistinct: Boolean
        get() = breeds.map { it.name.trim().lowercase() }.let { it.distinct().size == it.size }

    /**
     * Лоток разбит по породам: строк две и больше, и у каждой своё число яиц. Тогда
     * «Количество яиц» — их сумма, и поле не редактируется, а считается: это всего яиц по
     * закладкам, которые сохранит форма, а не яйца одной из них.
     */
    val splitByBreeds: Boolean get() = breeds.size >= 2

    /** Сколько яиц набралось по строкам пород — то, что стоит в «Количество яиц» при [splitByBreeds]. */
    val breedEggsTotal: Int get() = breeds.sumOf { it.eggs.toIntOrNull() ?: 0 }

    /**
     * Какие из уже известных пород ([known], см. `knownBreeds` в :domain) предложить в
     * выпадающем списке под строкой породы [index].
     *
     * Убрана только порода, которая уже стоит в **другой** строке формы: лоток на две
     * одинаковые породы не делят. Совпадение без учёта регистра и краёв, как и сами
     * подсказки. Сужение по набранному и прочее поведение списка — у самого поля,
     * `SuggestingSheetTextField`, общего с брендом и моделью инкубатора.
     */
    fun breedSuggestions(known: List<String>, index: Int): List<String> {
        val taken = breeds.filterIndexed { i, _ -> i != index }
            .map { it.name.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return known.filter { it.lowercase() !in taken }
    }

    /**
     * Во что обошлись яйца по строкам пород — в рублях.
     *
     * Цена за яйцо умножается на яйца своей строки, цена за всё берётся как есть. Форме
     * это нужно, чтобы показать итог в поле «Стоимость» до сохранения; в базу уедут
     * цены самих закладок, каждая своя.
     */
    val breedPriceTotal: Int
        get() = breeds.sumOf { row ->
            val price = row.price.toIntOrNull() ?: 0
            if (price <= 0) 0
            else if (row.pricePerEgg) price * (row.eggs.toIntOrNull() ?: 0) else price
        }

    /** Заложено яиц, как это запишется в базу: сумма по породам или введённое число. */
    val eggCount: Int get() = if (splitByBreeds) breedEggsTotal else eggAll.toIntOrNull() ?: 0
}

/**
 * Закладка из формы — одна, с первой строкой пород в качестве породы.
 *
 * Это то, что сохраняет правка и что создание сохраняет при одной строке. С двумя и
 * больше строками закладок столько же, сколько строк, — [toBatches].
 */
fun BatchUiState.toBatch(): Batch = Batch(
    id,
    title,
    type,
    data,
    eggAll.toIntOrNull() ?: 0,
    eggAllEND.toIntOrNull() ?: 0,
    airing.toString(),
    over.toString(),
    arhive,
    dateEnd,
    note,
    incubatorId,
    price.toIntOrNull() ?: 0,
    pricePerEgg,
    endReason,
    chickPrice.toIntOrNull() ?: 0,
    chickPricePerHead,
    hidden,
    time,
    eggRejected.toIntOrNull() ?: 0,
    breed = breed,
)

/**
 * Закладки, которые создаст форма: по одной на строку пород.
 *
 * С одной строкой — ровно [toBatch]. С двумя и больше каждая закладка берёт у своей
 * строки породу, яйца и цену, а у формы всё остальное; название получает хвост с
 * породой — «Весенняя партия — Хайсекс», — иначе две карточки в списке инкубатора
 * различались бы одной мелкой подписью, а уведомление напоминания не различало бы их
 * вовсе. Строка с пустым именем не выбрасывается: её не пускает
 * [AddBatchViewModel.isValid], а выброшенная тут она молча уменьшила бы число закладок
 * против того, что человек видел в форме.
 */
fun BatchUiState.toBatches(): List<Batch> {
    val base = toBatch()
    if (!splitByBreeds) return listOf(base)
    return breeds.map { row ->
        val name = row.name.trim()
        base.copy(
            title = splitBatchTitle(base.title, name),
            eggAll = row.eggs.toIntOrNull() ?: 0,
            price = row.price.toIntOrNull() ?: 0,
            pricePerEgg = row.pricePerEgg,
            breed = name,
        )
    }
}

/** «Весенняя партия — Хайсекс»: название закладки, заложенной вместе с другими породами. */
fun splitBatchTitle(title: String, breed: String): String =
    if (breed.isBlank()) title.trim() else "${title.trim()} — ${breed.trim()}"

/**
 * Обратное к [splitBatchTitle]: «Весенняя партия — Хайсекс» → «Весенняя партия».
 *
 * По нему узнают закладки, заложенные одним нажатием на разные породы, — у них общее
 * всё, кроме хвоста с породой. Название без такого хвоста (одна порода, переименованная
 * закладка) возвращается как есть.
 */
fun baseBatchTitle(title: String, breed: String): String {
    // Без пробела впереди: у партии без названия хвост и есть всё название, «— Хайсекс».
    val tail = "— ${breed.trim()}"
    val trimmed = title.trim()
    return if (breed.isNotBlank() && trimmed.endsWith(tail)) {
        trimmed.removeSuffix(tail).trim()
    } else {
        trimmed
    }
}

fun Batch.toBatchUiState(): BatchUiState = BatchUiState(
    id,
    title,
    type,
    data,
    eggAll.toString(),
    eggAllEND.toString(),
    airing.toBoolean(),
    over.toBoolean(),
    arhive,
    dateEnd,
    note,
    incubatorId,
    listOf(BreedUiState(name = breed)),
    if (price == 0) "" else price.toString(),
    pricePerEgg,
    endReason,
    if (chickPrice == 0) "" else chickPrice.toString(),
    chickPricePerHead,
    hidden,
    time,
    // Ноль отбраковки — это «никого не убирали», и в поле он выглядит поставленной
    // отметкой; пустая строка честнее, а в базу она вернётся тем же нулём.
    if (eggRejected == 0) "" else eggRejected.toString()
)

/**
 * Состояние правки одного дня расписания.
 *
 * Жило рядом с `BatchDayViewModel` — отдельным экраном дня, — но тот удалён вместе с
 * расписанием на весь экран: день правят прямо в карточке на вкладке «Расписание» в
 * шторке закладки ([BatchDetailViewModel.dayEdit]).
 */
data class ValueUiState(
    val id: Long = 0,
    val day: Int = 0,
    val temp: String = "",
    val damp: String = "",
    var over: String = "",
    var airingCount: String = "",
    var airingTime: String = "",
    var note: String = "",
    var idPT: Long = 0
)

/**
 * Форма держит все четыре величины строками; числа живут в [Value].
 *
 * Пустое поле — `null`, то есть «нормы нет»: так выглядит и день на автоматике
 * инкубатора, и день, у которого норму стёрли руками. Ноль пустотой не становится —
 * «не переворачивать» пользователь пишет цифрой.
 */
fun Value.toValueUiState(unit: TemperatureUnit): ValueUiState = ValueUiState(
    id, day, temp.toTempFieldText(unit), damp.toFieldText(),
    over.toCountText(), airingCount.toCountText(), airingTime.toCountText(), note, idPT
)

fun ValueUiState.toValue(unit: TemperatureUnit): Value = Value(
    id, day, temp.toCelsiusOrNull(unit), damp.toMeasureOrNull(),
    over.toCountOrNull(), airingCount.toCountOrNull(), airingTime.toCountOrNull(), note, idPT
)
