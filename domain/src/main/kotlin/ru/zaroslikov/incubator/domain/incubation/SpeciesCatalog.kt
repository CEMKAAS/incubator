package ru.zaroslikov.incubator.domain.incubation

import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.model.toValue

/**
 * Каталог видов птицы — единственное место, где интерфейс спрашивает про вид.
 *
 * До своих видов всё знание о птице лежало в `when` по имени: срок в
 * [incubationDays], режим по дням в [setIncubator], дни овоскопирования в [setOvoskop].
 * Свой вид описан не кодом, а строками в базе ([CustomSpecies]), и ответить про него
 * функция без данных не может. Каталог собирает оба источника под одним набором
 * вопросов: сперва ищет среди своих видов, не найдя — спрашивает встроенные.
 *
 * Именно поэтому прежние функции стали `internal`: обратись экран к [incubationDays]
 * напрямую — свой вид для него окажется «неизвестным», то есть без срока, всегда
 * готовым к завершению и без единого овоскопирования, и ошибка эта молчаливая.
 * Компилятор её теперь не пропустит.
 *
 * Свой вид **перекрывает** встроенный с тем же именем — на всякий случай, потому что
 * форма своего вида такое имя всё равно не пропускает: одноимённых видов в списке
 * выбора быть не должно.
 *
 * Неизменяемый: ViewModel собирает его заново на каждый ответ базы и кладёт в
 * состояние экрана, как любую другую производную от данных.
 */
class SpeciesCatalog(custom: List<CustomSpecies> = emptyList()) {

    /** Свои виды по имени, как их сохранили; порядок списка — порядок в базе. */
    val custom: List<CustomSpecies> = custom

    private val byName: Map<String, CustomSpecies> = custom.associateBy { it.name }

    /** Имена своих видов — для сетки выбора и списка в настройках. */
    val customNames: List<String> get() = custom.map { it.name }

    /** Все виды, которые можно выбрать: встроенные в порядке макета, за ними свои. */
    val allNames: List<String> get() = BUILT_IN + customNames

    fun isCustom(type: String): Boolean = type in byName

    fun custom(type: String): CustomSpecies? = byName[type]

    /**
     * Занято ли имя — встроенным видом или своим. Без учёта регистра и краёв: «курицы»
     * и «Курицы » в списке выбора выглядели бы двумя плитками одной птицы.
     *
     * [exceptId] исключает сам правящийся вид: своё имя ему не запрещено.
     */
    fun isNameTaken(name: String, exceptId: Long = 0): Boolean {
        val wanted = name.trim()
        if (BUILT_IN.any { it.equals(wanted, ignoreCase = true) }) return true
        return custom.any { it.id != exceptId && it.name.equals(wanted, ignoreCase = true) }
    }

    /** Срок инкубации в днях; `null` — вид неизвестен. */
    fun incubationDays(type: String): Int? =
        byName[type]?.length ?: ru.zaroslikov.incubator.domain.incubation.incubationDays(type)

    /** Срок вышел. Неизвестный вид считается завершённым: срока для него нет. */
    fun isIncubationFinished(type: String, day: Int): Boolean {
        val days = incubationDays(type) ?: return true
        return day >= days
    }

    /**
     * Завершать уже можно — последний или предпоследний день, см. пояснение у
     * [canFinishIncubation]. Неизвестный вид готов всегда.
     */
    fun canFinishIncubation(type: String, day: Int): Boolean {
        val days = incubationDays(type) ?: return true
        return day >= days - 1
    }

    /** Предлагается ли в этот день овоскопирование. */
    fun isCandlingDay(type: String, day: Int): Boolean =
        byName[type]?.let { day in it.candlingDays } ?: setOvoskop(type, day)

    /**
     * Какое это по счёту овоскопирование — 1, 2, 3…; 0, если в этот день его нет.
     *
     * Считается перебором дней, а не хранится, по той же причине, что и у встроенных
     * ([ovoskopStage]): список дней уже есть, а вторая копия разъехалась бы с ним.
     * У своего вида овоскопирований может быть и больше трёх — сколько дней отметили.
     */
    fun candlingStage(type: String, day: Int): Int {
        val own = byName[type] ?: return ovoskopStage(type, day)
        if (day !in own.candlingDays) return 0
        return own.candlingDays.count { it <= day }
    }

    /**
     * Режим по умолчанию — по одной строке на день, как их порождает [setIncubator].
     *
     * Новый список на каждый вызов: вызывающий правит строки на месте
     * (`setAutoIncubator`, `setIdPT`), и общий экземпляр разнёс бы правки одной
     * закладки по всем следующим. Неизвестный вид — пустой список, и форма закладки
     * так и говорит: «режим не описан».
     */
    fun schedule(type: String): MutableList<Value> =
        byName[type]?.days?.sortedBy { it.day }?.map { it.toValue() }?.toMutableList()
            ?: setIncubator(type)

    companion object {
        /** Встроенные виды в порядке макета формы закладки. */
        val BUILT_IN: List<String> = listOf("Курицы", "Утки", "Гуси", "Индюки", "Перепела")

        /** Каталог без своих видов — начальное значение, пока база не ответила. */
        val EMPTY = SpeciesCatalog()
    }
}
