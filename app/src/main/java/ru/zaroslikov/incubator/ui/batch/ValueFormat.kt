package ru.zaroslikov.incubator.ui.batch

import java.util.Locale

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
 * Пропускает во ввод не больше [MAX_INTEGER_DIGITS] цифр до разделителя и
 * [MAX_FRACTION_DIGITS] после.
 *
 * Разделитель сразу становится точкой: запятую с цифровой клавиатуры набирают чаще,
 * а показываем мы везде точку — пусть поле и не расходится с тем, что выйдет наружу.
 * Разделитель первым символом не пропускается: «.5» — не то, что человек имел в виду.
 */
internal fun String.filterMeasureInput(): String {
    val out = StringBuilder(MAX_INTEGER_DIGITS + 1 + MAX_FRACTION_DIGITS)
    var separated = false
    var intDigits = 0
    var fracDigits = 0
    for (ch in this) {
        when {
            ch.isDigit() && !separated && intDigits < MAX_INTEGER_DIGITS -> {
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
