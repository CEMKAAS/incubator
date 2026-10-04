package ru.zaroslikov.incubator.ui.batch

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.calendar.CalendarOffer
import ru.zaroslikov.incubator.calendar.batchCalendarDates
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.incubation.setAutoIncubator
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.Species
import ru.zaroslikov.incubator.ui.components.toFormState
import ru.zaroslikov.incubator.ui.components.toSettings
import ru.zaroslikov.incubator.domain.model.Time
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.model.knownBreeds as knownBreedsOf
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.transfer.ScheduleExport
import ru.zaroslikov.incubator.transfer.ScheduleFileException
import ru.zaroslikov.incubator.transfer.ScheduleTransferController
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.todayText
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Откуда берётся режим, взятый из завершённой закладки.
 *
 * [Plan] — её план по дням, то есть цифры, с которыми она начиналась. [Fact] — среднее
 * по её замерам: чем режим оказался на самом деле. Второе имеет смысл предлагать не
 * всегда — закладку могли провести, не записав ни одного показания.
 */
enum class ArchiveScheduleSource { Plan, Fact }

/**
 * Завершённая закладка как вариант в списке выбора.
 *
 * [hasMeasurements] решает, доступно ли у неё «среднее по замерам»: без факта этот
 * выбор молча вернул бы тот же план, и разницы между двумя кнопками не было бы видно.
 *
 * Хранится при этом само число замеров, а не флаг: `countMeasurements` возвращает его
 * и так, а сводка о выбранной закладке в диалоге отвечает не «есть ли замеры», а
 * «сколько» — на трёх замерах среднее по факту значит совсем не то же, что на двух
 * сотнях.
 */
data class ArchiveOption(val batch: Batch, val measurementCount: Int) {
    val hasMeasurements: Boolean get() = measurementCount > 0
}

/**
 * Откуда взят режим, стоящий сейчас в таблице: какая закладка и что из неё.
 *
 * Хранится целиком [ArchiveOption], а не один идентификатор: список архива
 * перечитывается при смене вида, а сводка над таблицей должна пережить и то, что
 * закладка из него уйдёт — вид сменили, а режим взяли до того.
 */
data class AppliedArchive(val option: ArchiveOption, val source: ArchiveScheduleSource)

/**
 * Откуда взят режим, стоящий сейчас в таблице, если не из `:domain`: из завершённой
 * закладки этого хозяйства ([Archive]) или из файла расписания ([File]).
 *
 * Один тип на оба, потому что над таблицей одно место, и стоять в нём должно ровно
 * одно из трёх — предупреждение о справочных цифрах, сводка об архивной закладке или
 * сводка о файле; два независимых поля позволили бы двум сводкам стоять разом.
 */
sealed interface ScheduleOrigin {
    data class Archive(val applied: AppliedArchive) : ScheduleOrigin

    /** [source] — что взяли из файла: план или среднее по замерам, как и из архива. */
    data class File(val export: ScheduleExport, val source: ArchiveScheduleSource) : ScheduleOrigin
}

/**
 * Прочитанный файл расписания, ожидающий подтверждения в диалоге.
 *
 * Диалог нужен не ради «вы уверены», а ради двух вещей, о которых человек должен
 * узнать до того, как таблица перепишется. [speciesChanges] — вид в файле не тот, что
 * выбран в форме: расписание утки, вставленное в форму, где стоят «Курицы», сменит
 * вид, а с ним срок и овоскопирования, и молча делать это нельзя — человек мог
 * перепутать файл. [creatingSpecies] — вида из файла у получателя нет, и он будет
 * заведён как свой: это запись в базу, которая переживёт и отмену формы.
 *
 * [resolvedType] — имя вида, каким оно станет в форме: имя из файла, приведённое к
 * тому, что уже есть в каталоге получателя (регистр и края не в счёт), либо оно же как
 * есть, когда вид предстоит создать.
 */
data class ImportPreview(
    val export: ScheduleExport,
    val currentType: String,
    val resolvedType: String,
    val creatingSpecies: Boolean,
) {
    val speciesChanges: Boolean get() = resolvedType != currentType
}

/**
 * Состояние формы закладки — всё, что рисует [AddBatchSheet], и та немногая
 * бухгалтерия, без которой оно не считается.
 *
 * Поля формы лежат в [form] отдельным классом ([BatchUiState]) по той же причине, по
 * которой так устроена форма инкубатора: у него есть переводы в [Batch] и обратно, и
 * держать их на классе, где рядом лежат подсказки пород и таблица режима, значило бы
 * переводить в модель и их.
 *
 * Класс помечен `@Immutable`, хотя внутри лежат модели `:domain` с полями `var`
 * ([Time], [Batch], [Value] внутри [ScheduleOrigin.File] и внутри [NeighbourSchedule]
 * у каждого соседа). Обещание выполняется не
 * типом, а тем, что ни одна строка этой ViewModel их на месте не правит: все изменения
 * идут через `copy`, а единственный, кто действительно правит [Value] на месте —
 * `setAutoIncubator`, — работает со свежесгенерированными строками до того, как они
 * попадут в состояние.
 */
@Immutable
data class AddBatchState(
    /** Поля первой страницы формы: название, вид, породы, яйца, цена, дата, заметка. */
    val form: BatchUiState = BatchUiState(),
    /** Правка существующей закладки; при создании — `false`. */
    val isEditing: Boolean = false,
    /**
     * Форма правки ещё читается из базы. Только при правке: пустые поля над существующей
     * закладкой — не «ничего не заполнено», а ответ, которого ещё нет. Вопрос про первую
     * страницу, в отличие от [scheduleReady], который про таблицу.
     */
    val loading: Boolean = false,
    /**
     * Виды птицы — встроенные и свои: плитки сетки, срок под плиткой, таблица
     * (`catalog.schedule`). Подписка живёт с ViewModel, а не с загрузкой формы: свой вид
     * могут завести из этой же формы.
     */
    val catalog: SpeciesCatalog = SpeciesCatalog.EMPTY,
    /**
     * Породы выбранной птицы, которые уже вводили в других закладках, — подсказки под
     * полем «Порода» (`knownBreeds` в :domain). Поле, а не `get()`: страницу полей
     * перерисовывает каждый символ; пересчитывается в `withKnownBreeds` — на ответ базы и
     * смену вида.
     */
    val knownBreeds: List<String> = emptyList(),
    /** Времена напоминаний, как их правят в форме; при сохранении перезаписываются целиком. */
    val reminders: List<Time> = emptyList(),
    /**
     * Режим по дням — вторая страница формы. Строками ([ValueUiState]), а не [Value]:
     * стёртая температура должна остаться пустой, а не стать нулём.
     *
     * При создании порождается из [SpeciesCatalog.schedule] и переписывается при смене
     * вида. При правке загружаются существующие строки **вместе с идентификаторами**:
     * замеры висят на них, поэтому сохранение обновляет строки, а не создаёт заново.
     */
    val schedule: List<ValueUiState> = emptyList(),
    /**
     * Таблица наполнена — не путать с «непустая»: при правке строки приезжают из базы не
     * сразу, и без флага пустой список был бы неотличим от вида, для которого режима нет.
     */
    val scheduleReady: Boolean = false,
    /**
     * Завершённые закладки того же вида — источник выверенного режима вместо расписания
     * по умолчанию. Пусто — диалога нет. При правке не используется. Рядом с каждой —
     * число замеров: от него зависит, предлагать ли среднее по факту ([ArchiveScheduleSource]).
     */
    val archiveOptions: List<ArchiveOption> = emptyList(),
    /**
     * Откуда режим в таблице — из архивной закладки или из файла — или `null`, если он
     * порождён [SpeciesCatalog.schedule]. От этого зависит, что стоит над таблицей:
     * предупреждение о справочных цифрах или сводка об источнике.
     */
    val scheduleOrigin: ScheduleOrigin? = null,
    /** Файл расписания прочитан и ждёт ответа в диалоге; `null` — диалога нет. */
    val importPreview: ImportPreview? = null,
    /** Файл не прочитан — готовый текст для диалога; `null` — ошибки нет. */
    val importError: String? = null,
    /** Файл читается: кнопка импорта на это время не нажимается второй раз. */
    val importing: Boolean = false,
    /**
     * Все закладки хозяйства — ради одного: пород, которые уже вводили ([knownBreeds]).
     * Экран их не рисует; подписка живёт с ViewModel по той же причине, что и [catalog].
     */
    val allBatches: List<Batch> = emptyList(),
    /**
     * Тот же режим, что в [schedule], но до автоматики: с целыми столбцами поворота и
     * проветривания. Включённая автоматика стирает норму (`setAutoIncubator`), и без этого
     * снимка выключатель нельзя было бы отжать; возвращаются два столбца, правки в
     * остальных не трогаются.
     */
    val scheduleBase: List<ValueUiState> = emptyList(),
    /**
     * Переключатели автоматики уже трогали руками: флаги приезжают из инкубатора
     * асинхронно, и без этого ответ базы затёр бы выбор человека, успевшего нажать раньше.
     */
    val autoTouched: Boolean = false,
    /** Напоминания, какими они были при открытии: их нужно удалить перед перезаписью. */
    val loadedReminders: List<Time> = emptyList(),
    /**
     * Овоскопирования закладки по дням — только при правке ([candlingEditRows]). Их сумма
     * живёт в `form.candlingRejected`; записи в базе меняются при сохранении формы.
     */
    val candlings: List<CandlingEditRow> = emptyList(),
    /**
     * Открытый диалог «Отбраковано яиц» и то, что в нём набрано; `null` — закрыт.
     * Черновик, а не правка формы на месте: «Отмена» должна вернуть всё как было, и
     * держит его ViewModel, а не `rememberSaveable`, — так набранное переживает поворот
     * вместе со строками дней.
     */
    val rejectedDraft: RejectedDraft? = null,
    /**
     * Идущие закладки этого инкубатора со своими днями — соседи новой закладки по
     * воздуху. Только при создании: при правке закладка сама среди идущих. Подписка живёт
     * с формой ([load]).
     */
    val neighbours: List<NeighbourSchedule> = emptyList(),
    /**
     * Сколько яиц уже лежит в инкубаторе — сумма заложенного по [neighbours]. Вместе с
     * [capacity] рисует под «Количеством яиц» полосу заполнения, чтобы перебор был виден,
     * пока число можно поправить. Только при создании, как [neighbours]. Поле, а не
     * `get()`, по тому же правилу, что [knownBreeds].
     */
    val occupiedEggs: Int = 0,
    /** Вместимость инкубатора; ноль — не указана, и полоса просит её заполнить. */
    val capacity: Int = 0,
    /**
     * Приговор каждой строке [schedule] — насколько её температура и влажность расходятся
     * с планом соседей на ту же дату ([overlapVerdicts]); `null` у дня без соседей. Поле,
     * а не `get()`; считается в [withOverlap] там, где меняются входы.
     */
    val overlap: List<CellVerdicts?> = emptyList(),
    /**
     * Есть хоть одна окрашенная клетка — над таблицей нужна легенда. Именно «окрашенная»,
     * а не «с приговором»: у дня с соседом, но без заданных температуры и влажности,
     * приговор есть, а заливки нет.
     */
    val overlapShown: Boolean = false,
) {
    /**
     * Название и хоть одно яйцо — как и прежде; с породами добавляется третье: у каждой
     * строки есть имя и яйца, и имена не повторяются. Строка без имени при двух породах —
     * это закладка, о которой нечего сказать в статистике, строка без яиц — закладка,
     * в которой ничего нет, а две строки одной породы — две закладки с одним названием,
     * которых никто не различит: один лоток одной породы закладывают одной закладкой.
     */
    val isValid: Boolean
        get() = form.title.isNotBlank() && form.eggCount > 0 && (
            !form.splitByBreeds || form.breeds.all {
                it.name.isNotBlank() && (it.eggs.toIntOrNull() ?: 0) > 0
            } && form.breedNamesDistinct
        ) &&
            // Пустой вывод у завершённой закладки — не ноль, а стёртый итог: «не указали»
            // и «вывелось ноль» — разные ответы, как и в диалоге завершения.
            (!form.editableHatch || form.eggAllEND.isNotBlank()) &&
            // Птенцов не больше, чем дожило яиц: «Количество яиц» можно уменьшить уже
            // после вывода, и молча урезать вывод при сохранении было бы хуже отказа.
            form.hatchFits &&
            // Момент окончания — не раньше закладки и не в будущем, как в диалогах завершения.
            (!form.finished || form.finishMomentError == null) &&
            // Отбраковано не больше, чем заложено. Диалог этого не пустит, но заложенное
            // можно уменьшить уже после, и тогда держит только это место.
            (!isEditing || form.rejectedFits)
}

sealed interface AddBatchIntent {
    /**
     * Заполняет форму заново — шторка зовёт это при каждом открытии с отменённым
     * черновиком, прошлый ввод не тянется следом.
     *
     * @param incubatorId инкубатор, внутри которого создаётся закладка.
     * @param batchId ноль — создание, иначе правка существующей закладки.
     */
    data class Load(val incubatorId: Long, val batchId: Long) : AddBatchIntent

    /** Правка любого поля первой страницы: шторка отдаёт копию [BatchUiState] целиком. */
    data class Update(val form: BatchUiState) : AddBatchIntent

    /** Нажатие на поле «Отбраковано яиц»: открывает диалог с разбивкой. */
    data object OpenRejected : AddBatchIntent

    /** Отбраковано руками — набранный в диалоге текст как есть. */
    data class UpdateManualRejected(val rejected: String) : AddBatchIntent

    /** Выбраковано на овоскопировании строки [index] диалога — набранный текст как есть. */
    data class UpdateCandling(val index: Int, val rejected: String) : AddBatchIntent

    /** «Готово» в диалоге отбраковки. */
    data object ConfirmRejected : AddBatchIntent

    /** «Отмена» в диалоге отбраковки: набранное в нём пропадает. */
    data object DismissRejected : AddBatchIntent

    data class UpdateBreed(val index: Int, val row: BreedUiState) : AddBatchIntent

    /** Ещё одна порода — при сохранении она станет отдельной закладкой. Только при создании. */
    data object AddBreed : AddBatchIntent

    data class RemoveBreed(val index: Int) : AddBatchIntent

    /** Правка одной клетки таблицы; строку меняет только страница расписания. */
    data class UpdateScheduleRow(val index: Int, val row: ValueUiState) : AddBatchIntent

    /** Свой вид, только что сохранённый из конструктора поверх этой формы. */
    data class SelectNewSpecies(val species: CustomSpecies) : AddBatchIntent

    data class SetAutoTurn(val enabled: Boolean) : AddBatchIntent

    data class SetAutoAiring(val enabled: Boolean) : AddBatchIntent

    data class ApplyArchiveSchedule(
        val sourceId: Long,
        val source: ArchiveScheduleSource,
    ) : AddBatchIntent

    /** Возврат к режиму по умолчанию — единственный способ отменить правки таблицы. */
    data object ResetSchedule : AddBatchIntent

    data class ImportFile(val source: Uri) : AddBatchIntent

    data object DismissImport : AddBatchIntent

    data object ClearImportError : AddBatchIntent

    data class ConfirmImport(val source: ArchiveScheduleSource) : AddBatchIntent

    data object AddReminder : AddBatchIntent

    data class RemoveReminder(val index: Int) : AddBatchIntent

    /**
     * Правка одного напоминания. `null` означает «это поле не меняем»: карточка правит
     * время и текст разными полями, а строка в базе одна.
     */
    data class UpdateReminder(
        val index: Int,
        val time: String? = null,
        val note: String? = null,
    ) : AddBatchIntent

    data object Save : AddBatchIntent
}

sealed interface AddBatchEffect {
    /**
     * Закладки записаны; [ids] — их идентификаторы. При правке и при создании с одной
     * породой — ровно один; с несколькими породами — по закладке на каждую, в порядке
     * строк формы.
     *
     * [calendar] — важные даты новой закладки, которые экран предлагает записать в
     * календарь телефона. Только при создании: при правке вопрос на каждое сохранение
     * формы был бы уже не вопросом, а помехой. `null` — предлагать нечего: дата не
     * разобралась, срок вида неизвестен или все даты уже прошли.
     */
    data class Saved(
        val ids: List<Long>,
        val calendar: CalendarOffer? = null,
    ) : AddBatchEffect
}

/**
 * Форма закладки — макет
 * [12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555).
 *
 * Одна форма и на создание, и на правку, как у инкубатора: отдельного макета для
 * правки нет, а поля совпадают ровно. Что именно открыли, решает [AddBatchIntent.Load];
 * [AddBatchState.isEditing] переключает заголовок, кнопку и показ действий, которые
 * правке недоступны.
 *
 * Ни инкубатор, ни закладка не приходят из SavedStateHandle: форма живёт в нижней
 * шторке, у которой нет своего маршрута.
 *
 * Состояние локальное ([StatefulMviViewModel]), а не собранное из потоков базы: форма
 * держит ввод у себя, и подписка, которая гасла бы при уходе с экрана, унесла бы с
 * собой набранное. Потоки базы — свои виды и закладки хозяйства — собираются
 * коллекторами в [init] и пишут в то же состояние через `reduce`.
 */
class AddBatchViewModel(
    private val settings: AppSettings,
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository,
    private val scheduleTransfer: ScheduleTransferController,
) : StatefulMviViewModel<AddBatchState, AddBatchIntent, AddBatchEffect>(AddBatchState()) {
    /**
     * Градусы полей ввода — из «Настроек». Строки формы держатся в них, а число в базе
     * всегда в Цельсиях; перевод — `toValueUiState(unit)` / `toValue(unit)` в `ValueFormat`.
     * Читается при каждом обращении, а не один раз: форма переживает уход в настройки.
     */
    private val temperatureUnit: TemperatureUnit
        get() = settings.temperatureUnit

    /**
     * Чтение формы: закладка, её времена и её дни — либо флаги автоматики инкубатора
     * при создании. Отменяется при повторной загрузке: шторку могли открыть заново на
     * другой закладке, и тогда ответ базы уже не о ней.
     */
    private var loadJob: Job? = null

    /**
     * При правке: свет закладки, как он лежит в базе, и как его вернёт нетронутая форма.
     * Пустые поля закладки форма показывает значениями инкубатора, и сохранение без
     * правки не должно превращать их в собственные (`save`). `null` при создании.
     */
    private var shownPower: Pair<PowerSettings, PowerSettings>? = null

    /** Чтение архива закладок вида; своё на каждый вид, см. [refreshArchive]. */
    private var archiveJob: Job? = null

    /**
     * Подписка на идущие закладки инкубатора и их дни — соседей новой закладки.
     * Отменяется при повторной загрузке вместе с [loadJob]: форму могли открыть заново
     * в другом приборе.
     */
    private var neighboursJob: Job? = null

    init {
        viewModelScope.launch {
            itemsRepository.getCustomSpecies().collect { species ->
                reduce { copy(catalog = SpeciesCatalog(species)) }
            }
        }
        viewModelScope.launch {
            itemsRepository.getAllBatches().collect { batches ->
                reduce { copy(allBatches = batches).withKnownBreeds() }
            }
        }
    }

    override fun onIntent(intent: AddBatchIntent) {
        when (intent) {
            is AddBatchIntent.Load -> load(intent.incubatorId, intent.batchId)
            is AddBatchIntent.Update -> updateForm(intent.form)
            AddBatchIntent.OpenRejected -> openRejected()
            is AddBatchIntent.UpdateManualRejected -> updateRejectedDraft(null, intent.rejected)
            is AddBatchIntent.UpdateCandling -> updateRejectedDraft(intent.index, intent.rejected)
            AddBatchIntent.ConfirmRejected -> confirmRejected()
            AddBatchIntent.DismissRejected -> reduce { copy(rejectedDraft = null) }
            is AddBatchIntent.UpdateBreed -> updateBreed(intent.index, intent.row)
            AddBatchIntent.AddBreed -> addBreed()
            is AddBatchIntent.RemoveBreed -> removeBreed(intent.index)
            is AddBatchIntent.UpdateScheduleRow -> updateScheduleRow(intent.index, intent.row)
            is AddBatchIntent.SelectNewSpecies -> selectNewSpecies(intent.species)
            is AddBatchIntent.SetAutoTurn -> setAutoTurn(intent.enabled)
            is AddBatchIntent.SetAutoAiring -> setAutoAiring(intent.enabled)
            is AddBatchIntent.ApplyArchiveSchedule ->
                applyArchiveSchedule(intent.sourceId, intent.source)

            AddBatchIntent.ResetSchedule -> resetSchedule()
            is AddBatchIntent.ImportFile -> importFile(intent.source)
            AddBatchIntent.DismissImport -> reduce { copy(importPreview = null) }
            AddBatchIntent.ClearImportError -> reduce { copy(importError = null) }
            is AddBatchIntent.ConfirmImport -> confirmImport(intent.source)
            AddBatchIntent.AddReminder -> addReminder()
            is AddBatchIntent.RemoveReminder -> removeReminder(intent.index)
            is AddBatchIntent.UpdateReminder ->
                updateReminder(intent.index, intent.time, intent.note)

            AddBatchIntent.Save -> save()
        }
    }

    // --- Производные поля состояния ---------------------------------------------------------

    /**
     * Пересчитывает подсказки пород. Зовётся только там, где меняются её входы: ответ
     * базы о закладках хозяйства и смена вида птицы — но не ввод в поля формы.
     */
    private fun AddBatchState.withKnownBreeds(): AddBatchState =
        copy(knownBreeds = knownBreedsOf(allBatches, form.type))

    /**
     * Пересчитывает цвета расхождения по текущей таблице, дате и соседям.
     *
     * Вызывается в каждой ветке, где меняется один из трёх входов; ветка, которая
     * правит поля первой страницы, зовёт его только на смене даты — остальные поля к
     * расписанию отношения не имеют, а перебирать соседей на каждую букву названия
     * незачем.
     */
    private fun AddBatchState.withOverlap(): AddBatchState {
        val verdicts = if (isEditing) emptyList()
        else overlapVerdicts(schedule, parseDate(form.data), neighbours, temperatureUnit)
        return copy(overlap = verdicts, overlapShown = verdicts.any { it?.tinted == true })
    }

    /**
     * Режим по умолчанию для текущего вида, с «Авто» там, где его ставит инкубатор.
     *
     * Снимок [AddBatchState.scheduleBase] делается до `setAutoIncubator`, и это не
     * перестраховка: тот правит [Value] на месте — поля модели `var`, — а
     * `toValueUiState` создаёт новые строки, и после него стирать уже нечего.
     */
    private fun AddBatchState.withRegeneratedSchedule(): AddBatchState {
        val unit = temperatureUnit
        val source = catalog.schedule(form.type)
        val base = source.map { it.toValueUiState(unit) }
        val generated = setAutoIncubator(source, form.airing, form.over)
        return copy(
            schedule = generated.map { it.toValueUiState(unit) },
            scheduleBase = base,
            scheduleReady = true,
            // Таблица снова из :domain — сводка об архивной закладке или о файле к ней
            // больше не относится. Сюда приходят и «По умолчанию», и смена вида.
            scheduleOrigin = null,
        ).withOverlap()
    }

    /**
     * Приводит два столбца таблицы к флагам: включено — пусто, выключено — норма из
     * [AddBatchState.scheduleBase].
     *
     * Список пересобирается целиком: он иммутабельный, и правка «на месте» означала бы
     * правку того же самого списка, который читает композиция.
     */
    private fun AddBatchState.withAutoApplied(): AddBatchState = copy(
        schedule = schedule.mapIndexed { index, row ->
            val base = scheduleBase.getOrNull(index)
            row.copy(
                over = if (form.over) "" else base?.over ?: row.over,
                airingCount = if (form.airing) "" else base?.airingCount ?: row.airingCount,
                airingTime = if (form.airing) "" else base?.airingTime ?: row.airingTime,
            )
        }
    )

    // --- Загрузка ----------------------------------------------------------------------------

    private fun load(incubatorId: Long, batchId: Long) {
        val editing = batchId != 0L
        loadJob?.cancel()
        neighboursJob?.cancel()

        // Состояние собирается заново, но каталог и закладки хозяйства переносятся как
        // есть: их подписки живут с ViewModel, а не с формой, и обнулённые здесь они
        // ждали бы следующего ответа базы — сетка видов успела бы остаться без своих.
        reduce {
            AddBatchState(
                form = if (editing) {
                    BatchUiState(incubatorId = incubatorId)
                } else {
                    BatchUiState(
                        type = DEFAULT_SPECIES,
                        data = todayText(),
                        time = clockText(),
                        eggAll = "",
                        eggAllEND = "0",
                        arhive = "0",
                        incubatorId = incubatorId,
                    )
                },
                isEditing = editing,
                loading = editing,
                catalog = catalog,
                allBatches = allBatches,
                reminders = if (editing) {
                    emptyList()
                } else {
                    listOf(Time(id = 0, time = "08:00", idPT = 0))
                },
            ).withKnownBreeds()
        }

        shownPower = null
        if (editing) {
            loadJob = viewModelScope.launch {
                val batch = itemsRepository.getBatch(batchId).filterNotNull().first()
                val times = itemsRepository.getTimeList(batchId)
                // Через getBatchValues, а не getValueArchive: там есть ORDER BY day, а
                // таблица читается сверху вниз по дням.
                val days = itemsRepository.getBatchValues(batchId).first()
                    .map { it.toValueUiState(temperatureUnit) }
                // Каталог читается здесь, а не берётся из состояния: в первые кадры формы
                // там только встроенные виды, и у своего вида не нашлось бы ни срока, ни
                // дней овоскопирования.
                val candlings = candlingEditRows(
                    batch = batch,
                    catalog = SpeciesCatalog(itemsRepository.getCustomSpecies().first()),
                    saved = itemsRepository.getCandlings(batchId).first(),
                    now = Date(),
                )
                // Пустые поля света у закладки значат «как у инкубатора» — так считают и
                // «Финансы», — и форма показывает именно эти значения, а не пустоту:
                // закладки, заложенные до появления полей, иначе выглядели бы без света.
                val device = itemsRepository.getIncubator(batch.incubatorId).first()
                val power = batch.power.over(device?.power ?: PowerSettings())
                // Что из этого своё у закладки и что форма покажет — чтобы при сохранении
                // отличить нетронутые поля от правки (`save`). Сравнивается то, что форма
                // вернёт, а не `power`: состояние полей округляет и нормализует.
                shownPower = batch.power to power.toFormState().toSettings()
                reduce {
                    copy(
                        form = batch.toBatchUiState(candlings.rejectedSum())
                            .copy(power = power.toFormState())
                            .withBalancedCull(),
                        candlings = candlings,
                        loadedReminders = times,
                        reminders = times.map { it.copy() },
                        schedule = days,
                        scheduleReady = true,
                        loading = false,
                    ).withKnownBreeds()
                }
            }
            return
        }

        reduce { withRegeneratedSchedule() }

        loadJob = viewModelScope.launch {
            // Автоматика закладки — это то, что умеет само устройство, и форма берёт
            // его ответ как значение по умолчанию: инкубатор с автопереворотом
            // закладывают с автопереворотом, а два переключателя в форме дают сказать
            // «в этот раз переворачиваю сам». Инкубатора может не быть — форму
            // открыли, а устройство удалили: тогда закладка остаётся с флагами по
            // умолчанию, то есть выключенными.
            val incubator = itemsRepository.getIncubator(incubatorId).first() ?: return@launch
            reduce {
                // Вместимость — под полосу заполнения; её человек не трогает, и она
                // ложится в состояние в любом случае.
                val sized = copy(
                    capacity = incubator.capacity,
                    // Свет — так же, как автоматика: значения устройства по умолчанию,
                    // если человек ещё ничего не вписал сам.
                    form = if (form.power.isBlank) {
                        form.copy(power = incubator.power.toFormState())
                    } else form,
                )
                // База отвечает не мгновенно, и человек мог успеть нажать раньше: его
                // выбор старше ответа устройства, иначе переключатель отскакивал бы назад.
                if (autoTouched) return@reduce sized
                val next = sized.copy(
                    // От `sized`, а не от `form`: тот ещё без света устройства, и
                    // собранная из него форма стирала только что подставленные
                    // потребление и тариф — закладка сохранялась с пустыми полями.
                    form = sized.form.copy(airing = incubator.autoAiring, over = incubator.autoTurn),
                )
                // Флаги приходят уже после того, как таблица нарисована по умолчанию:
                // стирает нормы в столбцах поворота и проветривания setAutoIncubator, а
                // знать о нём до ответа базы неоткуда. Переписывать таблицу зря нельзя —
                // при выключенной автоматике стирать нечего, а правки, успевшие лечь в
                // неё, пропали бы.
                if (incubator.autoAiring || incubator.autoTurn) next.withAutoApplied() else next
            }
        }
        refreshArchive(DEFAULT_SPECIES)
        watchNeighbours(incubatorId)
    }

    /**
     * Соседи по воздуху: идущие закладки этого инкубатора и их дни, потоком.
     *
     * Потоком, а не снимком, по той же причине, что и в шторке замеров инкубатора:
     * пока форма открыта, соседнюю закладку могут завершить с другого экрана, и цвета
     * с её дней должны сойти. По подписке на дни на каждую идущую закладку — их
     * единицы, а `flatMapLatest` пересобирает их только когда меняется сам список.
     * Ответ ложится в состояние с пересчётом цветов: таблица к этому моменту, как
     * правило, уже нарисована, и ждать следующей правки строки ей незачем.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchNeighbours(incubatorId: Long) {
        neighboursJob = viewModelScope.launch {
            itemsRepository.getBatchesFor(incubatorId)
                .map { batches -> batches.filter { it.status == BatchStatus.Active } }
                // Различаются по тому, что влияет на ответ, — состав и даты закладки:
                // переименование соседа или правка его цены меняет список, а
                // пересобирать по ней подписки на дни незачем.
                .distinctUntilChanged { a, b -> a.map { it.id to it.data } == b.map { it.id to it.data } }
                .flatMapLatest { active ->
                    if (active.isEmpty()) flowOf(emptyList())
                    else combine(
                        active.map { batch ->
                            itemsRepository.getBatchValues(batch.id)
                                .map { NeighbourSchedule(batch, it) }
                        }
                    ) { it.toList() }
                }
                .distinctUntilChanged()
                .collect { neighbours ->
                    // Та же сверка, что в refreshArchive: ответ ложится только под тот
                    // инкубатор, о котором спрашивали.
                    reduce {
                        if (form.incubatorId != incubatorId) this
                        else copy(
                            neighbours = neighbours,
                            occupiedEggs = neighbours.sumOf { it.batch.eggAll },
                        ).withOverlap()
                    }
                }
        }
    }

    /**
     * Читает архив завершённых закладок этого вида — по одному чтению на вид.
     *
     * Прежний запрос отменяется, а ответ ложится только под тот вид, что стоит в форме
     * сейчас: два быстрых переключения плитки или переоткрытая форма иначе могли бы
     * положить в список утиные закладки под курицу — ответы базы приходят не в
     * порядке вопросов.
     */
    private fun refreshArchive(type: String) {
        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            val options = itemsRepository.getArchivedBatches(type).map { batch ->
                ArchiveOption(batch, itemsRepository.countMeasurements(batch.id))
            }
            reduce { if (form.type == type) copy(archiveOptions = options) else this }
        }
    }

    // --- Поля формы --------------------------------------------------------------------------

    /**
     * Вид птицы при правке не меняется, и отказ живёт здесь, а не только в форме — та же
     * причина, что у [setAutoTurn]: композиция может пережить состояние, из которого
     * была построена. Яйца уже в лотке, и они той птицы, какой были; расписание
     * сгенерировано под неё и на его днях висят замеры, а срок у другой птицы другой —
     * закладка с 21 строкой куриного режима под 28-дневным утиным сроком показывала бы
     * «День 7/28» и не имела бы строк для дней с 22-го по 28-й. Пересоздать таблицу
     * нельзя по правилу [save] — идентификаторы дней держат замеры. Ошибка в виде
     * поправляется удалением закладки и новой закладкой.
     *
     * У завершённой закладки так же заперты дата и время закладки: инкубация прожита, от
     * них отсчитаны её дни и итог, и сдвиг задним числом переставил бы замеры по чужим
     * дням календаря.
     */
    private fun updateForm(requested: BatchUiState) {
        val previous = current
        val updated = when {
            !previous.isEditing -> requested
            previous.form.finished -> requested.copy(
                type = previous.form.type,
                data = previous.form.data,
                time = previous.form.time,
                candlingRejected = previous.form.candlingRejected,
            ).withBalancedCull(clampHatch = requested.eggAllEND != previous.form.eggAllEND)
            else -> requested.copy(type = previous.form.type)
        }
        val speciesChanged = updated.type != previous.form.type
        val dateChanged = updated.data != previous.form.data
        reduce {
            val next = copy(form = updated)
                .let { if (speciesChanged) it.withKnownBreeds() else it }
            // Смена вида переписывает таблицу целиком: у гуся и перепела разное число
            // дней, и сохранять правки от прежнего вида было бы не бережностью, а
            // мусором в чужом режиме. При правке вид не меняется (выше), и таблица
            // остаётся той, что сгенерирована при закладке.
            when {
                speciesChanged -> next.withRegeneratedSchedule()
                // Дата двигает всю таблицу по календарю: день 1 ложится на другой день
                // соседей, и цвета надо считать заново. Перегенерация делает это сама.
                dateChanged -> next.withOverlap()
                else -> next
            }
        }
        if (speciesChanged) refreshArchive(updated.type)
    }

    /** Открывает диалог «Отбраковано яиц» с тем, что сейчас стоит в форме. */
    private fun openRejected() {
        reduce {
            if (!isEditing) this
            else copy(rejectedDraft = RejectedDraft(form.eggRejected, candlings))
        }
    }

    /**
     * Правка поля диалога — ручной отбраковки или одного овоскопирования ([index]
     * `null` — ручная). Каждое поле зажимается остатком: заложенные минус всё, что
     * набрано в остальных, — так итог внизу диалога не перерастёт «Количество яиц», в
     * каком бы порядке ни исправляли числа.
     */
    private fun updateRejectedDraft(index: Int?, input: String) {
        reduce {
            val draft = rejectedDraft ?: return@reduce this
            val hatched = form.fixedHatch
            // У закладки с вписанными птенцами главные — птенцы: вся отбраковка равна
            // «заложено − вывелось», ручная часть — остаток после овоскопирований, и
            // править её руками нечего.
            if (hatched != null) {
                if (index == null || index !in draft.candlings.indices) return@reduce this
                val pool = (form.eggCount - hatched).coerceAtLeast(0)
                val others = draft.candlings.filterIndexed { i, _ -> i != index }.rejectedSum()
                val candlings = draft.candlings.toMutableList().also {
                    it[index] = it[index].copy(rejected = clampCount(input, (pool - others).coerceAtLeast(0)))
                }
                val manual = (pool - candlings.rejectedSum()).coerceAtLeast(0)
                return@reduce copy(
                    rejectedDraft = draft.copy(
                        manual = if (manual == 0) "" else manual.toString(),
                        candlings = candlings,
                    ),
                )
            }
            val eggs = form.eggCount
            val next = if (index == null) {
                draft.copy(manual = clampCount(input, (eggs - draft.candlingSum).coerceAtLeast(0)))
            } else {
                if (index !in draft.candlings.indices) return@reduce this
                val others = draft.candlings.filterIndexed { i, _ -> i != index }.rejectedSum()
                val limit = (eggs - draft.manualCount - others).coerceAtLeast(0)
                draft.copy(
                    candlings = draft.candlings.toMutableList().also {
                        it[index] = it[index].copy(rejected = clampCount(input, limit))
                    },
                )
            }
            copy(rejectedDraft = next)
        }
    }

    /**
     * «Готово» в диалоге: набранное становится формой. В базу оно уходит вместе с
     * остальной формой по «Сохранить» — до тех пор закрытая без сохранения форма не
     * меняет ничего.
     */
    private fun confirmRejected() {
        reduce {
            val draft = rejectedDraft ?: return@reduce this
            copy(
                rejectedDraft = null,
                candlings = draft.candlings,
                form = form.copy(
                    eggRejected = draft.manual,
                    candlingRejected = draft.candlingSum,
                    // Вывод не зажимается и здесь: лишний держит `isValid` (`hatchFits`), а
                    // молча урезать его по «Готово» в соседнем диалоге — хуже отказа.
                ).withBalancedCull(clampHatch = false),
            )
        }
    }

    /**
     * Свой вид, только что сохранённый из этой формы, становится выбранным сразу.
     *
     * В каталог он подставляется руками, не дожидаясь потока из базы: [updateForm] тут же
     * перепишет таблицу по новому виду, а поток ответит на кадр-другой позже — и таблица
     * успела бы нарисоваться по запасному режиму «неизвестного» вида. Ответ базы затем
     * заменит каталог на такой же, только прочитанный.
     */
    private fun selectNewSpecies(species: CustomSpecies) {
        // При правке вид заперт (см. [updateForm]), и двери «Свой вид» в форме нет.
        if (current.isEditing) return
        reduce {
            copy(catalog = SpeciesCatalog(catalog.custom.filter { it.id != species.id } + species))
        }
        updateForm(current.form.copy(type = species.name))
    }

    // --- Породы ------------------------------------------------------------------------------

    private fun updateBreed(index: Int, row: BreedUiState) {
        reduce {
            if (index !in form.breeds.indices) return@reduce this
            copy(form = form.copy(breeds = form.breeds.toMutableList().also { it[index] = row }))
        }
    }

    /**
     * Вторая строка делит лоток по породам — на **отдельные закладки** при сохранении
     * ([BatchUiState.toBatches]): с этого момента яйца и цена вводятся у каждой строки, а
     * поля закладки показывают их сумму.
     *
     * Всё, что уже набрано в тех двух полях, переезжает в первую строку: это числа про
     * те яйца, что уже в форме, и терять их на ровном месте незачем — до нажатия они
     * относились ко всему лотку, а лоток пока и состоит из одной породы.
     *
     * **При правке строки не добавляются.** У закладки одна порода, и другую породу
     * заводят другой закладкой: у этой уже есть свои яйца, свой режим и свои замеры, и
     * делить их надвое задним числом нельзя. Форма кнопки при правке не показывает,
     * а отказ здесь — на случай, если композиция пережила состояние.
     */
    private fun addBreed() {
        reduce {
            if (isEditing) return@reduce this
            val rows = form.breeds
            val seeded = if (rows.size == 1) {
                listOf(
                    rows.single().copy(
                        eggs = form.eggAll,
                        price = form.price,
                        pricePerEgg = form.pricePerEgg,
                    ),
                )
            } else {
                rows
            }
            copy(form = form.copy(breeds = seeded + BreedUiState()))
        }
    }

    /**
     * Обратный переход: оставшись одна, порода отдаёт свои числа полям закладки — иначе
     * в них осталась бы сумма, из которой только что вычли строку.
     */
    private fun removeBreed(index: Int) {
        reduce {
            val rows = form.breeds.toMutableList()
            if (index !in rows.indices || rows.size < 2) return@reduce this
            rows.removeAt(index)
            if (rows.size == 1) {
                val kept = rows.single()
                copy(
                    form = form.copy(
                        breeds = rows,
                        eggAll = kept.eggs,
                        price = kept.price,
                        pricePerEgg = kept.pricePerEgg,
                    ),
                )
            } else {
                copy(form = form.copy(breeds = rows))
            }
        }
    }

    // --- Расписание --------------------------------------------------------------------------

    private fun updateScheduleRow(index: Int, row: ValueUiState) {
        reduce {
            if (index !in schedule.indices) return@reduce this
            // Цвета считаются тут же, на каждый символ: это и есть «динамически» —
            // клетка зеленеет или краснеет, пока в неё набирают. Обход соседей на
            // один день дешёв, а ждать ухода фокуса значило бы красить с опозданием.
            copy(schedule = schedule.toMutableList().also { it[index] = row }).withOverlap()
        }
    }

    /**
     * «Автопереворот» и «Автопроветривание» в форме закладки.
     *
     * Значение приходит от инкубатора ([load]), но остаётся выбором на эту закладку:
     * устройство умеет переворачивать само — это его свойство, а перевернёт ли оно
     * именно этот лоток, решает тот, кто его закладывает. Выключенное возвращает в
     * таблицу нормы по дням, включённое их стирает — `Batch.over` / `Batch.airing`
     * значат ровно «нормы нет, её держит инкубатор», и флаг, разошедшийся со своими
     * столбцами, обещал бы в шторке «на автомате» над живыми цифрами.
     *
     * При правке не работает и не показывается: столбцы уже прожиты закладкой, на её
     * днях висят замеры, и стереть или вернуть норму задним числом значит переписать
     * не план, а то, по чему человек уже работал. Отказ живёт здесь, а не в форме:
     * композиция может пережить то состояние, из которого была построена.
     */
    private fun setAutoTurn(enabled: Boolean) {
        reduce {
            if (isEditing) return@reduce this
            copy(autoTouched = true, form = form.copy(over = enabled)).withAutoApplied()
        }
    }

    private fun setAutoAiring(enabled: Boolean) {
        reduce {
            if (isEditing) return@reduce this
            copy(autoTouched = true, form = form.copy(airing = enabled)).withAutoApplied()
        }
    }

    /**
     * Подставляет в таблицу режим завершённой закладки того же вида.
     *
     * Брать можно двумя способами ([ArchiveScheduleSource]). [ArchiveScheduleSource.Plan] —
     * план той закладки как есть. [ArchiveScheduleSource.Fact] — среднее по её замерам:
     * у того, кто эту птицу уже выводил, выверенный режим лежит не в плане, который он
     * однажды сгенерировал и мог ни разу не поправить, а в показаниях, которые он снимал
     * изо дня в день. Считает это [averagedScheduleOf]; дни и столбцы, по которым замеров
     * не было, остаются планом-источником, так что таблица приходит заполненной целиком.
     *
     * Идентификаторы обнуляются: строки чужие, при сохранении они станут новыми.
     * Сортировка своя — запрос архива идёт без `ORDER BY`, а разложить замеры по дням
     * без порядка по дню тоже нельзя.
     */
    private fun applyArchiveSchedule(sourceId: Long, source: ArchiveScheduleSource) {
        // Только при создании: строки архива приходят без идентификаторов, а правка
        // обновляет существующие по ним, и обнулённый идентификатор молча потерял бы всё.
        if (current.isEditing) return
        viewModelScope.launch {
            val archived = itemsRepository.getValueArchive(sourceId).sortedBy { it.day }
            if (archived.isEmpty()) return@launch
            val rows = when (source) {
                ArchiveScheduleSource.Plan -> archived
                // Идентификаторы дней-источников ещё нужны: замер знает свой день
                // только через Value.id, и обнулять их можно лишь после усреднения.
                ArchiveScheduleSource.Fact -> averagedScheduleOf(
                    archived,
                    itemsRepository.getBatchMeasurements(sourceId).first(),
                )
            }
            val taken = rows.map { it.toValueUiState(temperatureUnit).copy(id = 0, idPT = 0) }
            reduce {
                // Взятый режим — новый источник: к нему вернутся столбцы, если автоматику
                // отжать. А сама автоматика проходит по нему тут же: включённый
                // «Автопереворот» над колонкой чужих переворотов — это флаг, который
                // разошёлся со своей таблицей.
                copy(
                    schedule = taken,
                    scheduleBase = taken,
                    // Запоминаем, чей это теперь режим: страница расписания говорит об
                    // этом вместо предупреждения о справочных цифрах — предупреждение
                    // относится к режиму из :domain, а этот пришёл из живой закладки.
                    scheduleOrigin = archiveOptions.firstOrNull { it.batch.id == sourceId }
                        ?.let { ScheduleOrigin.Archive(AppliedArchive(it, source)) },
                ).withAutoApplied().withOverlap()
            }
        }
    }

    private fun resetSchedule() {
        reduce { if (isEditing) this else withRegeneratedSchedule() }
    }

    // --- Расписание из файла ---------------------------------------------------------------

    /**
     * Читает файл расписания и выставляет [AddBatchState.importPreview] — диалог с тем,
     * что в нём, и с тем, что импорт изменит. Сам в таблицу ничего не пишет: файл могли
     * выбрать не тот, и об этом человек узнаёт из диалога, а не из переписанной таблицы.
     *
     * Только при создании: правка не подменяет ни вид, ни дни — см. [applyArchiveSchedule].
     */
    private fun importFile(source: Uri) {
        val snapshot = current
        if (snapshot.isEditing || snapshot.importing) return
        reduce { copy(importing = true) }
        viewModelScope.launch {
            try {
                val export = scheduleTransfer.read(source)
                // Каталог читается заново, а не из состояния: там он — ответ потока,
                // и в первые кадры формы это одни встроенные виды. Свой вид, которого в
                // нём ещё нет, диалог обещал бы завести, а база отказала бы дубликату.
                val catalog = SpeciesCatalog(itemsRepository.getCustomSpecies().first())
                val known = catalog.resolveName(export.type)
                reduce {
                    copy(
                        importPreview = ImportPreview(
                            export = export,
                            currentType = form.type,
                            resolvedType = known ?: export.type,
                            creatingSpecies = known == null,
                        ),
                    )
                }
            } catch (e: ScheduleFileException) {
                reduce { copy(importError = e.message) }
            } catch (e: CancellationException) {
                // Отмена — не ошибка чтения: форму закрыли, и сообщать ей больше нечего.
                throw e
            } catch (e: Exception) {
                reduce {
                    copy(importError = "Не удалось прочитать файл: ${e.message ?: "неизвестная ошибка"}")
                }
            } finally {
                reduce { copy(importing = false) }
            }
        }
    }

    /**
     * Подставляет в таблицу расписание из файла [AddBatchState.importPreview] — и только
     * его: вид закладки становится видом файла (расписание принадлежит птице), а всё
     * остальное в форме — название, породы, яйца, цена, дата, напоминания, автоматика —
     * не трогается.
     *
     * [source] — план той закладки или среднее по её замерам, тот же выбор, что у
     * [applyArchiveSchedule], и по той же причине: выверенный режим у того, кто птицу
     * уже выводил, лежит в показаниях, а не в плане. Файл без замеров даёт только план,
     * и просьба о среднем тогда честно сводится к нему (диалог такую строку и не даёт
     * выбрать).
     *
     * Вид, которого у получателя нет, сперва заводится как свой — из описания в файле
     * или, когда его нет, из плана ([ScheduleExport.speciesToCreate]). Это единственная
     * запись в базу, которую делает импорт, и делается она до того, как форма примет
     * вид: [selectNewSpecies] должен положить вид в каталог, иначе таблица успела бы
     * нарисоваться по запасному режиму «неизвестного» вида.
     *
     * Взятое расписание — новый [AddBatchState.scheduleBase], и автоматика формы
     * проходит по нему тут же, как и по режиму из архива: включённый «Автопереворот»
     * над колонкой чужих поворотов — это флаг, разошедшийся со своей таблицей.
     */
    private fun confirmImport(source: ArchiveScheduleSource) {
        val preview = current.importPreview ?: return
        reduce { copy(importPreview = null) }
        if (current.isEditing) return
        val export = preview.export
        viewModelScope.launch {
            if (preview.creatingSpecies) {
                val species = export.speciesToCreate()
                val id = try {
                    itemsRepository.saveCustomSpecies(species)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Имя успели занять между чтением файла и нажатием — или база
                    // отказала по другой причине. Таблица не тронута, и так и говорим.
                    reduce {
                        copy(
                            importError = "Не удалось добавить вид «${species.name}»: " +
                                "${e.message ?: "неизвестная ошибка"}. Расписание не изменено.",
                        )
                    }
                    return@launch
                }
                selectNewSpecies(species.copy(id = id))
            } else if (preview.resolvedType != current.form.type) {
                updateForm(current.form.copy(type = preview.resolvedType))
            }

            // Файл без замеров даёт только план, и просьба о среднем честно сводится к нему:
            // выбор выводится из того, что нашлось, а не наоборот.
            val fact = export.fact?.takeIf { source == ArchiveScheduleSource.Fact }
            val chosen = if (fact != null) ArchiveScheduleSource.Fact else ArchiveScheduleSource.Plan
            val rows = fact ?: export.plan
            val taken = rows.sortedBy { it.day }
                .map { it.toValueUiState(temperatureUnit).copy(id = 0, idPT = 0) }
            reduce {
                copy(
                    schedule = taken,
                    scheduleBase = taken,
                    scheduleReady = true,
                    scheduleOrigin = ScheduleOrigin.File(export, chosen),
                ).withAutoApplied().withOverlap()
            }
        }
    }

    /**
     * Имя вида из файла, каким его знает этот каталог, — или `null`, если не знает.
     *
     * Без учёта регистра и краёв, как [SpeciesCatalog.isNameTaken]: «цесарки» в файле
     * и «Цесарки» в каталоге — одна птица, и заводить вторую база всё равно не даст.
     * Возвращается имя из каталога, а не из файла: закладка ссылается на вид по имени
     * дословно.
     */
    private fun SpeciesCatalog.resolveName(type: String): String? {
        val wanted = type.trim()
        return custom.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.name
            ?: SpeciesCatalog.BUILT_IN.firstOrNull { it.equals(wanted, ignoreCase = true) }
    }

    // --- Напоминания -------------------------------------------------------------------------
    //
    // У завершённой закладки напоминаний в форме нет, и правки их отклоняются здесь же:
    // будить больше не о чем, а строки `Batch_time` остаются как были — по ним
    // «Вернуть в инкубацию» вернёт напоминания.

    private fun addReminder() {
        reduce {
            if (isEditing && form.finished) return@reduce this
            copy(reminders = reminders + Time(id = 0, time = "08:00", idPT = 0))
        }
    }

    private fun removeReminder(index: Int) {
        reduce {
            if (isEditing && form.finished) return@reduce this
            if (index !in reminders.indices) return@reduce this
            copy(reminders = reminders.toMutableList().also { it.removeAt(index) })
        }
    }

    private fun updateReminder(index: Int, time: String?, note: String?) {
        reduce {
            if (isEditing && form.finished) return@reduce this
            if (index !in reminders.indices) return@reduce this
            val updated = reminders.toMutableList()
            val row = updated[index]
            updated[index] = row.copy(
                time = time ?: row.time,
                note = note ?: row.note,
            )
            copy(reminders = updated)
        }
    }

    // --- Сохранение --------------------------------------------------------------------------

    /**
     * Создаёт закладку вместе с расписанием по дням, напоминаниями и чипом вида —
     * либо, в режиме правки, обновляет её, перевыставляет напоминания и переписывает
     * дни.
     *
     * Дни при правке именно **обновляются**, а не пересоздаются: на их
     * идентификаторах висят замеры, и пересоздание оторвало бы факт от плана.
     *
     * Породы при создании — **отдельные закладки** ([BatchUiState.toBatches]), и каждая
     * кладётся своей транзакцией с собственной копией расписания и напоминаний: дни и
     * времена — строки закладки, и общих у двух закладок быть не может, замеры висят на
     * днях. Одна транзакция на все закладки не нужна: каждая из них целая сама по себе,
     * и смерть процесса между двумя оставит одну заложенную закладку, а не половину.
     */
    private fun save() {
        viewModelScope.launch {
            val snapshot = current
            val form = snapshot.form
            val days = snapshot.schedule.map { it.toValue(temperatureUnit) }

            if (snapshot.isEditing) {
                // Свет, показанный из инкубатора поверх пустых полей закладки и никем не
                // тронутый, остаётся пустым — по частям, мощность и тариф порознь: иначе
                // сохранение формы записало бы в закладку тарифы инкубатора, и новая цена
                // в инкубаторе её бы уже не коснулась.
                val shown = shownPower
                val batch = form.toBatch().let {
                    if (shown == null) it
                    else it.copy(power = it.power.withoutInherited(own = shown.first, shown = shown.second))
                }
                // Записи овоскопирований — в одной транзакции с закладкой: итог
                // «Отбраковано яиц», подтверждённый в диалоге, — это её `eggRejected` и их
                // сумма вместе, и одно без другого его бы сдвинуло. Пишутся только
                // изменённые строки (`candlingWrites`).
                val writes = candlingWrites(batch.id, snapshot.candlings, todayText())
                if (writes.isEmpty) {
                    itemsRepository.updateBatch(batch)
                } else {
                    itemsRepository.updateBatchWithCandlings(batch, writes.save, writes.delete)
                }
                itemsRepository.updateSchedule(days)
                rewriteReminders(batch, snapshot)
                sendEffect(AddBatchEffect.Saved(listOf(form.id)))
                return@launch
            }

            // Строка, чип вида, все дни и все времена — одной транзакцией: закладка с
            // половиной расписания хуже, чем несохранённая, потому что выглядит целой.
            // `idPT` расставляет сама транзакция, ей одной он и известен вовремя.
            val ids = form.toBatches().map { batch ->
                itemsRepository.insertBatchWithSchedule(
                    batch = batch,
                    species = Species(species = form.type, idPT = 0),
                    days = days,
                    times = snapshot.reminders,
                )
            }
            // Расписание пересчитывается по базе: времена уже записаны транзакциями выше.
            workRepository.refreshReminders()

            sendEffect(AddBatchEffect.Saved(ids, calendarOfferOf(snapshot)))
        }
    }

    /**
     * Важные даты новой закладки для календаря — из того, что форма уже держит: даты,
     * вида и плана по дням.
     *
     * Перевороты берутся из таблицы, какой её сохранят, а под автоповоротом — из
     * [AddBatchState.scheduleBase]: инкубатор, который переворачивает сам, тоже надо
     * выключить в день перекладки на вывод, а стёртый автоматикой столбец этого дня
     * не знает.
     */
    private fun calendarOfferOf(snapshot: AddBatchState): CalendarOffer? {
        val form = snapshot.form
        val term = snapshot.catalog.incubationDays(form.type) ?: return null
        val start = parseDate(form.data)
            ?.let { it.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() }
            ?: return null
        val plan = (if (form.over) snapshot.scheduleBase else snapshot.schedule).sortedBy { it.day }
        // День прекращения считается по позиции строки, поэтому только для плана без
        // пропусков: при дыре в днях дата съехала бы, а лучше не дать её вовсе.
        val contiguous = plan.withIndex().all { (index, row) -> row.day == index + 1 }
        val dates = batchCalendarDates(
            start = start,
            term = term,
            candlingStage = { day -> snapshot.catalog.candlingStage(form.type, day) },
            plannedTurns = if (contiguous) plan.map { it.over.trim().toIntOrNull() } else emptyList(),
            today = LocalDate.now(),
        )
        if (dates.isEmpty()) return null
        return CalendarOffer(
            title = form.title.trim(),
            species = form.type,
            term = term,
            dates = dates,
        )
    }

    /**
     * Напоминания перезаписываются целиком: строк мало, а сравнивать их построчно
     * пришлось бы по времени и тексту сразу.
     *
     * **Завершённой закладке напоминания не ставятся**, и следит за этим не эта функция.
     * Строки времён остаются в базе — их нужно сохранить: закладку могут вернуть в
     * работу, и будильники восстанавливаются оттуда же. А звенеть или молчать, решает
     * расписание, которое считается по базе: завершённая закладка в него не попадает
     * (`plannedReminders`).
     */
    private suspend fun rewriteReminders(batch: Batch, snapshot: AddBatchState) {
        snapshot.loadedReminders.forEach { itemsRepository.deleteTime(it) }

        val fresh = snapshot.reminders.map { it.copy(id = 0, idPT = batch.id) }
        fresh.forEach { itemsRepository.insertTime(it) }
        // Записанные строки становятся «загруженными»: второе сохранение той же формы
        // должно удалить именно их, а не те, что были при открытии и уже удалены.
        // Перечитываются из базы, а не берутся из `fresh`: `insertTime` не возвращает
        // идентификаторов, и строки с нулевым `id` удалить было бы нельзя — второе
        // нажатие до закрытия шторки удвоило бы напоминания.
        val written = itemsRepository.getTimeList(batch.id)
        reduce { copy(loadedReminders = written) }

        workRepository.refreshReminders()
    }

    companion object {
        const val DEFAULT_SPECIES = "Курицы"
    }
}
