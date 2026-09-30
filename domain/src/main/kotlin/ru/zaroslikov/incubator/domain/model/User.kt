package ru.zaroslikov.incubator.domain.model

/**
 * Владелец хозяйства — профиль, одна строка на всю базу.
 *
 * Профиль необязателен, и пустой [User] — обычное состояние, а не ошибка: приложением
 * пользуются и без него, целиком. Заводится он двумя путями — входом через VK ID или
 * заполнением вручную, — и оба кончаются одной и той же строкой: VK лишь подставляет
 * имя и фото и оставляет свой [vkUserId].
 *
 * Сервера у приложения нет, поэтому профиль живёт в базе рядом с хозяйством и едет вместе
 * с ней — в резервной копии и в файле экспорта. [vkUserId] хранится как задел: будущей
 * синхронизации нужен будет устойчивый идентификатор человека, и имя им быть не может.
 * Токен VK сюда не попадает никогда — его держит сам SDK в своём хранилище, и в копию
 * базы, которую человек отправляет в мессенджер, ему дороги нет.
 *
 * [avatar] — JPEG не больше 256 точек по стороне. Байтами в базе, а не файлом рядом с
 * ней, по той же причине, по которой профиль — строка в базе: фото, оставшееся на старом
 * телефоне, пока имя переехало на новый, — это половина профиля.
 */
data class User(
    val name: String = "",
    val farm: String = "",
    val city: String = "",
    val avatar: ByteArray? = null,
    val vkUserId: Long? = null,
) {
    /** Заполнено ли имя. Пробелы за содержимое не считаются. */
    val hasName: Boolean get() = name.isNotBlank()

    /** Привязан ли VK ID. */
    val isVkLinked: Boolean get() = vkUserId != null

    /**
     * Заведён ли профиль — то есть прошёл ли человек «регистрацию».
     *
     * Имени достаточно: ручная регистрация без имени не сохраняется, а у входа через VK
     * имя есть всегда. Привязка без имени — человек стёр имя после входа — профилем
     * остаётся: связь с VK он не отменял.
     */
    val hasProfile: Boolean get() = hasName || isVkLinked

    // ByteArray сравнивается по ссылке, и без этих двух методов два одинаковых профиля,
    // прочитанные из базы дважды, были бы разными — поток состояния перерисовывал бы
    // экран на каждый ответ Room.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return name == other.name &&
            farm == other.farm &&
            city == other.city &&
            vkUserId == other.vkUserId &&
            avatar.contentEquals(other.avatar)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + farm.hashCode()
        result = 31 * result + city.hashCode()
        result = 31 * result + (vkUserId?.hashCode() ?: 0)
        result = 31 * result + (avatar?.contentHashCode() ?: 0)
        return result
    }
}
