package ru.zaroslikov.incubator.domain.model

/**
 * Закладка — партия яиц, заложенная в инкубатор. Раньше эта сущность называлась
 * IncubatorTable и хранилась в таблице `Incubator`, что путало её с самим устройством.
 *
 * [airing] и [over] — что фактически использовали при генерации расписания этой закладки;
 * у самого устройства ([Incubator.autoAiring], [Incubator.autoTurn]) это возможность.
 * Значения уже запечены в строки [Value], переписывать их задним числом нельзя.
 *
 * [price] и [pricePerEgg] хранят ровно то, что ввёл пользователь: цену за яйцо либо
 * за всю партию. Пересчитывать одно в другое при сохранении нельзя — количество яиц
 * ещё изменится, и намерение «22 ₽ за яйцо» тогда потеряется.
 */
data class Batch(
    val id: Long = 0,
    val title: String, // название
    val type: String, // ведущий вид птицы, задаёт режим инкубации
    val data: String,  // Дата начала закладки
    val eggAll: Int, // заложено яиц
    val eggAllEND: Int, // выведено
    val airing: String, //не авто = 0, авто = 1
    val over: String,  //не авто = 0, авто = 1
    var arhive: String, //не архив =0, Архив = 1
    val dateEnd: String,
    val note: String,
    val incubatorId: Long = 0, // инкубатор, которому принадлежит закладка
    val breed: String = "", // порода внутри вида: «Ломан Браун», «Пекинская»
    val price: Int = 0, // введённая стоимость в рублях, 0 — не указана
    val pricePerEgg: Boolean = true, // true — [price] за яйцо, false — за всю партию
)
