package ru.zaroslikov.incubator.settings

import kotlin.math.roundToLong

/**
 * Единицы измерения, которые выбирают в «Настройках»: градусы и валюта.
 *
 * Обе — про показ, а не про хранение. **В базе температура всегда в градусах Цельсия**:
 * режимы в `:domain` записаны в них, файлы расписания уезжают в них, и закладка,
 * заведённая при одних единицах, должна читаться при других без пересчёта записей.
 * Перевод происходит на границе с экраном — в [ru.zaroslikov.incubator.ui.batch.ValueFormat]
 * при показе и при разборе поля ввода, — и нигде больше: арифметика отклонений, порогов и
 * средних остаётся в Цельсиях, а в Фаренгейты переводится уже готовое число.
 *
 * Валюта — только знак. Суммы хранятся числом без валюты, и смена рубля на тенге ничего
 * не пересчитывает: сколько записали, столько и осталось, сменилась подпись. Курс здесь
 * не нужен и был бы враньём — приложение не знает, в какой валюте сумму вводили.
 */
enum class TemperatureUnit(
    /** Знак после числа: «°C», «°F». */
    val symbol: String,
    /** Подпись над полем ввода: «T°C», «T°F». */
    val fieldLabel: String,
    /** Как единицы названы в «Настройках». */
    val title: String,
    /**
     * Сколько цифр до точки помещается в поле температуры. Цельсий двузначен (36–39),
     * Фаренгейт — нет: 99.5 и 100.4 стоят по соседству в одном режиме.
     */
    val integerDigits: Int,
) {
    CELSIUS("°C", "T°C", "Цельсий", 2),
    FAHRENHEIT("°F", "T°F", "Фаренгейт", 3);

    /**
     * Градусы базы — в градусы экрана.
     *
     * Округление до сотых — потому что столько и хранится (см. `filterMeasureInput`):
     * 37.8 °C — это ровно 100.04 °F, и обратно [toCelsius] возвращает ровно 37.8. Один
     * знак в Фаренгейтах терял бы это: 100.0 °F обратно — уже 37.78.
     */
    fun fromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> round2(celsius * 9.0 / 5.0 + 32.0)
    }

    /** Градусы экрана — в градусы базы, до сотых. */
    fun toCelsius(value: Double): Double = when (this) {
        CELSIUS -> value
        FAHRENHEIT -> round2((value - 32.0) * 5.0 / 9.0)
    }

    /**
     * Разница температур — отклонение, разброс — переводится без сдвига на 32: она
     * одна и та же по обе стороны шкалы, а отличается только длина градуса.
     */
    fun scale(deltaCelsius: Double): Double = when (this) {
        CELSIUS -> deltaCelsius
        FAHRENHEIT -> deltaCelsius * 9.0 / 5.0
    }

    companion object {
        /** Разбор сохранённого имени; незнакомое или отсутствующее — [CELSIUS]. */
        fun fromName(name: String?): TemperatureUnit =
            entries.firstOrNull { it.name == name } ?: CELSIUS

        private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0
    }
}

/**
 * Валюта сумм — знак, который ставится после числа: «11 250 ₽», «11 250 $».
 *
 * Порядок — порядок плиток в «Настройках»: рубль первым, потому что до появления выбора
 * приложение знало только его и он остаётся значением по умолчанию.
 */
enum class Currency(
    /** Знак после суммы. У белорусского рубля своего знака нет — принят «Br». */
    val symbol: String,
    /** Название в «Настройках». */
    val title: String,
) {
    RUB("₽", "Рубль"),
    USD("$", "Доллар"),
    EUR("€", "Евро"),
    KZT("₸", "Тенге"),
    BYN("Br", "Белорусский рубль"),
    CNY("¥", "Юань");

    companion object {
        /** Разбор сохранённого имени; незнакомое или отсутствующее — [RUB]. */
        fun fromName(name: String?): Currency =
            entries.firstOrNull { it.name == name } ?: RUB
    }
}

/** Обе настройки вместе — то, что экраны читают через `LocalUnits`. */
data class Units(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val currency: Currency = Currency.RUB,
) {
    companion object {
        val DEFAULT = Units()
    }
}
