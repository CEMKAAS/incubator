package ru.zaroslikov.incubator.ui.species

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.CustomSpeciesDay
// Под псевдонимом: рядом живёт `ValueUiState.toValue(temperatureUnit)` из формы закладки, и одно имя
// на два перевода в одном файле читалось бы как один и тот же.
import ru.zaroslikov.incubator.domain.model.toValue as asValue
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.batch.ValueUiState
import ru.zaroslikov.incubator.ui.batch.toValue
import ru.zaroslikov.incubator.ui.batch.toValueUiState
import ru.zaroslikov.incubator.ui.incubator.plural
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel

/**
 * Строка конструктора — день своего вида. План дня — тот же [ValueUiState], что в таблице формы
 * закладки (одни правила ввода, «пусто — не ноль»); отметка овоскопирования лежит рядом, у закладки
 * такой величины нет. Без `@Immutable`: половина полей [ValueUiState] — `var`.
 */
data class SpeciesDayRow(val value: ValueUiState, val candling: Boolean = false)

/**
 * Состояние конструктора своего вида — всё, что рисует [CustomSpeciesSheet].
 *
 * Производные величины остались свойствами состояния, а не его полями: все они дешёвые.
 * [nameTaken] — поиск по списку из нескольких имён, [summary] — два подсчёта по строкам
 * таблицы, и пересчитывать их в редьюсере значило бы держать в состоянии копию того,
 * что и так лежит в нём рядом, с обязанностью не дать копии разойтись с исходником.
 */
@Immutable
data class CustomSpeciesState(
    val name: String = "",
    /** Правка существующего вида; при создании — `false`. */
    val isEditing: Boolean = false,
    /**
     * Вид ещё читается из базы. Только при правке: у нового вида читать нечего, и
     * колесо над формой, которая всё равно будет пустой, обещало бы содержимое.
     */
    val loading: Boolean = false,
    /**
     * Каталог всех видов — нужен ровно для двух вещей: проверить, не занято ли имя, и
     * достать режим встроенного вида для кнопки «взять за основу».
     *
     * Пересобирается на каждый ответ базы, как и на других экранах: это производная от
     * данных, а не состояние, которое кто-то ведёт.
     */
    val catalog: SpeciesCatalog = SpeciesCatalog.EMPTY,
    /** Дни вида — строки таблицы; хотя бы одна есть всегда. */
    val rows: List<SpeciesDayRow> = emptyList(),
    /**
     * Встроенный вид, чей режим взят за основу; `null` — не брали. Только для подписи
     * выпадающего списка: таблица после этого правится свободно, и вид остаётся
     * названием источника, а не обещанием, что строки с ним совпадают.
     */
    val seededFrom: String? = null,
    /** Идентификатор правящегося вида; ноль — создаётся новый. */
    val editedId: Long = 0,
    /**
     * Поля имени касались. Без этого «Введите название» стояло бы красным над пустой
     * формой в первую же секунду после открытия — то есть упрёком за то, чего человек
     * ещё не успел сделать. Стереть уже набранное имя — другое дело: это ошибка, и
     * сказать о ней надо сразу, потому что кнопка сохранения при пустом имени всё
     * равно недоступна и сама ничего не объяснит.
     */
    val nameTouched: Boolean = false,
    /**
     * Запись уже идёт. Пока идёт — кнопка гаснет: второе нажатие в тот же момент
     * повторило бы вставку того же имени, уникальный индекс ответил бы отказом, а
     * отказ из корутины ViewModel — это падение приложения.
     */
    val saving: Boolean = false,
    /**
     * Почему сохранение не удалось; `null` — не было такого. Единственный ожидаемый
     * случай — занятое имя, проскочившее мимо проверки каталога (например, вид с тем же
     * именем только что завёл кто-то на этом же телефоне), и ответ на него тот же, что
     * даёт проверка: поправить имя.
     */
    val saveError: String? = null,
) {
    /** Дней уже столько, сколько конструктор готов показать; кнопка «Добавить» гаснет. */
    val canAddDay: Boolean get() = rows.size < CustomSpeciesViewModel.MAX_DAYS

    val nameTaken: Boolean get() = catalog.isNameTaken(name, editedId)

    /**
     * Что не так с именем, или `null`.
     *
     * Занятое имя проверяется каталогом, то есть и по встроенным видам, и по своим:
     * две плитки «Курицы» в форме закладки нельзя было бы различить, а закладка
     * ссылается на вид именно по имени.
     */
    val nameError: String?
        get() = when {
            name.isBlank() -> if (nameTouched) "Введите название" else null
            nameTaken -> "Такой вид уже есть"
            else -> saveError
        }

    val isValid: Boolean get() = name.isNotBlank() && !nameTaken && rows.isNotEmpty()

    /** «21 день · 3 овоскопирования» — то, чем вид получился, одной строкой. */
    val summary: String
        get() = speciesSummary(days = rows.size, candlings = rows.count { it.candling })
}

sealed interface CustomSpeciesIntent {
    /**
     * Вызывается из `LaunchedEffect` шторки, а не приходит из `SavedStateHandle`:
     * маршрута у шторки нет. Ноль — создаётся новый вид.
     */
    data class Load(val speciesId: Long = 0) : CustomSpeciesIntent

    data class UpdateName(val value: String) : CustomSpeciesIntent

    /** Правка одной клетки строки; строку целиком меняет только таблица. */
    data class UpdateRow(val index: Int, val value: ValueUiState) : CustomSpeciesIntent

    data class ToggleCandling(val index: Int) : CustomSpeciesIntent

    data object AddDay : CustomSpeciesIntent

    data object RemoveLastDay : CustomSpeciesIntent

    /** «Взять за основу»: заполнить таблицу режимом встроенного вида. */
    data class SeedFrom(val builtIn: String) : CustomSpeciesIntent

    data object Save : CustomSpeciesIntent
}

sealed interface CustomSpeciesEffect {
    /**
     * Вид записан. [species] уже с идентификатором из базы — вызвавший экран выбирает
     * его сразу, не дожидаясь ответа потока.
     */
    data class Saved(val species: CustomSpecies) : CustomSpeciesEffect
}

/**
 * Конструктор своего вида птицы: имя, режим по дням и дни овоскопирования — то, что для встроенных
 * видов написано кодом в `domain.incubation`. **Срок инкубации отдельным полем не спрашивается**: он
 * и есть число строк в таблице.
 *
 * Состояние локальное ([StatefulMviViewModel]) — это форма; каталог видов приезжает из базы через
 * коллектор внутри [CustomSpeciesIntent.Load].
 */
class CustomSpeciesViewModel(
    private val settings: AppSettings,
    private val itemsRepository: ItemsRepository,
) : StatefulMviViewModel<CustomSpeciesState, CustomSpeciesIntent, CustomSpeciesEffect>(
    CustomSpeciesState(),
) {
    /**
     * Градусы полей ввода — из «Настроек». Строки формы держатся в них, а число в базе
     * всегда в Цельсиях; перевод — `toValueUiState(unit)` / `toValue(unit)` в `ValueFormat`.
     * Читается при каждом обращении, а не один раз: форма переживает уход в настройки.
     */
    private val temperatureUnit: TemperatureUnit
        get() = settings.temperatureUnit

    /** Подписка на каталог; своя на каждое открытие, см. [load]. */
    private var catalogJob: Job? = null

    override fun onIntent(intent: CustomSpeciesIntent) {
        when (intent) {
            is CustomSpeciesIntent.Load -> load(intent.speciesId)
            is CustomSpeciesIntent.UpdateName -> updateName(intent.value)
            is CustomSpeciesIntent.UpdateRow -> updateRow(intent.index, intent.value)
            is CustomSpeciesIntent.ToggleCandling -> toggleCandling(intent.index)
            CustomSpeciesIntent.AddDay -> addDay()
            CustomSpeciesIntent.RemoveLastDay -> removeLastDay()
            is CustomSpeciesIntent.SeedFrom -> seedFrom(intent.builtIn)
            CustomSpeciesIntent.Save -> save()
        }
    }

    private fun load(speciesId: Long) {
        val editing = speciesId != 0L
        reduce {
            CustomSpeciesState(
                isEditing = editing,
                editedId = speciesId,
                loading = editing,
                // Новый вид начинается с одного пустого дня, а не с пустой таблицы:
                // шапка столбцов над пустым местом не говорит, что строки добавляют
                // кнопкой, — а первый день нужен любому виду.
                rows = if (editing) emptyList() else listOf(emptyRow(1)),
            )
        }

        // Прежняя подписка снимается: шторка открывается по многу раз на одной и той
        // же ViewModel, и второй сборщик продолжал бы заполнять форму видом, который
        // закрыли.
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            itemsRepository.getCustomSpecies().collect { list ->
                val newCatalog = SpeciesCatalog(list)
                if (!current.loading) {
                    // Форма уже заполнена — из базы дальше нужен только каталог: по нему
                    // проверяется занятость имени, и он же отдаёт режим встроенного вида.
                    reduce { copy(catalog = newCatalog) }
                    return@collect
                }
                val species = list.firstOrNull { it.id == speciesId }
                val unit = temperatureUnit
                reduce {
                    val filled = if (species != null) {
                        copy(
                            name = species.name,
                            rows = species.days.sortedBy { it.day }.map { it.toRow(unit) },
                        )
                    } else {
                        // Вида с таким идентификатором уже нет — его удалили, пока шторка
                        // открывалась. Форма становится формой нового вида: сохранение с
                        // прежним идентификатором обновило бы пустоту и вставило бы дни
                        // под несуществующий вид, а это нарушение внешнего ключа.
                        copy(editedId = 0, isEditing = false)
                    }
                    filled.copy(
                        catalog = newCatalog,
                        // Вид без дней в базе невозможен, но таблица без строк — это шапка
                        // над пустотой, из которой не выбраться: «убрать последний» дальше
                        // нечего.
                        rows = filled.rows.ifEmpty { listOf(emptyRow(1)) },
                        loading = false,
                    )
                }
            }
        }
    }

    /**
     * Имя обрезается по [MAX_NAME_LENGTH], а не отвергается: вставленный из буфера
     * абзац должен стать началом имени, а не пропасть. Плитка в форме закладки и
     * подпись на карточке рассчитаны на слово-два, не на предложение.
     */
    private fun updateName(value: String) {
        reduce {
            copy(
                name = value.take(MAX_NAME_LENGTH),
                nameTouched = true,
                // Новое имя — новая попытка: прежний отказ базы к нему не относится.
                saveError = null,
            )
        }
    }

    private fun updateRow(index: Int, value: ValueUiState) {
        reduce {
            if (index !in rows.indices) return@reduce this
            copy(
                rows = rows.mapIndexed { i, row ->
                    if (i == index) row.copy(value = value) else row
                },
            )
        }
    }

    private fun toggleCandling(index: Int) {
        reduce {
            if (index !in rows.indices) return@reduce this
            copy(
                rows = rows.mapIndexed { i, row ->
                    if (i == index) row.copy(candling = !row.candling) else row
                },
            )
        }
    }

    /**
     * Новый день в конце таблицы — **с цифрами предыдущего дня**.
     *
     * Копия, а не пустая строка, потому что режим меняется не каждый день: у курицы
     * температура стоит одна и та же три недели подряд, и набирать «37.8 / 55» двадцать
     * один раз — это ровно то, из-за чего конструктором никто не воспользовался бы.
     * Отметка овоскопирования при этом не копируется: она про конкретный день, и
     * скопированная превратила бы её в «каждый день», чего не бывает.
     */
    private fun addDay() {
        reduce {
            if (!canAddDay) return@reduce this
            val day = rows.size + 1
            val previous = rows.lastOrNull()?.value
            copy(
                rows = rows + SpeciesDayRow(
                    value = previous?.copy(id = 0, day = day, note = "", idPT = 0)
                        ?: emptyValue(day),
                    candling = false,
                ),
            )
        }
    }

    /** Последний день из таблицы. Один день остаётся всегда: вид без дней — не вид. */
    private fun removeLastDay() {
        reduce { if (rows.size > 1) copy(rows = rows.dropLast(1)) else this }
    }

    /**
     * Заполняет таблицу режимом встроенного вида — «взять за основу».
     *
     * Только при создании: у правящегося вида таблица уже своя, и подменить её целиком
     * значило бы стереть то, ради чего его открыли. Дни овоскопирования переносятся
     * вместе с режимом — они такая же часть описания птицы, как температура.
     */
    private fun seedFrom(builtIn: String) {
        val snapshot = current
        if (snapshot.isEditing) return
        val schedule = snapshot.catalog.schedule(builtIn)
        if (schedule.isEmpty()) return
        val unit = temperatureUnit
        val seeded = schedule.sortedBy { it.day }.mapIndexed { index, value ->
            SpeciesDayRow(
                // Дни перенумеровываются по месту в таблице, а идентификаторы
                // обнуляются: строки чужие и своими станут только при сохранении.
                value = value.toValueUiState(unit).copy(id = 0, day = index + 1, idPT = 0),
                candling = snapshot.catalog.isCandlingDay(builtIn, value.day),
            )
        }
        reduce { copy(rows = seeded, seededFrom = builtIn) }
    }

    /**
     * Сохраняет вид целиком — строку и все её дни. Дни нумеруются по месту в таблице, чтобы номера
     * оставались сплошными, — на них держатся срок и порядок. Идентификаторы дней нулевые:
     * `saveCustomSpecies` переписывает их одной транзакцией.
     */
    private fun save() {
        val snapshot = current
        if (!snapshot.isValid || snapshot.saving) return
        reduce { copy(saving = true, saveError = null) }
        val unit = temperatureUnit
        val species = CustomSpecies(
            id = snapshot.editedId,
            name = snapshot.name.trim(),
            days = snapshot.rows.mapIndexed { index, row ->
                val value = row.value.toValue(unit)
                CustomSpeciesDay(
                    id = 0,
                    speciesId = snapshot.editedId,
                    day = index + 1,
                    temp = value.temp,
                    damp = value.damp,
                    over = value.over,
                    airingCount = value.airingCount,
                    airingTime = value.airingTime,
                    candling = row.candling,
                )
            },
        )
        viewModelScope.launch {
            try {
                val id = itemsRepository.saveCustomSpecies(species)
                sendEffect(CustomSpeciesEffect.Saved(species.copy(id = id)))
            } catch (e: CancellationException) {
                // Отмена — не отказ базы: ViewModel уходит, и сообщать ошибку некому.
                throw e
            } catch (e: Exception) {
                // Отказ базы — ошибка формы, а не приложения: имя занято, и это видно
                // под полем. Всё остальное сюда не доходит: запрос параметризован,
                // а дни перенумерованы, так что второму индексу нарушать нечего.
                reduce { copy(saveError = "Не удалось сохранить: такой вид уже есть") }
            } finally {
                reduce { copy(saving = false) }
            }
        }
    }

    private fun emptyValue(day: Int): ValueUiState = ValueUiState(day = day)

    private fun emptyRow(day: Int): SpeciesDayRow = SpeciesDayRow(emptyValue(day))

    companion object {
        /** Самый долгий срок среди птиц — страусы, около 42 дней; сотня — с запасом. */
        const val MAX_DAYS = 100

        /** Длина имени: «Цесарки серебристые» — 19 знаков, вдвое больше хватит любому. */
        const val MAX_NAME_LENGTH = 40
    }
}

/**
 * «21 день · 3 овоскопирования»; без овоскопирований — «21 день · без овоскопирований».
 *
 * Один помощник на конструктор и на карточку в настройках: та же подпись о том же
 * виде, и две копии однажды сказали бы про один вид разное.
 */
internal fun speciesSummary(days: Int, candlings: Int): String {
    val daysPart = plural(days, "день", "дня", "дней")
    val candlingPart = if (candlings == 0) {
        "без овоскопирований"
    } else {
        plural(candlings, "овоскопирование", "овоскопирования", "овоскопирований")
    }
    return "$daysPart · $candlingPart"
}

/**
 * День своего вида как строка таблицы: план — в поля ввода, отметка — рядом.
 *
 * Через `CustomSpeciesDay.toValue()` из :domain, а не своим перекладыванием полей: этой
 * же функцией каталог порождает расписание закладки, и второй перевод «день вида →
 * строка плана» разошёлся бы с первым ровно там, где появится новая величина.
 */
private fun CustomSpeciesDay.toRow(unit: TemperatureUnit): SpeciesDayRow = SpeciesDayRow(
    value = asValue().toValueUiState(unit),
    candling = candling,
)
