package ru.zaroslikov.incubator.transfer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.CustomSpeciesDay
import ru.zaroslikov.incubator.domain.model.Value

/**
 * Файл расписания: режим по дням одной удачно завершённой закладки, отданный как
 * образец — себе про запас или другому человеку с той же птицей.
 *
 * В файл идёт **только расписание** — таблица «температура, влажность, поворот,
 * проветривание» на каждый день, в двух видах: план закладки и среднее по её замерам
 * ([fact]) — и вид птицы, которому она принадлежит. Ни названия закладки, ни яиц, ни
 * напоминаний, ни самих замеров: всё это про ту закладку, что была, а файл нужен той,
 * которую заложат сейчас. Вид без расписания не имеет смысла, а
 * расписание без вида — тем более: режим утки, подставленный курице, губит партию, и
 * получатель обязан знать, чьи это дни (о проверке — в `AddBatchViewModel.importFile`).
 *
 * Это не перенос данных: копия базы (`DatabaseTransfer` в `:data`) переезжает целиком и
 * заменяет всё, а файл расписания ничего не заменяет — форма новой закладки берёт из
 * него таблицу, как берёт её из архива. Отсюда и формат: не файл SQLite, привязанный к
 * версии схемы, а JSON с явными полями, который переживёт любую миграцию.
 *
 * [customSpecies] — описание своего вида, если закладка велась по нему: без него на чужом
 * телефоне «Цесарки» — пустое имя без срока и овоскопирований. У встроенного вида здесь
 * `null`, его знает любой экземпляр приложения.
 */
data class ScheduleExport(
    /** Вид птицы — `Batch.type` закладки-источника. */
    val type: String,
    /** Режим по дням, по возрастанию дня, с нулевыми идентификаторами. */
    val plan: List<Value>,
    /**
     * Среднее по замерам закладки-источника, уже разложенное по дням, — то, каким режим
     * вышел на самом деле; `null`, когда замеров не было. Считается при экспорте
     * (`averagedScheduleOf`), а не при импорте: сами замеры в файл не идут — их могут
     * быть сотни, а нужны из них только средние по дням. Дни и столбцы без замеров в нём
     * уже заполнены из [plan], так что таблица из него приходит целой.
     */
    val fact: List<Value>?,
    /**
     * Сколько замеров стояло за [fact]. Три замера за три недели усредняются во что
     * угодно, и тому, кто выбирает «среднее по замерам», это число нужно видеть.
     */
    val measurementCount: Int,
    val customSpecies: CustomSpecies?,
    /**
     * Порода закладки-источника («Ломан Браун, Хайсекс») и бренд с моделью инкубатора,
     * в котором она шла, — пустые, если не указаны. **Только на показ**: режим написан
     * для этой породы и выверен на этом устройстве, и получателю это нужно знать, чтобы
     * решить, брать ли его; но в форму ни то ни другое не переносится — порода новой
     * закладки и её инкубатор уже свои.
     */
    val breed: String = "",
    val incubatorBrand: String = "",
    val incubatorModel: String = "",
    val appVersion: String,
    val exportedAt: String,
) {
    /** «Бренд Модель» одной строкой; пусто, если не указано ни то ни другое. */
    val incubatorLabel: String
        get() = listOf(incubatorBrand, incubatorModel).filter { it.isNotBlank() }.joinToString(" ")

    /** Срок инкубации, каким его задаёт это расписание. */
    val days: Int get() = plan.size

    /**
     * Свой вид, который надо завести, если в каталоге получателя такого нет.
     *
     * Из описания в файле, а его нет — из плана: у файла, сделанного по встроенному
     * виду, которого у получателя вдруг не оказалось (другая сборка), режим по дням всё
     * равно есть, и вид из него собирается — без дней овоскопирования, потому что их
     * взять неоткуда. Имя — всегда [type]: закладка ссылается на вид по нему, и вид
     * под другим именем ей не помог бы.
     */
    fun speciesToCreate(): CustomSpecies {
        val described = customSpecies
        val days = if (described != null && described.days.isNotEmpty()) {
            described.days.map { it.copy(id = 0, speciesId = 0) }
        } else {
            plan.map {
                CustomSpeciesDay(
                    day = it.day,
                    temp = it.temp,
                    damp = it.damp,
                    over = it.over,
                    airingCount = it.airingCount,
                    airingTime = it.airingTime,
                    candling = false,
                )
            }
        }
        return CustomSpecies(id = 0, name = type, days = days.sortedBy { it.day })
    }
}

/** Файл не прочитан; [message] — готовый текст для человека. */
class ScheduleFileException(message: String) : Exception(message)

/**
 * Файл расписания в байты и обратно: JSON внутри, шифр снаружи ([ScheduleFileCipher]).
 *
 * Поля перечислены руками, а не сняты с моделей отражением: формат файла — обещание
 * чужим телефонам и старым версиям, и переименованное поле модели не должно его
 * нарушать. [FORMAT] поднимается, когда меняется смысл записанного; новое поле с
 * разумным умолчанием версии не требует, его просто не найдут в старом файле.
 */
object ScheduleFileCodec {

    /** Версия формата, которую пишет эта сборка. Файл поновее не читается. */
    const val FORMAT = 1

    /** Расширение файла расписания. Своё, чтобы файл не путали с копией базы (`.db`). */
    const val EXTENSION = "incs"

    fun encode(export: ScheduleExport): ByteArray {
        val json = toJson(export).toString()
        return ScheduleFileCipher.seal(json.toByteArray(Charsets.UTF_8))
    }

    /** @throws ScheduleFileException если это не файл расписания или он повреждён. */
    fun decode(bytes: ByteArray): ScheduleExport {
        val json = ScheduleFileCipher.open(bytes).toString(Charsets.UTF_8)
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw ScheduleFileException("Файл повреждён: внутри не расписание.")
        }
        return try {
            fromJson(root)
        } catch (e: JSONException) {
            throw ScheduleFileException("Файл повреждён: в нём нет обязательных полей расписания.")
        }
    }

    // --- Запись ------------------------------------------------------------------------------

    private fun toJson(export: ScheduleExport): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("app", export.appVersion)
        put("exportedAt", export.exportedAt)
        put("type", export.type)
        put("plan", daysJson(export.plan))
        put("fact", export.fact?.let { daysJson(it) } ?: JSONObject.NULL)
        put("measurements", export.measurementCount)
        put("species", export.customSpecies?.let { speciesJson(it) } ?: JSONObject.NULL)
        put("breed", export.breed)
        put("incubatorBrand", export.incubatorBrand)
        put("incubatorModel", export.incubatorModel)
    }

    private fun daysJson(days: List<Value>): JSONArray = JSONArray().apply {
        days.sortedBy { it.day }.forEach { day ->
            put(JSONObject().apply {
                put("day", day.day)
                put("temp", day.temp ?: JSONObject.NULL)
                put("damp", day.damp ?: JSONObject.NULL)
                put("over", day.over ?: JSONObject.NULL)
                put("airingCount", day.airingCount ?: JSONObject.NULL)
                put("airingTime", day.airingTime ?: JSONObject.NULL)
                put("note", day.note)
            })
        }
    }

    private fun speciesJson(species: CustomSpecies): JSONObject = JSONObject().apply {
        put("name", species.name)
        put("days", JSONArray().apply {
            species.days.sortedBy { it.day }.forEach { day ->
                put(JSONObject().apply {
                    put("day", day.day)
                    put("temp", day.temp ?: JSONObject.NULL)
                    put("damp", day.damp ?: JSONObject.NULL)
                    put("over", day.over ?: JSONObject.NULL)
                    put("airingCount", day.airingCount ?: JSONObject.NULL)
                    put("airingTime", day.airingTime ?: JSONObject.NULL)
                    put("candling", day.candling)
                })
            }
        })
    }

    // --- Чтение ------------------------------------------------------------------------------

    private fun fromJson(root: JSONObject): ScheduleExport {
        val format = root.optInt("format", 0)
        if (format > FORMAT) {
            throw ScheduleFileException(
                "Файл сохранён более новой версией приложения (формат $format, здесь " +
                    "$FORMAT). Обновите приложение."
            )
        }
        val type = root.getString("type").trim()
        if (type.isBlank()) throw ScheduleFileException("В файле не указан вид птицы.")
        val plan = daysFrom(root.getJSONArray("plan"))
        if (plan.isEmpty()) throw ScheduleFileException("В файле нет ни одного дня расписания.")
        // Пустое среднее — то же, что его нет: выбирать «по замерам» было бы не из чего.
        val fact = if (root.isNull("fact")) null else daysFrom(root.getJSONArray("fact")).takeIf { it.isNotEmpty() }
        val species = if (root.isNull("species")) null else speciesFrom(root.getJSONObject("species"))
        return ScheduleExport(
            type = type,
            plan = plan,
            fact = fact,
            measurementCount = if (fact == null) 0 else root.optInt("measurements", 0).coerceAtLeast(0),
            customSpecies = species,
            breed = root.optString("breed").trim(),
            incubatorBrand = root.optString("incubatorBrand").trim(),
            incubatorModel = root.optString("incubatorModel").trim(),
            appVersion = root.optString("app"),
            exportedAt = root.optString("exportedAt"),
        )
    }

    private fun daysFrom(array: JSONArray): List<Value> =
        (0 until array.length()).map { i ->
            val d = array.getJSONObject(i)
            Value(
                id = 0,
                day = d.getInt("day"),
                temp = d.doubleOrNull("temp"),
                damp = d.doubleOrNull("damp"),
                over = d.intOrNull("over"),
                airingCount = d.intOrNull("airingCount"),
                airingTime = d.intOrNull("airingTime"),
                note = d.optString("note"),
                idPT = 0,
            )
        }.sortedBy { it.day }

    private fun speciesFrom(o: JSONObject): CustomSpecies {
        val days = o.optJSONArray("days")?.let { array ->
            (0 until array.length()).map { i ->
                val d = array.getJSONObject(i)
                CustomSpeciesDay(
                    day = d.getInt("day"),
                    temp = d.doubleOrNull("temp"),
                    damp = d.doubleOrNull("damp"),
                    over = d.intOrNull("over"),
                    airingCount = d.intOrNull("airingCount"),
                    airingTime = d.intOrNull("airingTime"),
                    candling = d.optBoolean("candling", false),
                )
            }
        }.orEmpty()
        return CustomSpecies(id = 0, name = o.optString("name"), days = days.sortedBy { it.day })
    }

    // `optDouble` на отсутствующем ключе отдаёт NaN, а `optInt` — ноль, и ноль здесь
    // значит «не делать», а не «нормы нет». Поэтому `null` проверяется явно.
    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)
}
