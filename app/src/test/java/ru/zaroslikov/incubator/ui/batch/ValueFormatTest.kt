package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.zaroslikov.incubator.settings.TemperatureUnit

/**
 * Правила ввода и показа температуры и влажности.
 *
 * Тест держится в `:app`, а не в `:domain`, потому что там же живёт и сам
 * `ValueFormat.kt`; андроидного в нём ничего нет, и `gradlew :app:test` гоняет его
 * на JVM без эмулятора.
 */
class ValueFormatTest {

    @Test
    fun `в замер помещаются две цифры до точки и две после`() {
        assertEquals("37.55", "37.55".filterMeasureInput())
        assertEquals("37.5", "37.5".filterMeasureInput())
        assertEquals("9.5", "9.5".filterMeasureInput())
        assertEquals("60", "60".filterMeasureInput())
    }

    @Test
    fun `лишние цифры отсекаются`() {
        // Ровно то, что просили запретить.
        assertEquals("33.44", "333.44".filterMeasureInput())
        assertEquals("33.33", "33.333".filterMeasureInput())
        // Целая часть двузначна, поэтому «100» целиком не проходит — см. KDoc правила.
        assertEquals("10", "100".filterMeasureInput())
    }

    @Test
    fun `разделитель один и всегда точка`() {
        assertEquals("37.5", "37,5".filterMeasureInput())
        assertEquals("37.5", "37..5".filterMeasureInput())
        assertEquals("37.5", "37,.5".filterMeasureInput())
    }

    @Test
    fun `с разделителя ввод не начинается`() {
        assertEquals("5", ".5".filterMeasureInput())
        assertEquals("", ".".filterMeasureInput())
    }

    @Test
    fun `буквы и знаки во ввод не проходят`() {
        assertEquals("37.5", "t37.5°C".filterMeasureInput())
        assertEquals("", "нет".filterMeasureInput())
        assertEquals("38", "-38".filterMeasureInput())
    }

    @Test
    fun `на счётчики правило замера не распространяется`() {
        assertEquals("2", "2.5".filterCountInput())
        assertEquals("37", "37,55".filterCountInput())
        assertEquals("38", "-38".filterCountInput())
        assertEquals("12", "12".filterCountInput())
        assertEquals("999", "9999".filterCountInput())
    }

    @Test
    fun `температура показывается с одним знаком, а при нужде с двумя`() {
        assertEquals("37.0", 37.0.formatTemp())
        assertEquals("37.5", 37.5.formatTemp())
        assertEquals("37.55", 37.55.formatTemp())
        assertEquals("37.05", 37.05.formatTemp())
        assertEquals("—", (null as Double?).formatTempOrDash())
    }

    @Test
    fun `в фаренгейтах показ и поле ввода переводятся, а разница — только масштабируется`() {
        val f = TemperatureUnit.FAHRENHEIT
        val c = TemperatureUnit.CELSIUS
        assertEquals("37.8", 37.8.formatTemp(c))
        assertEquals("100.04", 37.8.formatTemp(f))
        assertEquals("99.5", 37.5.formatTemp(f))
        assertEquals("—", (null as Double?).formatTempOrDash(f))
        // Отклонение: 0.5 °C — это 0.9 °F, а не 32.9.
        assertEquals("0.9", 0.5.formatTempDelta(f))
        assertEquals("0.5", 0.5.formatTempDelta(c))
        assertEquals("-2.7", (-1.5).formatTempDelta(f))
        // Поле ввода: в базе 37.8, в поле «100.04», набранное «99.5» ложится в базу как 37.5.
        assertEquals("100.04", (37.8 as Double?).toTempFieldText(f))
        assertEquals("37.8", (37.8 as Double?).toTempFieldText(c))
        assertEquals("", (null as Double?).toTempFieldText(f))
        assertEquals(37.5, "99.5".toCelsiusOrNull(f))
        assertEquals(37.5, "37,5".toCelsiusOrNull(c))
        assertNull("".toCelsiusOrNull(f))
    }

    @Test
    fun `третья цифра до точки пропускается только по просьбе поля`() {
        assertEquals("100.4", "100.4".filterMeasureInput(TemperatureUnit.FAHRENHEIT.integerDigits))
        assertEquals("10.4", "100.4".filterMeasureInput(TemperatureUnit.CELSIUS.integerDigits))
        assertEquals("100.44", "1000.444".filterMeasureInput(3))
    }

    @Test
    fun `влажность целая, если целая`() {
        assertEquals("60", 60.0.formatDamp())
        assertEquals("37.5", 37.5.formatDamp())
        assertEquals("55.25", 55.25.formatDamp())
        assertEquals("—", (null as Double?).formatDampOrDash())
    }

    @Test
    fun `в поле ввода хвостового нуля нет`() {
        assertEquals("38", 38.0.toFieldText())
        assertEquals("37.5", 37.5.toFieldText())
        assertEquals("37.55", 37.55.toFieldText())
        assertEquals("", (null as Double?).toFieldText())
    }

    @Test
    fun `разбор принимает запятую, а пустое и мусор дают null`() {
        assertEquals(37.55, "37,55".toMeasureOrNull()!!, 0.0001)
        assertEquals(37.5, "37.5".toMeasureOrNull()!!, 0.0001)
        assertEquals(37.0, "37.".toMeasureOrNull()!!, 0.0001)
        assertNull("".toMeasureOrNull())
        assertNull("нет".toMeasureOrNull())
    }

    @Test
    fun `у счётчика пустое поле — null, а ноль — значение`() {
        assertEquals(3, "3".toCountOrNull())
        assertEquals(0, "0".toCountOrNull())
        assertNull("".toCountOrNull())
        assertNull("Авто".toCountOrNull())
        assertEquals("3", 3.toCountText())
        assertEquals("0", 0.toCountText())
        assertEquals("", (null as Int?).toCountText())
    }

    @Test
    fun `в плане переворотов ноль — «нет», а пусто — «Авто» только на автоматике`() {
        assertEquals("3", planTurnLabel(3, auto = false))
        assertEquals("нет", planTurnLabel(0, auto = false))
        assertEquals("Авто", planTurnLabel(null, auto = true))
        assertEquals("—", planTurnLabel(null, auto = false))
    }

    @Test
    fun `план проветривания сводит разы и минуты в одну подпись`() {
        assertEquals("2×5 мин", planAiringLabel(2, 5, auto = false))
        assertEquals("2×5", planAiringPill(2, 5, auto = false))
        // Длительности нет — остаются одни разы.
        assertEquals("2", planAiringLabel(2, 0, auto = false))
        assertEquals("нет", planAiringLabel(0, 0, auto = false))
        assertEquals("Авто", planAiringLabel(null, null, auto = true))
        assertEquals("—", planAiringLabel(null, null, auto = false))
    }

    @Test
    fun `записанное проветривание показывает ровно то, что записали`() {
        // Обычный замер: одна запись — одно проветривание, множитель на единицу лишний.
        assertEquals("15 мин", factAiringLabel(1, 15))
        assertEquals("15 мин", factAiringLabel(null, 15))
        assertEquals("2×5 мин", factAiringLabel(2, 5))
        assertEquals("1 раз", factAiringLabel(1, null))
        assertEquals("2 раза", factAiringLabel(2, null))
        assertEquals("5 раз", factAiringLabel(5, null))
        assertEquals("11 раз", factAiringLabel(11, null))
        assertNull(factAiringLabel(null, null))
    }

    @Test
    fun `записанные минуты проветривания — это одно проветривание`() {
        assertEquals(1, MeasurementForm(airingTime = "15").airingCountValue)
        assertNull(MeasurementForm(airingTime = "").airingCountValue)
        // Заполнить только заметку — не значит проветрить.
        assertNull(MeasurementForm(note = "Долил воды").airingCountValue)
    }

    @Test
    fun `ячейка проветривания — одна цифра разов, потом разделитель и две цифры минут`() {
        assertEquals("2×15", "215".filterAiringInput())
        assertEquals("2", "2".filterAiringInput())
        assertEquals("2×1", "2×1".filterAiringInput())
        // Третья цифра минут уже не влезает, четвёртая тем более.
        assertEquals("2×15", "2×153".filterAiringInput())
        // Из буфера вставили фразу — берутся её первые три цифры.
        assertEquals("2×5", "2 раза по 5 минут".filterAiringInput())
        assertEquals("", "".filterAiringInput())
        assertEquals("", "нет".filterAiringInput())
    }

    @Test
    fun `набор и стирание в ячейке проветривания идут шаг за шагом`() {
        // Как это выглядит в поле: цифру набрали — разделитель появился сам,
        // стёрли — исчез, и ничего не встаёт обратно.
        fun cell(text: String): String {
            val (count, minutes) = parseAiringCell(text.filterAiringInput())
            return airingCellText(count, minutes)
        }
        assertEquals("2", cell("2"))
        assertEquals("2×1", cell("21"))
        assertEquals("2×15", cell("2×15"))
        assertEquals("2×1", cell("2×1"))
        assertEquals("2", cell("2×"))
        assertEquals("", cell(""))
    }

    @Test
    fun `ноль проветриваний в ячейке — один ноль, но минуты после него набираются`() {
        assertEquals("0", airingCellText("0", "0"))
        assertEquals("0", airingCellText("0", ""))
        // Иначе «0» съедало бы следующую цифру и минуты стало бы не ввести.
        assertEquals("0×5", airingCellText("0", "5"))
        assertEquals("", airingCellText("", ""))
    }

    @Test
    fun `заметка начинается с заглавной буквы`() {
        assertEquals("Долил воды", "долил воды".capitalizeFirst())
        assertEquals("Долил воды", "Долил воды".capitalizeFirst())
        // Меняется ровно первый символ, поэтому «pH» в начале строки его и получает.
        assertEquals("PH в норме", "pH в норме".capitalizeFirst())
        assertEquals("", "".capitalizeFirst())
    }
}
