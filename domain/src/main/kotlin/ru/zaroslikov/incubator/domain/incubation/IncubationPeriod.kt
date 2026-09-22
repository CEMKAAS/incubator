package ru.zaroslikov.incubator.domain.incubation

/**
 * Длительность инкубации в днях для вида птицы, null — вид неизвестен.
 */
internal fun incubationDays(typeBird: String): Int? = when (typeBird) {
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
internal fun isIncubationFinished(typeBird: String, day: Int): Boolean {
    val days = incubationDays(typeBird) ?: return true
    return day >= days
}

/**
 * Пора ли завершать инкубацию: идёт последний или предпоследний её день.
 *
 * Отдельно от [isIncubationFinished], потому что вопросы разные. Та отвечает «срок
 * вышел» и решает, предлагать ли итог. Эта отвечает «завершать уже можно» и красит
 * кнопку в шторке закладки: птенцы наклёвываются не строго в срок, и запретить
 * завершение за день до конца значило бы спорить с тем, что человек видит в лотке.
 *
 * [day] — день инкубации, считая с первого, как его показывает шторка закладки.
 * Неизвестный вид считается готовым: срока для него нет, а значит и «рано» не бывает.
 */
internal fun canFinishIncubation(typeBird: String, day: Int): Boolean {
    val days = incubationDays(typeBird) ?: return true
    return day >= days - 1
}
