package ru.zaroslikov.incubator.ui.batch

import ru.zaroslikov.incubator.settings.TemperatureUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Перевод температуры и влажности между числом в базе и текстом на экране.
 *
 * С пятой версии схемы `temp` и `damp` — `Double?`, а поля ввода по-прежнему работают
 * со строками, поэтому граница между ними проходит здесь, в одном месте на всё
 * приложение, а не в каждом экране по-своему.
 *
 * Разделитель всегда точка, отсюда и `Locale.US` в форматировании. Это не недосмотр:
 * в макете
 * ([14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893))
 * везде «37.8», и расписание в `:domain` записано так же — форматирование по системной
 * локали подставило бы запятую и рассогласовало бы экран с макетом.
 */

/**
 * Температура — не меньше одного знака после точки и не больше двух:
 * «37.0», «37.5», «37.55».
 *
 * Последний ноль отбрасывается только второй: весь режим инкубации записан с одним
 * знаком, и «37» посреди списка из «37.5» читалось бы как другая величина, а вот
 * «37.50» — лишний знак на пустом месте.
 */
internal fun Double.formatTemp(): String {
    val text = String.format(Locale.US, "%.2f", Math.round(this * 100.0) / 100.0)
    return if (text.endsWith("0")) text.dropLast(1) else text
}

/** Влажность — целая, если целая: «60», а не «60.0»; дробная переживает: «37.5», «55.25». */
internal fun Double.formatDamp(): String {
    val rounded = Math.round(this * 100.0) / 100.0
    if (rounded % 1.0 == 0.0) return rounded.toLong().toString()
    return String.format(Locale.US, "%.2f", rounded).trimEnd('0')
}

/** `null` — прочерк: значение не задано или не записано. */
internal fun Double?.formatTempOrDash(): String = this?.formatTemp() ?: "—"

internal fun Double?.formatDampOrDash(): String = this?.formatDamp() ?: "—"

// --- Градусы экрана ----------------------------------------------------------------------
//
// Температура в базе всегда в Цельсиях (см. [TemperatureUnit]); в Фаренгейты она
// переводится здесь, на границе с экраном, и здесь же возвращается обратно из поля
// ввода. Четыре функции ниже — единственное место, где единицы из «Настроек» касаются
// числа; всё, что считает отклонения и средние, о них не знает.

/** Температура базы в градусах экрана: «37.8» при Цельсии, «100.04» при Фаренгейте. */
internal fun Double.formatTemp(unit: TemperatureUnit): String =
    unit.fromCelsius(this).formatTemp()

internal fun Double?.formatTempOrDash(unit: TemperatureUnit): String =
    this?.formatTemp(unit) ?: "—"

/**
 * Разница температур — отклонение, разброс — в градусах экрана: без сдвига на 32, только
 * длина градуса. Округление до десятых — как у всех отклонений (см. `roundTo1`).
 */
internal fun Double.formatTempDelta(unit: TemperatureUnit): String =
    (Math.round(unit.scale(this) * 10.0) / 10.0).formatTemp()

/** Поле ввода температуры: число базы — в градусах экрана, `null` — пустое поле. */
internal fun Double?.toTempFieldText(unit: TemperatureUnit): String =
    this?.let { unit.fromCelsius(it) }.toFieldText()

/** Набранная температура — обратно в градусы базы; пустое поле и мусор — `null`. */
internal fun String.toCelsiusOrNull(unit: TemperatureUnit): Double? =
    toMeasureOrNull()?.let { unit.toCelsius(it) }

/** Пустая строка и мусор — `null`; запятая на цифровой клавиатуре набирается легко. */
internal fun String.toMeasureOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()

/**
 * Обратный ход для полей ввода: `null` — пустое поле, а не «0».
 *
 * Хвостовой ноль здесь отбрасывается даже у температуры: набранное «38» иначе
 * возвращалось бы в поле как «38.0» после каждой правки.
 */
internal fun Double?.toFieldText(): String = this?.formatDamp() ?: ""

/**
 * Сколько цифр помещается в замер: две до точки и две после — «37.55», «9.5», «60».
 *
 * Две до точки, потому что и температура инкубации (36–39), и влажность в рабочем
 * диапазоне двузначны; две после — точность приличного термометра. Ограничение
 * не косметическое: «333.44» и «33.333» в поле означают опечатку, которую иначе
 * заметят уже в отклонении от плана.
 */
private const val MAX_INTEGER_DIGITS = 2
private const val MAX_FRACTION_DIGITS = 2

/** Счётчик переворотов и проветриваний: тысяча за сутки означает опечатку. */
private const val MAX_COUNT_DIGITS = 3

/**
 * Пропускает во ввод не больше [integerDigits] цифр до разделителя и
 * [MAX_FRACTION_DIGITS] после.
 *
 * Цифр до разделителя по умолчанию две, и единственное, что просит третью, — поле
 * температуры при Фаренгейте ([TemperatureUnit.integerDigits]): 99.5 и 100.4 там
 * стоят в одном режиме через день. Влажность остаётся при двух и в нём.
 *
 * Разделитель сразу становится точкой: запятую с цифровой клавиатуры набирают чаще,
 * а показываем мы везде точку — пусть поле и не расходится с тем, что выйдет наружу.
 * Разделитель первым символом не пропускается: «.5» — не то, что человек имел в виду.
 */
internal fun String.filterMeasureInput(integerDigits: Int = MAX_INTEGER_DIGITS): String {
    val out = StringBuilder(integerDigits + 1 + MAX_FRACTION_DIGITS)
    var separated = false
    var intDigits = 0
    var fracDigits = 0
    for (ch in this) {
        when {
            ch.isDigit() && !separated && intDigits < integerDigits -> {
                out.append(ch)
                intDigits++
            }

            ch.isDigit() && separated && fracDigits < MAX_FRACTION_DIGITS -> {
                out.append(ch)
                fracDigits++
            }

            (ch == '.' || ch == ',') && !separated && out.isNotEmpty() -> {
                out.append('.')
                separated = true
            }
        }
    }
    return out.toString()
}

/**
 * Перевороты и проветривания — счётчики, и правило замера на них не распространяется:
 * дробной части у них не бывает вовсе, только целые числа.
 *
 * На разделителе ввод обрывается, а не теряет его: вставленное «2.5» должно стать «2»,
 * а не «25» — иначе два переворота молча превратились бы в двадцать пять.
 */
internal fun String.filterCountInput(): String =
    takeWhile { it != '.' && it != ',' }
        .filter { it.isDigit() }
        .take(MAX_COUNT_DIGITS)

/**
 * Первая буква заметки — заглавная. Подсказки клавиатуры мало: она не касается ни текста
 * из буфера, ни уже записанной заметки, открытой на правку.
 *
 * Затрагивается ровно первый символ строки: «t в углу 36» дальше по тексту остаётся
 * таким, как его набрали.
 */
internal fun String.capitalizeFirst(): String =
    replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }

// --- Счётчики: перевороты и проветривания ---------------------------------------------------

/**
 * Пустое поле — `null`, а не ноль: ноль значит «не переворачивать», и записать его
 * пользователь может только цифрой.
 */
internal fun String.toCountOrNull(): Int? = trim().toIntOrNull()

/** Обратный ход для полей ввода: `null` — пустое поле. */
internal fun Int?.toCountText(): String = this?.toString() ?: ""

/**
 * План переворотов на плашке дня: «3», «нет», «Авто», «—».
 *
 * `null` в плане значит «нормы нет». Откуда она делась, знает только закладка: если
 * инкубатор переворачивает сам ([auto]), это «Авто», иначе поле просто очистили.
 */
internal fun planTurnLabel(count: Int?, auto: Boolean): String = when {
    count == null -> if (auto) "Авто" else "—"
    count == 0 -> "нет"
    else -> count.toString()
}

/**
 * План проветривания одной строкой: «2×5 мин», «2», «нет», «Авто», «—».
 *
 * Две цифры плашка показывает вместе, потому что порознь не читаются: «2» без минут
 * не говорит, надолго ли открывают инкубатор, а «5 мин» — сколько раз за день.
 * Длительность в нуле — не ошибка: так записан план, где важны только разы.
 */
internal fun planAiringLabel(count: Int?, minutes: Int?, auto: Boolean): String = when {
    count == null -> if (auto) "Авто" else "—"
    count == 0 -> "нет"
    minutes == null || minutes == 0 -> count.toString()
    else -> "$count×$minutes мин"
}

/**
 * То же для плашки шириной в четверть экрана: «2×5», без единиц.
 *
 * Слово «мин» там не помещается — плашка подписана «ПРОВЕТ.», и вторая цифра при ней
 * читается как длительность. Развёрнутый вид живёт в счётчике дня, где место есть.
 */
internal fun planAiringPill(count: Int?, minutes: Int?, auto: Boolean): String = when {
    count == null -> if (auto) "Авто" else "—"
    count == 0 -> "нет"
    minutes == null || minutes == 0 -> count.toString()
    else -> "$count×$minutes"
}

/**
 * Записанное проветривание в журнале замера: «15 мин», «2×5 мин», «2 раза».
 *
 * Форма замера спрашивает одни минуты — одна запись это одно проветривание, — поэтому
 * обычный случай выглядит просто «15 мин», без множителя на единицу. Обе цифры разом
 * встречаются у замеров, пришедших не из формы, и тогда показываются обе. `null` в
 * обеих значит «не проветривали»: такую строку журнал не рисует вовсе.
 */
internal fun factAiringLabel(count: Int?, minutes: Int?): String? = when {
    minutes != null && (count == null || count == 1) -> "$minutes мин"
    count != null && minutes != null -> "$count×$minutes мин"
    count != null -> "$count ${timesWord(count)}"
    else -> null
}

/** «раз» / «раза» — 1 и 21 раз, 2–4 раза, 5–20 раз. */
private fun timesWord(count: Int): String {
    val tail = count % 100
    if (tail in 11..14) return "раз"
    return if (tail % 10 in 2..4) "раза" else "раз"
}

// --- Ячейка проветривания в таблице расписания ----------------------------------------------

/**
 * Разделитель в ячейке «2×15» — тот же знак, что на плашках дня и в счётчиках, чтобы
 * одна и та же величина не выглядела в таблице иначе, чем везде.
 *
 * С клавиатуры его не набирают: цифровая клавиатура его не показывает, и подставляет
 * его [filterAiringInput] сама, как только появляется вторая цифра.
 */
internal const val AIRING_SEPARATOR = '×'

/** Сколько цифр берёт каждая половина ячейки: разов не больше девяти, минут — двузначно. */
private const val AIRING_COUNT_DIGITS = 1
private const val AIRING_MINUTE_DIGITS = 2

/**
 * Маска ячейки проветривания: первая цифра — сколько раз, дальше «×» и до двух цифр
 * минут. «215» → «2×15», «2» → «2», «2×1» → «2×1».
 *
 * Разделитель не появляется, пока не набрана вторая цифра: иначе «2» тут же становилось
 * бы «2×», а стереть этот хвост было бы нечем — `Backspace` снял бы его, и фильтр
 * вернул бы обратно.
 *
 * Всё, кроме цифр, отбрасывается, поэтому вставленное «2 раза по 5 минут» превращается
 * в «2×5» — первые три цифры и есть то, что ячейка спрашивает.
 */
internal fun String.filterAiringInput(): String {
    val digits = filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val count = digits.take(AIRING_COUNT_DIGITS)
    val minutes = digits.drop(AIRING_COUNT_DIGITS).take(AIRING_MINUTE_DIGITS)
    return if (minutes.isEmpty()) count else "$count$AIRING_SEPARATOR$minutes"
}

/**
 * Ячейка обратно в две величины строки расписания: «2×15» → «2» и «15».
 *
 * Половины возвращаются строками, потому что такими их и держит форма ([ValueUiState]):
 * пустая значит «не задано», и до самого сохранения ноль от пустоты не отличается ничем,
 * кроме набранной цифры.
 */
internal fun parseAiringCell(text: String): Pair<String, String> {
    val digits = text.filter { it.isDigit() }
    return digits.take(AIRING_COUNT_DIGITS) to
        digits.drop(AIRING_COUNT_DIGITS).take(AIRING_MINUTE_DIGITS)
}

/**
 * Две величины строки расписания в текст ячейки.
 *
 * «Не проветривать» показывается одним нулём, а не «0×0»: в режимах без проветривания
 * таких дней полсотни подряд, и длительность при нулевых разах всё равно ничего не
 * значит. Свернуть в «0» любые нулевые разы нельзя — набранное «0» тогда съедало бы
 * следующую цифру, и минуты в такой клетке стало бы не ввести.
 */
internal fun airingCellText(count: String, minutes: String): String = when {
    count.isBlank() -> minutes
    minutes.isBlank() -> count
    count == "0" && minutes == "0" -> "0"
    else -> "$count$AIRING_SEPARATOR$minutes"
}

// --- Отклонение от плана -------------------------------------------------------------------

/**
 * Насколько замер разошёлся с планом — или план новой закладки с планом соседей.
 *
 * Здесь, а не в `BatchDetailSheet.kt`, где им пользуются плитки замера: файл без Compose,
 * и на нём стоит `ScheduleOverlap.kt`, который гоняется JVM-тестом. Публичный, а не
 * `internal`, потому что стоит в `CellVerdicts`, а тот — в `AddBatchState`, публичном
 * состоянии формы закладки.
 */
enum class Severity { Normal, Minor, Major }

/**
 * Пороги — не из макета: он рисует единственный случай, «заметное отклонение». Для
 * температуры десятая доля градуса в инкубации не значит ничего, полградуса — уже много;
 * для влажности так же, но в процентах. Числа стоят у вызывающих: ±0.2 / ±0.5 у
 * температуры, ±3 / ±7 у влажности — одни и те же в шторке закладки и в таблице формы.
 */
internal fun severityOf(delta: Double, minor: Double, major: Double): Severity = when {
    abs(delta) <= minor -> Severity.Normal
    abs(delta) <= major -> Severity.Minor
    else -> Severity.Major
}

/** Отклонение факта от плана; нет одного из двух — нет и отклонения. */
internal fun deltaOf(actual: Double?, target: Double?): Double? {
    if (actual == null || target == null) return null
    return actual - target
}
