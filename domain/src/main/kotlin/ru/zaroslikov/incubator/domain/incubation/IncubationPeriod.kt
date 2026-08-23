package ru.zaroslikov.incubator.domain.incubation

/**
 * Длительность инкубации в днях для вида птицы, null — вид неизвестен.
 */
fun incubationDays(typeBird: String): Int? = when (typeBird) {
    "Курицы" -> 21
    "Индюки" -> 28
    "Гуси" -> 30
    "Утки" -> 28
    "Перепела" -> 17
    else -> null
}

/**
 * Завершилась ли инкубация для данного вида птицы на указанный день.
 *
 * Раньше эта проверка жила в IncubatorScreen.kt и сама выставляла состояние диалога.
 * Показ диалога — дело экрана, поэтому здесь остался только расчёт.
 * Неизвестный вид считается завершённым: срока для него нет.
 */
fun isIncubationFinished(typeBird: String, day: Int): Boolean {
    val days = incubationDays(typeBird) ?: return true
    return day >= days
}
