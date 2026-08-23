package ru.zaroslikov.incubator.domain.incubation

/** Дни, когда для данного вида птицы предлагается овоскопирование. */
fun setOvoskop(typeBird: String, day: Int): Boolean {
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
