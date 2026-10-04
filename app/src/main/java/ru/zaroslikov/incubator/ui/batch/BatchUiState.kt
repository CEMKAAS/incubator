package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.components.PowerFormState
import ru.zaroslikov.incubator.ui.components.toFormState
import ru.zaroslikov.incubator.ui.components.toSettings
import ru.zaroslikov.incubator.ui.incubator.atTimeOf
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.today

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
     * Итог завершения. Впервые его спрашивает диалог завершения в шторке, а форма
     * возит его через себя: правка закладки идёт через [toBatch], и не перенеси она
     * эти поля, редактирование уже завершённой закладки стёрло бы и причину, и цену
     * птенцов. Вывод ([eggAllEND]) и цену птенцов форма у закладки, доведённой до срока,
     * ещё и показывает — см. [editableHatch]; причину досрочного — нет.
     */
    val endReason: String = "",
    val chickPrice: String = "",
    val chickPricePerHead: Boolean = true,
    /** «Убрана в архив» — спрятана из списка инкубатора. Форма её тоже только возит. */
    val hidden: Boolean = false,
    /** Время закладки «ЧЧ:ММ», пара к [data]; пусто — у старой закладки его не спрашивали. */
    val time: String = "",
    /**
     * Отбраковано руками — яйца, убранные между овоскопированиями (`Batch.eggRejected`).
     * Правится в диалоге «Отбраковано яиц» рядом с овоскопированиями по дням; поле формы
     * показывает итог обоих учётов, [rejectedTotal].
     */
    val eggRejected: String = "",
    /**
     * Выбраковано за все овоскопирования — сумма строк диалога, а не колонка закладки:
     * сами записи живут в `Batch_candling`, и форма пишет их отдельно от строки закладки.
     */
    val candlingRejected: Int = 0,
    /**
     * Потребление и тариф закладки. Новая закладка получает их от инкубатора
     * (`AddBatchViewModel.load`), и правка здесь действует на неё одну: другой режим —
     * другое потребление, другой месяц — другой тариф.
     */
    val power: PowerFormState = PowerFormState(),
    /** Час окончания «ЧЧ:ММ», пара к [dateEnd] — когда выключили инкубатор (`Batch.timeEnd`). */
    val timeEnd: String = "",
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

    /**
     * Закладка доведена до срока, и форма правки показывает её итог — сколько птенцов
     * вывелось и почём они ушли. Тот же признак, что `BatchStatus.Hatched` в `:domain`.
     *
     * Только у неё: у идущей итога ещё нет, а у прерванной вывод — ноль по определению
     * (`stoppedEarly`), и поле с ним предлагало бы переписать то, чего не было.
     */
    val editableHatch: Boolean get() = finished && endReason.isBlank()

    /** Отбраковано руками, числом; пустое поле — ноль. */
    val manualRejected: Int get() = eggRejected.toIntOrNull() ?: 0

    /**
     * Отбраковано всего — руками и на овоскопированиях. Это число стоит в поле формы и
     * вычитается из заложенного в «Осталось».
     */
    val rejectedTotal: Int get() = manualRejected + candlingRejected

    /**
     * Отбраковать больше, чем заложили, нельзя. Поля диалога зажимают себя сами, но
     * «Количество яиц» можно уменьшить уже после — тогда сохранение не пускает
     * `AddBatchState.isValid`, а форма говорит почему.
     */
    val rejectedFits: Boolean get() = rejectedTotal <= eggCount

    /**
     * Больше птенцов, чем пережило овоскопирования, не выводится. Ручная отбраковка в
     * предел не входит: у закладки, доведённой до срока, она — остаток, «заложено −
     * вывелось − овоскопирования» ([withBalancedCull]), и подстраивается под вывод.
     */
    val hatchLimit: Int
        get() = (eggCount - candlingRejected).coerceAtLeast(0)

    /**
     * Инкубация окончена — в срок или досрочно. Форма правки у такой закладки не трогает
     * то, что важно только идущей: дату и время закладки (от них считаются дни, а дни
     * уже прожиты и несут замеры) и напоминания (будить больше не о чем).
     */
    val finished: Boolean get() = arhive == "1"
}

/**
 * Птенцы закладки, доведённой до срока, когда они вписаны, — тогда они главные: отбраковка
 * подстраивается под них, а не они под неё. У идущей, прерванной и с пустым полем — `null`.
 */
val BatchUiState.fixedHatch: Int?
    get() = if (editableHatch) eggAllEND.toIntOrNull() else null

/**
 * Отбраковка закладки, доведённой до срока, — всё, что не вылупилось: ручная часть
 * пересчитывается в `заложено − вывелось − овоскопирования`, так что «заложено = вывелось +
 * отбраковано» сходится и после правки (тот же расчёт, что в `finishedOnTime`). Пустой вывод
 * не трогается — сохранить его форма всё равно не даст. У идущей и прерванной не меняется.
 */
fun BatchUiState.withBalancedCull(clampHatch: Boolean = true): BatchUiState {
    if (!editableHatch) return this
    val hatched = eggAllEND.toIntOrNull() ?: return this
    val clamped = hatched.coerceIn(0, hatchLimit)
    val manual = (eggCount - clamped - candlingRejected).coerceAtLeast(0)
    return copy(
        // Вывод зажимается, только когда правят его самого. Правка «Количества яиц» идёт
        // по символу, и на полпути от «60» к «65» в поле стоит «6»: зажатый тогда вывод
        // так и остался бы шестью. Лишний вывод держит `AddBatchState.isValid`.
        eggAllEND = if (clampHatch) clamped.toString() else eggAllEND,
        eggRejected = if (manual == 0) "" else manual.toString(),
    )
}

/** Вписанный вывод помещается в яйца, пережившие овоскопирования. */
val BatchUiState.hatchFits: Boolean
    get() = !editableHatch || (eggAllEND.toIntOrNull() ?: 0) <= hatchLimit

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
    // Птенцов не больше, чем дожило яиц, — [BatchUiState.hatchLimit]. Поле вывода зажимает
    // себя само, но заложенное и отбраковку можно поменять уже после, и тогда держит
    // только это место.
    (eggAllEND.toIntOrNull() ?: 0).coerceIn(0, hatchLimit),
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
    manualRejected,
    breed = breed,
    power = power.toSettings(),
    timeEnd = timeEnd,
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

/**
 * @param candlingRejected выбраковано на овоскопированиях этой закладки — вторая
 *        половина итога «Отбраковано яиц».
 */
fun Batch.toBatchUiState(candlingRejected: Int = 0): BatchUiState = BatchUiState(
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
    if (eggRejected == 0) "" else eggRejected.toString(),
    candlingRejected,
    power.toFormState(),
    timeEnd,
)

/** Состояние правки одного дня расписания — в карточке дня шторки закладки ([BatchDetailViewModel]). */
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

/**
 * Когда закладку, доведённую до срока, закончили — то, что спросил диалог завершения.
 * У закладок, завершённых до появления поля, час пустой и показывается прочерком.
 */
val BatchUiState.finishMoment: FinishMoment
    get() = FinishMoment(dateEnd, timeEnd)

/**
 * Ошибка в моменте окончания, или `null`. Проверяется только вписанный час: пустой —
 * «не спрашивали», и подставлять за человека час закладки, чтобы потом запретить
 * сохранение из-за него, нельзя — у закладки, завершённой в день вывода раньше часа
 * закладки, такой момент вышел бы «ещё не наступившим».
 */
val BatchUiState.finishMomentError: String?
    get() = if (timeEnd.isBlank()) {
        // Без часа сравниваются одни даты: день окончания раньше дня закладки — ошибка
        // при любом часе, и такая закладка осталась бы без света и с «Днём 1».
        val start = parseDate(data)
        val end = parseDate(dateEnd)
        when {
            start != null && end != null && end.before(start) -> "Раньше, чем заложили яйца"
            end != null && end.after(today()) -> "Этот момент ещё не наступил"
            else -> null
        }
    } else finishMomentError(finishMoment, parseDate(data)?.atTimeOf(time))
