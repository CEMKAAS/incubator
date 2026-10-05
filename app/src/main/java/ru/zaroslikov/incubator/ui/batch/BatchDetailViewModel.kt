package ru.zaroslikov.incubator.ui.batch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.airing.AiringTimerController
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.airing.AiringTimerTarget
import ru.zaroslikov.incubator.airing.settled
import ru.zaroslikov.incubator.analytics.Analytics
import ru.zaroslikov.incubator.analytics.Events
import ru.zaroslikov.incubator.analytics.reportIncubationOutcomes
import ru.zaroslikov.incubator.domain.incubation.SpeciesCatalog
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.HatchOutcome
import ru.zaroslikov.incubator.domain.model.Candling
import ru.zaroslikov.incubator.domain.model.Measurement
import ru.zaroslikov.incubator.domain.model.PowerSettings
import ru.zaroslikov.incubator.domain.model.Value
import ru.zaroslikov.incubator.domain.model.finishedOnTime
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.model.stoppedEarly
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import ru.zaroslikov.incubator.domain.stats.ElectricityCost
import ru.zaroslikov.incubator.domain.stats.HatchSummary
import ru.zaroslikov.incubator.domain.stats.hatchSummaryOf
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.ui.clockText
import ru.zaroslikov.incubator.ui.incubator.batchFinishMoment
import ru.zaroslikov.incubator.ui.incubator.batchRunHours
import ru.zaroslikov.incubator.ui.incubator.electricityOfFinished
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.parseDate
import ru.zaroslikov.incubator.ui.plusDays
import ru.zaroslikov.incubator.ui.today
import java.util.Date

/**
 * Сводка закладки — та её часть состояния, которая описывает саму закладку: макет
 * [14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893).
 *
 * [plannedToday] и [plannedTomorrow] — строки расписания ([Value]), то есть план;
 * фактические показания лежат рядом, в [BatchDetailState.measurements].
 *
 * [plannedToday] заодно и есть то, к чему привязываются замеры: без строки расписания
 * на сегодня записывать замер некуда, и форма это учитывает.
 *
 * Отдельным классом внутри [BatchDetailState], а не растворённой в нём горстью полей:
 * ровно эту сводку — и ничего больше — спрашивают диалоги завершения и параметры их
 * событий в аналитике, и им незачем видеть ни расписание, ни форму замера.
 */
@Immutable
data class BatchDetailUiState(
    val batchId: Long = 0,
    /** Инкубатор закладки — цель таймера проветривания и адрес его уведомления. */
    val incubatorId: Long = 0,
    val title: String = "",
    val type: String = "",
    val eggAll: Int = 0,
    val eggAllEND: Int = 0,
    /**
     * Отбраковка, вписанная руками в форме закладки, — та, что случилась между
     * овоскопированиями. Складывается с выбраковкой овоскопирований, а не заменяет её.
     */
    val eggRejected: Int = 0,
    /** Порода закладки — `Batch.breed`; сводка печатает её рядом с видом, пусто — не печатает. */
    val breed: String = "",
    /**
     * Примечание к закладке — `Batch.note`, как его набрали в форме. Сводка показывает
     * его под полосой чисел в две строки и разворачивает по тапу; пусто — не показывает.
     */
    val note: String = "",
    val finished: Boolean = false,
    /**
     * Завершена досрочно — та самая закладка, у которой есть `Batch.endReason`.
     *
     * Отдельное поле рядом с [finished], а не `BatchStatus`: шторка спрашивает у
     * состояния «всё ли кончилось» гораздо чаще, чем «чем именно», и `finished` уже
     * разошёлся по десятку мест. Оба флага пишутся из `Batch.status`, так что
     * `stoppedEarly && !finished` не бывает.
     */
    val stoppedEarly: Boolean = false,
    /**
     * Убрана в архив — `Batch.hidden`. Шторка ничего не запрещает по этому флагу: он
     * нужен ей ровно затем, чтобы кольцо прогресса в сводке было того же цвета, что и
     * на карточке, с которой её открыли (см. `progressRingColor`).
     */
    val hidden: Boolean = false,
    /**
     * Почему закладку сняли досрочно — `Batch.endReason`, как её записали в диалоге
     * завершения. Пусто и у идущей закладки, и у той, что дошла до срока: у первой
     * ответа ещё нет, у второй вопроса нет вовсе.
     */
    val endReason: String = "",
    val startDate: Date? = null,
    /**
     * Час закладки «ЧЧ:ММ» или пусто. С него начинается каждый день инкубации, и
     * в расписании он стоит у сегодняшнего дня: утром 29-го «сегодня» ещё 28-е «с 10:00».
     */
    val startTime: String = "",
    val hatchDate: Date? = null,
    val day: Int = 0,
    val totalDays: Int? = null,
    /**
     * Момент, в который у идущей закладки выходит срок, — `batchFinishMoment`: дата
     * вывода в час закладки. Из него сводка считает «осталось N часов»; `null` у
     * завершённой (ждать нечего) и у закладки с неизвестным сроком или датой.
     */
    val finishesAt: Date? = null,
    val plannedToday: Value? = null,
    val plannedTomorrow: Value? = null,
    /**
     * Закладка идёт на автоматике инкубатора — `Batch.over` / `Batch.airing`.
     *
     * Норма такого дня в расписании пустая (`null`), и без этих флагов счётчик не
     * отличил бы «переворачивает инкубатор» от «норму просто стёрли»: числовым
     * колонкам сказать «Авто» больше нечем.
     */
    val autoTurn: Boolean = false,
    val autoAiring: Boolean = false,
    /** Стоимость яиц, какой её ввели в форме закладки: она же и расход при завершении. */
    val price: Int = 0,
    val pricePerEgg: Boolean = true,
    /**
     * Свет закладки — как его считают «Финансы» ([electricityOfFinished]): от момента
     * закладки до конца (у идущей — до момента чтения), общие часы поделены с соседками
     * по инкубатору. `null` — считать не из чего (нет потребления или тарифа) либо ещё
     * не посчитано, что различает [electricityLoaded].
     */
    val electricity: ElectricityCost? = null,
    val electricityLoaded: Boolean = false,
    /**
     * Сколько часов закладка проработала — к моменту чтения у идущей, до выключения у
     * завершённой ([batchRunHours]). `null` — промежуток не разобрать.
     */
    val runHours: Int? = null,
    /**
     * Окно ночного тарифа закладки «ЧЧ:ММ» — начало и конец, как их ввели в инкубаторе или
     * в закладке (`PowerSettings.over`); `null` — тариф однозонный.
     */
    val nightWindow: Pair<String, String>? = null,
    /**
     * Виды птицы, встроенные и свои, — через него шторка узнаёт срок и дни
     * овоскопирования. Часть состояния, а не поле ViewModel: и «пора завершать», и
     * «какое по счёту овоскопирование» — производные от закладки и её вида, а вид
     * может оказаться своим, описанным в базе, о чём функция по одному имени не знает.
     */
    val catalog: SpeciesCatalog = SpeciesCatalog.EMPTY,
    val loaded: Boolean = false,
) {
    /** Замер записывается в день расписания; нет строки — нет и дня, к которому привязать. */
    val canRecord: Boolean get() = plannedToday != null

    /**
     * Тот же `BatchStatus`, что у карточки, собранный обратно из [finished] и
     * [stoppedEarly] — производный, как и в `:domain`, а не четвёртое хранимое поле.
     *
     * Нужен там, где шторка говорит на языке карточки, а не своём: цвет кольца
     * прогресса. Сама шторка по-прежнему спрашивает «всё ли кончилось» через
     * [finished] — трёхзначный ответ ей нужен в одном месте, а двузначный в десяти.
     */
    val status: BatchStatus
        get() = when {
            stoppedEarly -> BatchStatus.Stopped
            finished -> BatchStatus.Hatched
            else -> BatchStatus.Active
        }

    /**
     * Срок вышел или на исходе — завершать пора: кнопка внизу «Обзора» зелёная.
     * Иначе она красная, и завершение считается досрочным.
     */
    val readyToFinish: Boolean get() = catalog.canFinishIncubation(type, day)

    /**
     * Какое по счёту овоскопирование выпадает на этот день; 0 — никакого. Считает
     * каталог: у своего вида дни овоскопирования лежат в базе, а не в коде.
     */
    fun candlingStage(day: Int): Int = catalog.candlingStage(type, day)

    /** Сколько дней инкубации ещё осталось; `null` — срок для вида неизвестен. */
    val daysLeft: Int? get() = totalDays?.let { (it - day).coerceAtLeast(0) }

    /**
     * Во что обошлись яйца закладки — то, что при досрочном завершении целиком уходит
     * в расход. Ноль означает «стоимость не указывали», а не «бесплатно».
     */
    val eggsCost: Int get() = if (pricePerEgg) price * eggAll else price
}

/**
 * Состояние шторки закладки целиком — всё, что рисуют её обе страницы.
 *
 * Одним классом, а не россыпью `mutableStateOf` у ViewModel: шторка читает состояние
 * одним снимком, и тогда никакой её части не достанется половина ответа — расписание
 * нового дня при сводке прежней закладки.
 *
 * Поля [measurements], [rejectedTotal], [totals] и [candlingToday] — производные: их считают
 * в тех ветках редьюсера, где меняются их входы ([withDerived]), а не при чтении — тело
 * шторки перерисовывается на каждый введённый символ формы, и проход по расписанию и всем
 * замерам закладки ездить вместе с этим не должен.
 */
@Immutable
internal data class BatchDetailState(
    /** Сама закладка: сводка, сроки, план на сегодня и завтра. */
    val summary: BatchDetailUiState = BatchDetailUiState(),
    /**
     * Всё расписание закладки — вкладка «Расписание» в шторке.
     *
     * Держится потоком, а не разовым чтением: день правят прямо на этой вкладке, и
     * исправленный режим должен тут же смениться и в плитках «Завтра», и в цели,
     * от которой считается отклонение сегодняшнего замера.
     */
    val schedule: List<Value> = emptyList(),
    /**
     * Замеры всей закладки, разложенные по дням расписания ([Measurement.idValue]).
     *
     * Не только за сегодня: вкладка «Расписание» показывает историю и аналитику любого
     * дня, а прошедший день — тот, ради которого это и понадобилось. Одним потоком, а не
     * подпиской на каждый раскрытый день: замеров за закладку набирается сотня-другая.
     */
    val measurementsByDay: Map<Long, List<Measurement>> = emptyMap(),
    /**
     * Итоги овоскопирований закладки.
     *
     * Потоком, а не разовым чтением: с этой же шторки уходят на шторку овоскопирования
     * и возвращаются с записанной выбраковкой — сводка должна пересчитаться сама.
     */
    val candlings: List<Candling> = emptyList(),
    /** Форма замера за сегодня — страница «Обзор». */
    val form: MeasurementForm = MeasurementForm(),
    /**
     * День расписания, открытый на правку. `null` — все дни свёрнуты.
     *
     * Правка идёт в копии ([ValueUiState]), а не в самой строке: «Отмена» тогда это
     * просто не сохранять, ровно как у формы замера.
     */
    val dayEdit: ValueUiState? = null,
    /**
     * Форма замера раскрытого дня расписания — отдельная от [form].
     *
     * Двух форм не избежать: обе страницы шторки живут в пейджере одновременно, и одна
     * общая форма теряла бы недописанный замер за сегодня при переходе на расписание.
     * Сбрасывается вместе с раскрытым днём — [BatchDetailIntent.ToggleDayEdit].
     */
    val dayForm: MeasurementForm = MeasurementForm(),
    /** Замеры сегодняшнего дня — то, что показывает вкладка «Обзор». Производное. */
    val measurements: List<Measurement> = emptyList(),
    /**
     * Сколько яиц убрано из инкубатора всего: выбраковка овоскопирований плюс та, что
     * вписали руками в форме закладки ([BatchDetailUiState.eggRejected]). Производное.
     *
     * Слагаемых два, потому что убирают яйца в двух разных случаях. Овоскопирование —
     * событие со своим днём: его выбраковка ложится в `Batch_candling` строкой на день.
     * Между ними яйца тоже пропадают — треснуло, протухло, разбили, — и записать это
     * овоскопированием нельзя, его в тот день не было. От «Заложено» отнимаются обе
     * величины, поэтому одно и то же число нельзя вписать в оба места.
     */
    val rejectedTotal: Int = 0,
    /**
     * Итог по всем замерам закладки — карточка «Итог по замерам» на «Обзоре»
     * завершённой ([batchTotals]). Производное, и самое дорогое из четырёх: проход по
     * всему расписанию и всем замерам.
     */
    val totals: BatchTotals = BatchTotals(),
    /** Итог сегодняшнего овоскопирования; `null` — сегодня его ещё не записывали. Производное. */
    val candlingToday: Candling? = null,
    /**
     * Таймер проветривания — единственный на приложение, как его отдаёт
     * `AiringTimerController`. Чей он, решает [timerSlot]: минуты этой закладке отдаёт
     * только таймер, поставленный из её формы.
     */
    val airingTimer: AiringTimerState = AiringTimerState.Idle,
) {
    /** Цель таймера, поставленного из этой шторки. */
    val timerTarget: AiringTimerTarget
        get() = AiringTimerTarget(summary.incubatorId, summary.batchId)

    /** Что показать под полями формы «Замеры за сегодня». Дёшево: три поля, без проходов. */
    val timerSlot: AiringTimerSlot
        get() = AiringTimerSlot(
            state = airingTimer,
            mine = airingTimer.targetOrNull?.let { it == timerTarget } ?: true,
            planMinutes = summary.plannedToday?.airingTime,
        )
}

/**
 * Пересчитывает производные поля состояния.
 *
 * Вызывается ровно там, где меняются их входы: ответ каждого из трёх потоков базы
 * (расписание, замеры, овоскопирования), установка дня при чтении закладки, сброс перед
 * новым чтением и запись итога при завершении. Ветка «ввели символ в форму» её не
 * трогает — в этом и была причина завести поля вместо свойств с `get()`.
 */
private fun BatchDetailState.withDerived(): BatchDetailState = copy(
    measurements = summary.plannedToday?.let { measurementsByDay[it.id] }.orEmpty(),
    rejectedTotal = candlings.sumOf { it.rejected } + summary.eggRejected,
    totals = batchTotals(schedule, measurementsByDay, summary.day),
    candlingToday = candlings.firstOrNull { it.day == summary.day },
)

internal sealed interface BatchDetailIntent {
    /**
     * Шторку открыли заново.
     *
     * Данные перечитываются **всегда**, а форма замера сбрасывается только при
     * [resetForm]. Разница в том, что шторка закладки — не только форма: пока её
     * держали свёрнутой, закладку могли завершить или вернуть в работу из меню
     * карточки, и открыться со старым ответом она не вправе. А вот набранные
     * показания — это ввод, и он переживает сворачивание: см.
     * [ru.zaroslikov.incubator.design.components.SheetDraft].
     */
    data class Load(val batchId: Long, val resetForm: Boolean = true) : BatchDetailIntent

    /** Тап по карточке дня: раскрывает её, повторный по тому же дню — сворачивает. */
    data class ToggleDayEdit(val value: Value) : BatchDetailIntent

    data class UpdateDayEdit(val value: ValueUiState) : BatchDetailIntent

    data object CancelDayEdit : BatchDetailIntent

    data object SaveDayEdit : BatchDetailIntent

    /** Правка формы замера за сегодня: страница отдаёт копию [MeasurementForm] целиком. */
    data class UpdateForm(val form: MeasurementForm) : BatchDetailIntent

    /** «Изм.» в строке журнала за сегодня — записанный замер уезжает в форму. */
    data class StartEdit(val measurement: Measurement) : BatchDetailIntent

    data object CancelEdit : BatchDetailIntent

    data object Save : BatchDetailIntent

    data class UpdateDayForm(val form: MeasurementForm) : BatchDetailIntent

    data class StartDayEdit(val measurement: Measurement) : BatchDetailIntent

    data object CancelDayForm : BatchDetailIntent

    data object SaveDayForm : BatchDetailIntent

    data class Delete(val measurement: Measurement) : BatchDetailIntent

    /** Завершение в срок; [hide] — заодно убрать в архив, см. [BatchDetailViewModel]. */
    data class Finish(
        val outcome: HatchOutcome,
        /** Когда выключили инкубатор или вынули птенцов — конец счёта за свет. */
        val moment: FinishMoment,
        val hide: Boolean = false,
    ) : BatchDetailIntent

    data class FinishEarly(
        val reason: String,
        /** Когда выключили инкубатор или убрали яйца — конец счёта за свет. */
        val moment: FinishMoment,
        val hide: Boolean = false,
    ) : BatchDetailIntent

    /** Кнопки карточки таймера проветривания под полями формы «Замеры за сегодня». */
    data class AiringTimer(val action: AiringTimerAction) : BatchDetailIntent

    /**
     * Примечание ко дню на «Обзоре» — [Value.note] строки [dayId]. Сохраняется само, без
     * кнопки: карточка шлёт текст, когда набор затих, и ещё раз, уходя с экрана.
     */
    data class SaveDayNote(val dayId: Long, val note: String) : BatchDetailIntent
}

internal sealed interface BatchDetailEffect {
    /**
     * Закладка завершена — тем или другим путём; шторку (или диалог) можно закрывать.
     *
     * Эффект, а не callback в `finish(…, onDone)`: закрыть надо один раз и ровно тогда,
     * когда запись состоялась. Отправляется **после** `reportIncubationOutcomes` — см.
     * причину в [BatchDetailViewModel].
     *
     * [summary] — итог для карточки, которую экран показывает под шторкой: поздравление
     * с салютом у закладки, доведённой до срока с птенцами, «Инкубация прервана» у
     * прерванной (`HatchSummary.stopped`) и «Птенцы не вывелись» у вывода «ноль» в срок —
     * обе без салюта. Едет в эффекте, а не читается экраном из базы:
     * закладка к тому моменту уже записана, а сводка собирается из того, что ViewModel
     * и так держала — брака, дня и срока вида.
     */
    data class Finished(val batchId: Long, val summary: HatchSummary? = null) : BatchDetailEffect
}

/**
 * Поля формы замера. [editingId] отличен от нуля, когда правят уже записанный замер:
 * тогда сохраняется его же время, а не текущее — замер сделали тогда, когда сделали.
 *
 * Все поля строковые: так их держат поля ввода. В [Measurement] они уезжают числами,
 * и пустое поле становится `null` — «не записали».
 *
 * Проветривание форма спрашивает **одним** числом — минутами: за один заход инкубатор
 * открывают один раз, и сама запись и есть то самое одно проветривание. Вторую цифру,
 * которую база хранит рядом ([Measurement.airingCount]), считает [airingCountValue].
 * План дня — другое дело: там «2 раза по 5 минут» задаётся обеими цифрами.
 */
data class MeasurementForm(
    val editingId: Long = 0,
    val time: String = "",
    val temp: String = "",
    val damp: String = "",
    val over: String = "",
    val airingTime: String = "",
    val note: String = "",
    /**
     * Метка замера по инкубатору у правящейся записи — [Measurement.groupId], пронесённая
     * через форму, чтобы правка из шторки закладки не стёрла её: `@Update` пишет все
     * колонки, и собранный без метки замер выпал бы из группы, а вместе с ним из журнала
     * инкубатора и из правки и удаления группы. Ставит метку только шторка инкубатора;
     * форма её лишь сохраняет. Пусто у нового замера и у замера, внесённого в закладке.
     */
    val groupId: String? = null,
) {
    /**
     * Сколько проветриваний записал этот замер: одно, если минуты заполнены, и ни
     * одного, если поле пустое.
     */
    val airingCountValue: Int? get() = if (airingTime.isBlank()) null else 1

    /**
     * Пустой замер записывать незачем, но заметка — такая же его часть, как показания:
     * «долил воды» без цифр это полноценная запись в журнале дня.
     */
    val isValid: Boolean
        get() = temp.isNotBlank() || damp.isNotBlank() || over.isNotBlank() ||
                airingTime.isNotBlank() || note.isNotBlank()
}

/**
 * Шторка одной закладки: сводка, замеры за сегодня и режим на завтра.
 *
 * Идентификатор приходит не из `SavedStateHandle`, а намерением [BatchDetailIntent.Load] —
 * у шторки нет маршрута в навигации, ровно как у [AddBatchViewModel].
 *
 * Номер сегодняшнего дня инкубации считается от даты начала закладки, а вот сами замеры
 * хранятся при строке этого дня ([Value.id]). Поэтому исправленная дата начала или
 * переведённые на телефоне часы меняют только то, какой день считается сегодняшним;
 * уже записанные замеры остаются при своих днях.
 *
 * Состояние локальное ([StatefulMviViewModel]), а не собранное `combine`-ом из потоков:
 * половина его — ввод (две формы замера и раскрытый день), а потоки базы читаются
 * внутри одного [load], который при повторном открытии отменяется целиком.
 */
internal class BatchDetailViewModel(
    private val settings: AppSettings,
    private val itemsRepository: ItemsRepository,
    private val workRepository: WorkRepository,
    private val airingTimer: AiringTimerController,
) : StatefulMviViewModel<BatchDetailState, BatchDetailIntent, BatchDetailEffect>(
    BatchDetailState(),
) {
    /**
     * Градусы полей ввода — из «Настроек». Строки формы держатся в них, а число в базе
     * всегда в Цельсиях; перевод — `toValueUiState(unit)` / `toValue(unit)` в `ValueFormat`.
     * Читается при каждом обращении, а не один раз: форма переживает уход в настройки.
     */
    private val temperatureUnit: TemperatureUnit
        get() = settings.temperatureUnit

    /** Одна корутина на всё открытие: сперва читает закладку, потом остаётся висеть
     *  на потоке замеров. Отмена при новом [load] снимает и то и другое разом. */
    private var loadJob: Job? = null

    /**
     * Закладка, которую читают сейчас. `loadJob?.cancel()` не ждёт отмены, и коллектор,
     * уже вошедший в тело `collect`, досчитал бы свой `reduce` и положил строки прежней
     * закладки поверх сброшенного состояния; каждая запись сверяется с этим полем.
     */
    private var activeBatchId: Long = 0

    /**
     * Результат таймера, чьи минуты эта форма уже подставила, — по его `id`. Чтобы не
     * подставлять их повторно при каждом ответе контроллера (человек мог их поправить) и
     * при этом подставить снова после сброса формы. Не в состоянии: экран его не рисует.
     */
    private var filledTimerId: Long = 0L

    /**
     * Закладка целиком, как её прочитали при открытии.
     *
     * Состояние хранит из неё только то, что рисуется, а завершение переписывает строку
     * в базе — и переписывать её нужно всю, вместе с напоминаниями, породой и заметкой,
     * которых на экране нет. Собирать `Batch` заново из полей сводки значило бы молча
     * обнулить всё, чего в них не оказалось.
     */
    private var batch: Batch? = null

    /**
     * Завершение уже пишется. Кнопка диалога остаётся живой, пока запись не вернётся
     * эффектом, и второе нажатие в это окно писало бы закладку дважды, слало бы два
     * отчёта и оставляло в канале второй `Finished`: первый закрывает шторку и её
     * коллектор, а второй дожидался бы следующего открытия и закрывал бы его сразу — с
     * поздравлением за старую закладку. Сбрасывается в [load]: ViewModel живёт дольше
     * шторки, и следующая закладка завершается заново.
     */
    private var finishing = false

    override fun onIntent(intent: BatchDetailIntent) {
        when (intent) {
            is BatchDetailIntent.Load -> load(intent.batchId, intent.resetForm)
            is BatchDetailIntent.ToggleDayEdit -> toggleDayEdit(intent.value)
            is BatchDetailIntent.UpdateDayEdit -> reduce { copy(dayEdit = intent.value) }
            BatchDetailIntent.CancelDayEdit -> reduce { copy(dayEdit = null) }
            BatchDetailIntent.SaveDayEdit -> saveDayEdit()
            is BatchDetailIntent.UpdateForm -> reduce { copy(form = intent.form) }
            is BatchDetailIntent.StartEdit ->
                reduce { copy(form = intent.measurement.toForm(temperatureUnit)) }
            // Правка идёт в форме, а не в самой строке, поэтому «вернуть как было» — это
            // просто не сохранять: в базе до нажатия «Сохранить замер» ничего не менялось.
            BatchDetailIntent.CancelEdit -> reduce { copy(form = MeasurementForm()) }
            BatchDetailIntent.Save -> save()
            is BatchDetailIntent.UpdateDayForm -> reduce { copy(dayForm = intent.form) }
            is BatchDetailIntent.StartDayEdit ->
                reduce { copy(dayForm = intent.measurement.toForm(temperatureUnit)) }
            BatchDetailIntent.CancelDayForm -> reduce { copy(dayForm = MeasurementForm()) }
            BatchDetailIntent.SaveDayForm -> saveDayForm()
            is BatchDetailIntent.Delete -> delete(intent.measurement)
            is BatchDetailIntent.Finish -> finish(intent.outcome, intent.moment, intent.hide)
            is BatchDetailIntent.FinishEarly -> finishEarly(intent.reason, intent.moment, intent.hide)
            is BatchDetailIntent.AiringTimer -> onTimer(intent.action)
            is BatchDetailIntent.SaveDayNote -> saveDayNote(intent.dayId, intent.note)
        }
    }

    private fun load(batchId: Long, resetForm: Boolean) {
        if (batchId == 0L) return
        // Сводка той же закладки при сбросе не трогается: она и есть то, по чему шторка
        // узнаёт, что закладка прочитана, и обнулить её значило бы на кадр показать
        // колесо поверх того, что только что читали. Сводка *другой* закладки — чужая:
        // оставь её, и шторка до ответа базы рисовала бы имя, день и кольцо соседки, а
        // `viewOnly` считался бы по её `finished`. Три списка из базы чужие всегда, и
        // производные от них пересчитываются тут же, чтобы итог прежней закладки не
        // постоял секунду над новой.
        // Сброшенная форма снова готова принять минуты таймера: если его результат ещё
        // ждёт (замер не записан), подписка ниже подставит их в чистое поле.
        if (resetForm) filledTimerId = 0L
        reduce {
            copy(
                summary = if (summary.batchId == batchId) summary else BatchDetailUiState(),
                form = if (resetForm) MeasurementForm() else form,
                dayForm = if (resetForm) MeasurementForm() else dayForm,
                dayEdit = if (resetForm) null else dayEdit,
                measurementsByDay = emptyMap(),
                schedule = emptyList(),
                candlings = emptyList(),
            ).withDerived()
        }
        batch = null
        activeBatchId = batchId
        finishing = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val batch = itemsRepository.getBatch(batchId).filterNotNull().first()
            this@BatchDetailViewModel.batch = batch
            val plan = itemsRepository.getBatchValues(batchId).first()
            // Один снимок, а не подписка: срок и дни овоскопирования у закладки
            // меняются не чаще, чем её вид, а вид она за свою жизнь не меняет.
            val catalog = SpeciesCatalog(itemsRepository.getCustomSpecies().first())

            val total = catalog.incubationDays(batch.type)
            val start = parseDate(batch.data)
            // Закончившаяся закладка стоит на дне своего завершения, а не на сегодняшнем:
            // иначе прерванная на пятый день через месяц показывала бы «День 28/28».
            // Карточка (`batchProgress`) считает так же. Идущая считается от момента
            // закладки до «сейчас» (день начинается в час закладки, `incubationDay`),
            // прерванная — по датам: о моменте остановки известно только число.
            val (from, until) = if (batch.status == BatchStatus.Active) batchStartMoment(batch) to Date()
            else start to (parseDate(batch.dateEnd) ?: today())
            val day = if (batch.status == BatchStatus.Hatched && total != null) {
                // Дошедшая до срока стоит на последнем своём дне, каким бы числом её ни
                // завершили: «Завершено» и означает «срок отбыт», а завершать можно с
                // предпоследнего дня — птенцы наклёвываются не по календарю. Карточка
                // печатает ровно «День N/N», и расходиться с ней тут нечему.
                total
            } else {
                // Та же функция, которой шторка замеров инкубатора выбирает строку
                // «сегодня» у каждой идущей закладки: замер, записанный оттуда, обязан
                // лечь в тот день, который здесь покажут как «Замеры за сегодня».
                incubationDay(from, total, until)
            }

            // Имя не `summary`: внутри `reduce` неявный приёмник — само состояние, и
            // его одноимённое поле перекрыло бы эту переменную, превратив запись в
            // присваивание самому себе.
            val loadedSummary = BatchDetailUiState(
                batchId = batch.id,
                incubatorId = batch.incubatorId,
                title = batch.title.ifBlank { batch.type },
                type = batch.type,
                eggAll = batch.eggAll,
                eggAllEND = batch.eggAllEND,
                eggRejected = batch.eggRejected,
                breed = batch.breed,
                note = batch.note.trim(),
                finished = batch.status != BatchStatus.Active,
                stoppedEarly = batch.status == BatchStatus.Stopped,
                hidden = batch.hidden,
                endReason = batch.endReason,
                startDate = start,
                startTime = batch.time.trim().takeIf { LAYING_TIME.matches(it) }.orEmpty(),
                hatchDate = when {
                    batch.arhive != "0" -> parseDate(batch.dateEnd)
                    start != null && total != null -> start.plusDays(total)
                    else -> null
                },
                day = day,
                totalDays = total,
                finishesAt = if (batch.status == BatchStatus.Active) batchFinishMoment(batch, catalog) else null,
                plannedToday = plan.firstOrNull { it.day == day },
                plannedTomorrow = plan.firstOrNull { it.day == day + 1 },
                autoTurn = batch.over.toBoolean(),
                autoAiring = batch.airing.toBoolean(),
                price = batch.price,
                pricePerEgg = batch.pricePerEgg,
                catalog = catalog,
                loaded = true,
            )
            // День установлен — а от него зависят все четыре производные.
            if (activeBatchId != batchId) return@launch
            reduce { copy(summary = loadedSummary).withDerived() }

            // Расписание слушается отдельной корутиной — дочерней, поэтому отмена
            // `loadJob` снимает и её. Идентификаторы дней при правке не меняются, так
            // что подписка на замеры ниже остаётся верной.
            launch {
                itemsRepository.getBatchValues(batchId).collect { rows ->
                    if (activeBatchId != batchId) return@collect
                    reduce {
                        val dayNow = summary.day
                        copy(
                            schedule = rows,
                            summary = summary.copy(
                                plannedToday = rows.firstOrNull { it.day == dayNow },
                                plannedTomorrow = rows.firstOrNull { it.day == dayNow + 1 },
                            ),
                        ).withDerived()
                    }
                }
            }

            launch { watchAiringTimer(batchId) }

            // Свет для финансовой справки — разовым чтением при открытии: у идущей
            // закладки счёт растёт, но часами, и шторка перечитывает его при каждом
            // открытии. Отдельной корутиной, чтобы чтение инкубатора и соседок не
            // задерживало сводку.
            launch {
                // Один «сейчас» на оба числа: часы и киловатт-часы — об одном отрезке.
                val now = Date()
                val incubator = itemsRepository.getIncubator(batch.incubatorId).first()
                val power = batch.power.over(incubator?.power ?: PowerSettings())
                val electricity = electricityOfFinished(
                    batch = batch,
                    incubator = incubator,
                    neighbours = itemsRepository.getBatchesFor(batch.incubatorId).first(),
                    now = now,
                )
                val hours = batchRunHours(batch, now)
                if (activeBatchId != batchId) return@launch
                reduce {
                    copy(
                        summary = summary.copy(
                            electricity = electricity,
                            electricityLoaded = true,
                            runHours = hours,
                            nightWindow = if (power.twoTariffs) power.nightStart to power.nightEnd else null,
                        ),
                    )
                }
            }

            launch {
                itemsRepository.getCandlings(batchId).collect { rows ->
                    if (activeBatchId != batchId) return@collect
                    reduce { copy(candlings = rows).withDerived() }
                }
            }

            itemsRepository.getBatchMeasurements(batchId).collect { rows ->
                if (activeBatchId != batchId) return@collect
                val byDay = rows.groupBy { it.idValue }
                reduce { copy(measurementsByDay = byDay).withDerived() }
            }
        }
    }

    /** Раскрывает день расписания; повторный тап по тому же дню его сворачивает. */
    private fun toggleDayEdit(value: Value) {
        val unit = temperatureUnit
        reduce {
            copy(
                dayEdit = if (dayEdit?.id == value.id) null else value.toValueUiState(unit),
                dayForm = MeasurementForm(),
            )
        }
    }

    /**
     * Сохраняет правку дня.
     *
     * Правится только строка плана: замеры висят на её идентификаторе, он не меняется,
     * и уже записанные показания остаются при своём дне.
     */
    private fun saveDayEdit() {
        val edited = current.dayEdit ?: return
        val unit = temperatureUnit
        viewModelScope.launch {
            itemsRepository.updateValue(edited.toValue(unit))
            reduce { copy(dayEdit = null) }
        }
    }

    /**
     * Пишет примечание ко дню в его строку плана.
     *
     * Строка берётся из расписания, каким его последним прислала база, и меняется в ней
     * одна заметка: режим дня могли поправить на «Расписании», и копия, снятая раньше,
     * вернула бы старые цифры. В `viewModelScope`, а не в области карточки: последний
     * текст карточка отдаёт, уже закрываясь, и запись обязана пережить её.
     */
    private fun saveDayNote(dayId: Long, note: String) {
        val row = current.schedule.firstOrNull { it.id == dayId } ?: return
        if (row.note == note) return
        // Редактор этого же дня на «Расписании» держит свою копию строки и при
        // «Сохранить» записал бы её целиком — со старой заметкой поверх новой. Поэтому
        // заметка подменяется и в открытой правке: последним сказанным словом остаётся
        // то, что написали последним, где бы это ни было.
        reduce {
            copy(dayEdit = dayEdit?.let { if (it.id == dayId) it.copy(note = note) else it })
        }
        viewModelScope.launch { itemsRepository.updateValue(row.copy(note = note)) }
    }

    private fun save() {
        val snapshot = current
        val dayRow = snapshot.summary.plannedToday ?: return
        persist(snapshot.form, dayRow.id) {
            reduce { copy(form = MeasurementForm()) }
            // Замер «сегодня» записан — результат таймера этой закладки отработал.
            airingTimer.resultSaved(snapshot.timerTarget)
        }
    }

    // --- Замеры произвольного дня (вкладка «Расписание») -----------------------------------

    /**
     * Записывает замер в раскрытый день расписания, а не в сегодняшний.
     *
     * Именно так замер и попадает в базу задним числом: строка дня уже существует, и
     * привязка идёт к ней. Время при этом не «сейчас» — форма дня всегда показывает
     * поле времени, потому что у прошедшего дня «сейчас» не значит ничего.
     */
    private fun saveDayForm() {
        val snapshot = current
        val dayRow = snapshot.dayEdit ?: return
        persist(snapshot.dayForm, dayRow.id) { reduce { copy(dayForm = MeasurementForm()) } }
    }

    /**
     * Общая запись замера: [dayId] — строка расписания, к которой он привязан.
     *
     * У правки день берётся тот же, в котором замер открыли, поэтому перевесить запись
     * на чужой день отсюда нельзя.
     */
    private fun persist(form: MeasurementForm, dayId: Long, onSaved: () -> Unit) {
        if (!form.isValid) return
        viewModelScope.launch {
            // Колонка, отданная автоматике, в замер не пишется — форма её и не
            // предлагает, но черновик мог остаться с тех пор, когда предлагала, а
            // composition переживает состояние, из которого построена. Правило то же,
            // что у плана: под флагом «Авто» числа нет, и записать его нельзя ниоткуда.
            val (autoTurn, autoAiring) = current.summary.let { it.autoTurn to it.autoAiring }
            val measurement = Measurement(
                id = form.editingId,
                idValue = dayId,
                time = form.time.ifBlank { clockText() },
                temp = form.temp.toCelsiusOrNull(temperatureUnit),
                damp = form.damp.toMeasureOrNull(),
                over = if (autoTurn) null else form.over.toCountOrNull(),
                airingCount = if (autoAiring) null else form.airingCountValue,
                airingTime = if (autoAiring) null else form.airingTime.toCountOrNull(),
                note = form.note.trim(),
                groupId = form.groupId,
            )
            if (form.editingId == 0L) itemsRepository.insertMeasurement(measurement)
            else itemsRepository.updateMeasurement(measurement)
            val snapshot = current
            // Шаг воронки между «создал закладку» и «завершил инкубацию»: открытая шторка
            // от записанного замера иначе неотличима.
            Analytics.report(
                Events.MEASUREMENT_SAVED,
                mapOf(
                    "Вид" to snapshot.summary.type,
                    // День берётся у строки расписания, а не из состояния: вкладка
                    // «Расписание» пишет замеры и в прошедшие дни, и «День» тогда
                    // означал бы сегодняшний вместо того, к которому запись привязана.
                    "День" to (
                        snapshot.schedule.firstOrNull { it.id == dayId }?.day
                            ?: snapshot.summary.day
                        ),
                    // Что именно записали: замер бывает и одной заметкой без цифр.
                    "Температура" to (measurement.temp != null),
                    "Влажность" to (measurement.damp != null),
                    "Перевороты" to (measurement.over != null),
                    "Проветривание" to (measurement.airingTime != null),
                    "Заметка" to measurement.note.isNotBlank(),
                    "Правка" to (form.editingId != 0L),
                    // Явное `false`, а не отсутствие: в AppMetrica «параметра нет» и
                    // «false» — разные разрезы, и доля замеров по инкубатору считалась
                    // бы по половине записей.
                    "Из инкубатора" to false,
                ),
            )
            onSaved()
        }
    }

    // --- Таймер проветривания ---------------------------------------------------------------

    /**
     * Слушает таймер и подставляет его результат, когда он адресован этой закладке:
     * минуты ложатся в поле «ПРОВ., МИН» формы «Замеры за сегодня» — один раз на
     * результат и форму ([filledTimerId]), чтобы поправленное руками число не
     * перезаписывалось. Контроллеру подстановка ничего не сообщает: результат ждёт там,
     * пока замер не записан ([AiringTimerController.resultSaved]), и поэтому доходит и до
     * формы, пересозданной переходом по уведомлению, — прежняя успевала подставить минуты
     * в свёрнутом приложении и уносила их с собой.
     *
     * Под автопроветриванием поле заперто, и подставлять некуда.
     */
    private suspend fun watchAiringTimer(batchId: Long) {
        airingTimer.state.collect { raw ->
            if (activeBatchId != batchId) return@collect
            // По часам: процесс и ViewModel живут часами, а результат трёхчасовой давности
            // в сегодняшний замер класть нельзя — это и есть срок из `settled`.
            val timer = raw.settled(System.currentTimeMillis())
            if (timer != raw) {
                airingTimer.refresh()
                return@collect
            }
            reduce { copy(airingTimer = timer) }
            val snapshot = current
            if (timer is AiringTimerState.Done &&
                timer.id != filledTimerId &&
                timer.target == snapshot.timerTarget &&
                !snapshot.summary.autoAiring
            ) {
                filledTimerId = timer.id
                reduce { copy(form = form.copy(airingTime = timer.minutes.toString())) }
            }
        }
    }

    /**
     * Кнопки карточки таймера. Запуск — только когда есть куда записать (строка дня есть,
     * проветривание не на автоматике); остальное — только над своим таймером: карточка
     * чужого кнопок не рисует, но composition переживает состояние, из которого построена.
     */
    private fun onTimer(action: AiringTimerAction) {
        val snapshot = current
        val target = snapshot.timerTarget
        val mine = snapshot.airingTimer.targetOrNull == target
        when (action) {
            is AiringTimerAction.Start -> {
                if (!snapshot.summary.canRecord || snapshot.summary.autoAiring) return
                if (snapshot.airingTimer !is AiringTimerState.Idle) return
                airingTimer.start(target, snapshot.summary.title, action.minutes)
            }
            AiringTimerAction.Cancel -> if (mine) airingTimer.cancel()
            AiringTimerAction.Finish -> if (mine) airingTimer.finishNow()
            AiringTimerAction.Dismiss -> if (mine) airingTimer.dismiss()
            AiringTimerAction.Silence -> airingTimer.stopRinging()
        }
    }

    private fun delete(measurement: Measurement) {
        viewModelScope.launch {
            itemsRepository.deleteMeasurement(measurement)
            reduce {
                copy(
                    form = if (form.editingId == measurement.id) MeasurementForm() else form,
                    dayForm = if (dayForm.editingId == measurement.id) {
                        MeasurementForm()
                    } else {
                        dayForm
                    },
                )
            }
        }
    }

    // --- Завершение инкубации --------------------------------------------------------------

    /**
     * Завершение в срок: птенцы вывелись, закладка завершена (в архив — только с `hide`).
     *
     * Что при этом записывается — дело [finishedOnTime] в `:domain`, как у досрочного —
     * дело [stoppedEarly]: вывод и цену птенцов зажимает та же функция, а не поле
     * ввода — числа сюда могут прийти и из другого места.
     *
     * Причина завершения не пишется: пустая [Batch.endReason] у завершённой закладки и
     * означает «в срок».
     *
     * [hide] — для «Убрать в архив» из меню карточки: см. [archive].
     */
    private fun finish(outcome: HatchOutcome, moment: FinishMoment, hide: Boolean) {
        val source = batch ?: return
        archive(
            source.finishedOnTime(
                outcome = outcome,
                dateEnd = moment.date,
                candlingRejected = current.candlings.sumOf { it.rejected },
                timeEnd = moment.time,
            ),
            hide,
        )
    }

    /**
     * Досрочное завершение: птенцов не будет, вся закладка уходит в расход.
     *
     * Что при этом записывается — дело [stoppedEarly] в `:domain`: та же функция прерывает
     * закладки, когда весь инкубатор уходит в архив, и итог не должен зависеть от того,
     * каким путём нажали. Причина обязательна: без неё через полгода не вспомнить, почему
     * партия из тридцати яиц кончилась ничем.
     */
    private fun finishEarly(reason: String, moment: FinishMoment, hide: Boolean) {
        val source = batch ?: return
        archive(source.stoppedEarly(reason, moment.date, moment.time), hide)
    }

    /**
     * Общая часть обоих завершений: флаг списка и снятие напоминаний.
     *
     * Напоминания снимаются по идентификатору закладки: она больше не в работе, и
     * будить по ней в восемь утра незачем. Это быстрый путь, и он не единственный —
     * `ReminderWorker` при срабатывании сам перечитывает закладку и молчит, если та в
     * архиве. Два рубежа нужны потому, что снятие работы действует только на этом
     * телефоне и только сейчас: расписание WorkManager живёт вне базы, и закладка,
     * приехавшая завершённой в чужой копии базы, никакого снятия не проходила.
     *
     * [hide] поднимает заодно [Batch.hidden], и той же записью, а не вторым обновлением
     * следом: «Убрать в архив» у идущей закладки — одно действие, а не «заверши, а потом
     * убери ещё раз». Уже поднятый флаг не сбрасывается — завершение не повод доставать
     * закладку обратно в список.
     */
    private fun archive(finished: Batch, hide: Boolean) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            val stamped = finished.copy(hidden = finished.hidden || hide)
            // Сперва запись, потом снятие, и порядок здесь не косметический. Упади
            // запись — закладка осталась бы идущей, но уже без будильников, и молчание
            // выглядело бы как поломка. В обратную сторону потеря безобидна: работа
            // проснётся, перечитает закладку, увидит архив и снимет себя сама.
            itemsRepository.updateBatch(stamped)
            workRepository.refreshReminders()
            batch = stamped
            val endedOn = parseDate(stamped.dateEnd)
            val wasStopped = stamped.status == BatchStatus.Stopped
            // День остановки — от даты, выбранной в диалоге (её можно поставить задним
            // числом), и той же арифметикой, что `load` у прерванной: по датам.
            val stoppedOn = if (wasStopped) {
                incubationDay(parseDate(stamped.data), current.summary.totalDays, endedOn ?: today())
            } else null
            // Шторку могли смахнуть, пока шла запись, и открыть другую закладку: тогда
            // сводка ей не принадлежит. Отчёт ниже уходит всё равно — запись уже прошла, —
            // а `Finished` несёт id, и чужая шторка его не примет.
            if (activeBatchId == stamped.id) reduce {
                copy(
                    summary = summary.copy(
                        eggAllEND = stamped.eggAllEND,
                        finished = true,
                        stoppedEarly = wasStopped,
                        hidden = stamped.hidden,
                        // Причина едет вместе с флагом: `stoppedEarly` без неё — сводка,
                        // противоречащая собственному описанию, и карточка причины на
                        // «Обзоре» осталась бы пустой, если шторку не закрыли.
                        endReason = stamped.endReason,
                        // Дошедшая до срока встаёт на последний день, как в load и на карточке;
                        // прерванная — на дне остановки.
                        day = stoppedOn ?: summary.totalDays ?: summary.day,
                        hatchDate = endedOn,
                        finishesAt = null,
                    ),
                ).withDerived()
            }
            // Отчёт об инкубаторе — до эффекта, и это не косметика: по нему закрывается
            // шторка, а вместе с ней уходит и ViewModel с её областью корутин, так что
            // чтение базы для отчёта было бы отменено на середине. Записан итог уже
            // выше, поэтому эффективность считается вместе с этой закладкой.
            itemsRepository.reportIncubationOutcomes(stamped.incubatorId, listOf(stamped))
            // Сводка — у любого завершения: за птенцов экран покажет поздравление с
            // салютом, прерванной и выводу «ноль» — ту же карточку без него.
            val summary = hatchSummaryOf(
                stamped,
                // В срок всё, что не вылупилось, — отбраковка: так её записал
                // `finishedOnTime`. У прерванной брак — только убранное до остановки.
                if (wasStopped) current.rejectedTotal else stamped.eggAll - stamped.eggAllEND,
                current.summary.totalDays,
                // Свет — как в «Финансах»: соседи по инкубатору делят с ней общие часы.
                electricityOfFinished(
                    batch = stamped,
                    incubator = itemsRepository.getIncubator(stamped.incubatorId).first(),
                    neighbours = itemsRepository.getBatchesFor(stamped.incubatorId).first(),
                ),
                stoppedDay = stoppedOn ?: current.summary.day,
            )
            sendEffect(BatchDetailEffect.Finished(stamped.id, summary))
        }
    }
}

/** Записанный замер обратно в поля формы — и на «Обзоре», и в дне расписания. */
internal fun Measurement.toForm(unit: TemperatureUnit): MeasurementForm = MeasurementForm(
    editingId = id,
    time = time,
    temp = temp.toTempFieldText(unit),
    damp = damp.toFieldText(),
    over = over.toCountText(),
    // Кол-во проветриваний в форму не возвращается: его там не спрашивают, и при
    // сохранении оно снова выведется из минут.
    airingTime = airingTime.toCountText(),
    note = note,
    groupId = groupId,
)

/** «ЧЧ:ММ» — час закладки в том виде, в каком его пишет форма. */
private val LAYING_TIME = Regex("""\d{1,2}:\d{2}""")
