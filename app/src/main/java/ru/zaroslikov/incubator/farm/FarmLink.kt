package ru.zaroslikov.incubator.farm

import java.net.URLEncoder
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.stats.HatchSummary

/**
 * Птенцы одной закладки, которых предлагают завести в «Моём хозяйстве» новой группой
 * животных: вид, название группы, порода, сколько голов и с какого числа.
 *
 * [type] — вид закладки как есть («Курицы», «Утки» или свой вид пользователя): список
 * видов у двух приложений разный, и сопоставлять его — дело принимающей стороны, которая
 * знает свой. [date] — «дд.ММ.гггг», тот же формат, что у обоих приложений в базе.
 */
data class FarmChicks(
    val type: String,
    val name: String,
    val breed: String,
    val count: Int,
    val date: String,
)

/**
 * Что уходит в «Моё хозяйство»: ссылка `myferma://animal/add?…`.
 *
 * **Это договор между двумя приложениями, и принимающая половина — не здесь.** Инкубатор
 * только открывает ссылку; «Моё хозяйство» (`com.zaroslikov.fermacompose2`) должно
 * объявить на неё `intent-filter` (схема `myferma`, хост `animal`, путь `/add`), спросить,
 * в какую ферму, и открыть заполненную форму новой группы — сохраняет человек, а не
 * ссылка. Та же схема `myferma` у него уже занята QR-шаблонами (`myferma://template`),
 * хост `animal` — новый.
 *
 * Схема, а не явный `Intent` с extras в конкретную активность: ссылку можно открыть
 * руками из `adb` и проверить до того, как вторая половина готова, имя активности
 * хозяйства не становится частью договора, а версия, которая ссылку не понимает, просто
 * не находится — тогда вместо «добавить» предлагаем обновить хозяйство (`FarmApp.status`). Пакет у интента всё
 * равно задан явно: кому попало эти числа не нужны.
 *
 * Параметры (все — строки, URL-кодированные, UTF-8):
 * - `v` — версия договора, сейчас [VERSION]; поднимается только со сменой смысла поля;
 * - `source` — `incubator`, откуда пришли птенцы;
 * - `type`, `name`, `breed`, `count`, `date` — поля [FarmChicks]; `breed` может быть
 *   пустым, `count` — целое больше нуля.
 *
 * Сборка — чистая функция над строками, без `android.net.Uri` (в JVM-тестах это
 * заглушка), чтобы формат пришпилил `FarmLinkTest`. Пробел кодируется `%20`, а не `+`:
 * `+` в запросе понимают как пробел не все разборщики.
 */
object FarmLink {
    const val PACKAGE = "com.zaroslikov.fermacompose2"
    const val SCHEME = "myferma"
    const val HOST = "animal"
    const val PATH = "/add"
    const val VERSION = 1
    const val SOURCE = "incubator"

    fun encode(chicks: FarmChicks): String {
        val params = listOf(
            "v" to VERSION.toString(),
            "source" to SOURCE,
            "type" to chicks.type,
            "name" to chicks.name,
            "breed" to chicks.breed,
            "count" to chicks.count.toString(),
            "date" to chicks.date,
        )
        return "$SCHEME://$HOST$PATH?" + params.joinToString("&") { (key, value) -> "$key=${encodeParam(value)}" }
    }

    private fun encodeParam(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * Что из сводки поздравления уходит в хозяйство.
 *
 * Название группы — название закладки, а у безымянной — вид: у партии пород оно уже несёт
 * породу хвостом («Весенняя партия — Хайсекс»), так что две группы не выйдут одноимёнными.
 * Дата — дата вывода из закладки, а если её нет — [today]: сводка приходит сразу после
 * завершения, и «сегодня» тогда верно. `null` — птенцов нет, и передавать нечего.
 */
fun farmChicksOf(summary: HatchSummary, today: String): FarmChicks? {
    if (summary.hatched <= 0) return null
    return FarmChicks(
        type = summary.species,
        name = summary.title.ifBlank { summary.species },
        breed = summary.breed,
        count = summary.hatched,
        date = summary.dateEnd.ifBlank { today },
    )
}

/**
 * То же для закладки, завершённой раньше, — из меню её карточки: птенцов можно завести в
 * хозяйстве и потом, а не только в минуту поздравления (его закрыли, хозяйство поставили
 * позже, выбор в нём смахнули). Предлагается ровно там, где было бы поздравление:
 * закладка доведена до срока ([BatchStatus.Hatched]) и птенцы есть. У прерванной их нет
 * по построению — `stoppedEarly` пишет в «выведено» ноль.
 *
 * Поля те же, что у [farmChicksOf] над сводкой, и собраны так же: название, а у
 * безымянной — вид; дата вывода, а без неё — [today].
 */
fun farmChicksOf(batch: Batch, today: String): FarmChicks? {
    if (batch.status != BatchStatus.Hatched || batch.eggAllEND <= 0) return null
    return FarmChicks(
        type = batch.type,
        name = batch.title.ifBlank { batch.type },
        breed = batch.breed,
        count = batch.eggAllEND,
        date = batch.dateEnd.ifBlank { today },
    )
}
