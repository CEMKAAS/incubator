package ru.zaroslikov.incubator.domain.incubation

/** Дни, когда для данного вида птицы предлагается овоскопирование. */
internal fun setOvoskop(typeBird: String, day: Int): Boolean {
    return when (typeBird) {
        "Курицы" -> {
            when (day) {
                7 -> true
                11 -> true
                16 -> true
                else -> false
            }
        }

        "Индюки", "Утки" -> {
            when (day) {
                8 -> true
                14 -> true
                25 -> true
                else -> false
            }
        }

        "Гуси" -> {
            when (day) {
                9 -> true
                15 -> true
                21 -> true
                else -> false
            }
        }

        "Перепела" -> {
            when (day) {
                6 -> true
                13 -> true
                else -> false
            }
        }

        else -> false
    }
}

/**
 * Какое это по счёту овоскопирование — 1, 2 или 3; 0, если в этот день его нет.
 *
 * Считается перебором дней через [setOvoskop], а не второй таблицей: список дней и так
 * задан там, и при добавлении новой птицы две копии неизбежно разъехались бы. У перепелов
 * овоскопирований два, поэтому «третьего» для них не бывает.
 */
internal fun ovoskopStage(typeBird: String, day: Int): Int =
    if (!setOvoskop(typeBird, day)) 0 else (1..day).count { setOvoskop(typeBird, it) }
