package ru.zaroslikov.incubator.domain.model

/**
 * Свой вид птицы — режим инкубации, который пользователь описал сам.
 *
 * Встроенные виды («Курицы», «Гуси»…) живут кодом в `domain.incubation`: срок, режим по
 * дням и дни овоскопирования у них зашиты в `when`. Свой вид — та же тройка знаний, но
 * записанная строками в базе, и отвечает по ней
 * [SpeciesCatalog][ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog]: он один
 * знает, где кончаются встроенные виды и начинаются свои.
 *
 * Вид общий для всего хозяйства, а не для одного инкубатора: закладка ссылается на него
 * по [name] через `Batch.type`, ровно как на встроенный, и два инкубатора с «Цесарками»
 * должны означать одних и тех же цесарок. Поэтому имя уникально, и переименование
 * обязано пройти по закладкам (см. `ItemsRepository.saveCustomSpecies`).
 *
 * [days] — по одной строке на день, подряд с первого; их число и есть срок инкубации.
 * Правка дней **не** переписывает расписание уже созданных закладок: их `Value`-строки
 * запечены при создании, как и у встроенного вида (см. `Batch.airing` / `Batch.over`).
 * Дни овоскопирования, напротив, считаются живьём: у закладки они нигде не хранятся.
 */
data class CustomSpecies(
    val id: Long = 0,
    val name: String,
    val days: List<CustomSpeciesDay> = emptyList(),
) {
    /** Срок инкубации — число описанных дней. */
    val length: Int get() = days.size

    /** Дни, в которые вид просит овоскопирование, по возрастанию. */
    val candlingDays: List<Int> get() = days.filter { it.candling }.map { it.day }.sorted()
}

/**
 * Один день своего вида: план дня, как в [Value], плюс отметка овоскопирования.
 *
 * Четыре величины плана — те же и с тем же смыслом `null`, что у [Value]: «нормы нет».
 * Ноль — «не делать». [candling] — флаг, а не отдельная таблица дней: у встроенного
 * вида список дней овоскопирования и режим по дням тоже задаются вместе, и разносить их
 * по двум таблицам значило бы позволить им разъехаться.
 */
data class CustomSpeciesDay(
    val id: Long = 0,
    val speciesId: Long = 0,
    val day: Int,
    val temp: Double?,
    val damp: Double?,
    val over: Int?,
    val airingCount: Int?,
    val airingTime: Int?,
    val candling: Boolean = false,
)

/**
 * Строка расписания закладки из дня своего вида — то, что `setIncubator` порождает для
 * встроенного. Заметка пуста, а `idPT` нулевой: закладки, к которой привязать день, ещё
 * нет, её проставит `setIdPT` при сохранении.
 */
fun CustomSpeciesDay.toValue(): Value = Value(
    day = day,
    temp = temp,
    damp = damp,
    over = over,
    airingCount = airingCount,
    airingTime = airingTime,
    note = "",
    idPT = 0,
)
