package ru.zaroslikov.incubator.domain.model

/**
 * Владелец хозяйства. Пока это ровно одно имя — приложение offline-only, аккаунтов нет.
 *
 * Пустое имя — обычное состояние, а не ошибка: поле необязательное, и экран профиля
 * в этом случае просто предлагает его заполнить.
 */
data class User(
    val name: String = "",
) {
    /** Заполнено ли имя. Пробелы за содержимое не считаются. */
    val hasName: Boolean get() = name.isNotBlank()
}
