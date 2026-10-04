package ru.zaroslikov.incubator.ui.menu

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.domain.model.CustomSpecies
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.Currency
import ru.zaroslikov.incubator.settings.TemperatureUnit
import ru.zaroslikov.incubator.settings.Units
import ru.zaroslikov.incubator.settings.DataTransferController
import ru.zaroslikov.incubator.settings.TransferState
import ru.zaroslikov.incubator.design.theme.ThemeMode
import ru.zaroslikov.incubator.ui.mvi.MviSharing
import ru.zaroslikov.incubator.ui.mvi.MviViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Что спросили удалить и можно ли: свой вид и сколько идущих закладок по нему.
 *
 * Считается один раз, при нажатии, а не держится потоком: ответ нужен диалогу, и
 * диалог открывается ровно на этот ответ. [activeBatches] больше нуля — удалять нельзя,
 * и диалог вместо вопроса объясняет почему.
 */
data class DeleteSpeciesRequest(val species: CustomSpecies, val activeBatches: Int)

/**
 * Состояние экрана настроек — всё, что он рисует, одним значением.
 *
 * Раньше это были пять отдельных `StateFlow` и одно `mutableStateOf`: экран собирал
 * шесть подписок вручную, и половина состояния гасла по своему расписанию, а половина
 * не гасла вовсе. Теперь источник один и гаснет он целиком.
 */
@Immutable
data class SettingsState(
    /**
     * Свои виды птицы — список в карточке «Свои виды». Живёт здесь, а не в форме
     * закладки, потому что вид общий для всего хозяйства: заводят его из закладки, но
     * правят и удаляют там, где собраны остальные общие вещи.
     */
    val customSpecies: List<CustomSpecies> = emptyList(),
    /** Открытый вопрос об удалении вида; `null` — не спрашивают. */
    val deleteRequest: DeleteSpeciesRequest? = null,
    val remindersEnabled: Boolean = false,
    /**
     * Состояние переноса базы — приходит прямо из [DataTransferController].
     *
     * Контроллер переживает экран, и это условие, а не удобство: `importDatabase` —
     * обычный блокирующий вызов, и уход с настроек посреди него базу всё равно
     * подменял, только просить о перезапуске становилось некому.
     */
    val transfer: TransferState = TransferState.Idle,
    /** Тема оформления — карточка «Оформление». */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Градусы и валюта — карточки «Температура» и «Валюта». */
    val units: Units = Units(),
)

sealed interface SettingsIntent {
    /** Спросить про удаление вида — сперва посчитав, что по нему идёт. */
    data class RequestDeleteSpecies(val species: CustomSpecies) : SettingsIntent

    data object CancelDeleteSpecies : SettingsIntent

    data object ConfirmDeleteSpecies : SettingsIntent

    data class SetReminders(val enabled: Boolean) : SettingsIntent

    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent

    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsIntent

    data class SetCurrency(val currency: Currency) : SettingsIntent

    data object ClearTransfer : SettingsIntent

    /** Сохранить копию базы в выбранное системным окном место. */
    data class Export(val target: Uri) : SettingsIntent

    /** Заменить базу выбранным файлом. Дальше — перезапуск, о котором просит контроллер. */
    data class Import(val source: Uri) : SettingsIntent

    /** Подготовить копию к отправке в другое приложение. */
    data object ShareCopy : SettingsIntent

    data object Wipe : SettingsIntent
}

/**
 * Эффектов у настроек нет, и интерфейс всё равно объявлен.
 *
 * Всё одноразовое здесь уже носит [TransferState]: и «копия готова к отправке», и
 * «перезапустите приложение» — это состояния переноса, живущие в контроллере, который
 * экран переживает. Эффект — событие, привязанное к подписке композиции, — потерял бы
 * ровно то свойство, ради которого перенос из ViewModel и вынесли. Но третий тип у
 * [MviViewModel] обязателен.
 */
sealed interface SettingsEffect

/**
 * Экран настроек: напоминания, тема, единицы, свои виды птицы и перенос базы. Сам перенос — в
 * [DataTransferController]: он обязан дойти до конца, даже если экран закрыли, иначе база подменена,
 * а перезапустить приложение некому.
 */
class SettingsViewModel(
    private val settings: AppSettings,
    private val transferController: DataTransferController,
    private val itemsRepository: ItemsRepository,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    /**
     * Единственная локальная половина состояния: открытый вопрос об удалении вида.
     *
     * Своим потоком, входящим в тот же `combine`, — так ответ базы о видах не затирает
     * открытый диалог, а открытый диалог не задерживает ответ базы.
     */
    private val deleteRequest = MutableStateFlow<DeleteSpeciesRequest?>(null)

    /**
     * Шесть источников на одно состояние, и поток контроллера — среди них напрямую.
     *
     * Пересобери его отдельным `stateIn` в `viewModelScope` — и он снова умирал бы
     * вместе с экраном, ровно то, от чего контроллер и заведён. Начальное значение
     * поэтому читается у контроллера же: экран, открытый посреди начатого переноса,
     * обязан увидеть его в первом кадре, а не «Idle» до первого ответа.
     *
     * Настройки читаются синхронно (`settings.remindersEnabled` и далее) по той же
     * причине: это `SharedPreferences`, ответ у них есть сразу, и тумблер не должен
     * мигать выключенным над включёнными напоминаниями.
     */
    override val state: StateFlow<SettingsState> =
        combine(
            itemsRepository.getCustomSpecies(),
            deleteRequest,
            settings.remindersEnabledFlow(),
            settings.themeModeFlow(),
            settings.unitsFlow(),
        ) { species, request, reminders, theme, units ->
            SettingsState(
                customSpecies = species,
                deleteRequest = request,
                remindersEnabled = reminders,
                themeMode = theme,
                units = units,
            )
        }
            .combine(transferController.state) { base, transfer -> base.copy(transfer = transfer) }
            .stateIn(
                viewModelScope,
                MviSharing.WhileVisible,
                SettingsState(
                    remindersEnabled = settings.remindersEnabled,
                    themeMode = settings.themeMode,
                    units = settings.units,
                    transfer = transferController.state.value,
                ),
            )

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.RequestDeleteSpecies -> requestDeleteSpecies(intent.species)
            SettingsIntent.CancelDeleteSpecies -> deleteRequest.value = null
            SettingsIntent.ConfirmDeleteSpecies -> confirmDeleteSpecies()
            is SettingsIntent.SetReminders -> settings.remindersEnabled = intent.enabled
            is SettingsIntent.SetThemeMode -> settings.themeMode = intent.mode
            is SettingsIntent.SetTemperatureUnit -> settings.temperatureUnit = intent.unit
            is SettingsIntent.SetCurrency -> settings.currency = intent.currency
            SettingsIntent.ClearTransfer -> transferController.clear()
            is SettingsIntent.Export -> transferController.export(intent.target)
            is SettingsIntent.Import -> transferController.import(intent.source)
            // Имя файла то же, что и у экспорта: копия одна и та же, отличается только
            // тем, кто её получит.
            SettingsIntent.ShareCopy -> transferController.shareCopy(defaultExportName())
            SettingsIntent.Wipe -> transferController.wipe()
        }
    }

    /**
     * Спрашивает про удаление вида, сперва посчитав, что по нему идёт.
     *
     * Идущая закладка по удалённому виду осталась бы без срока и без овоскопирований
     * на середине пути — её карточка перестала бы считать дни. Завершённые не в счёт:
     * их план записан в них самих, а срок им больше не нужен.
     */
    private fun requestDeleteSpecies(species: CustomSpecies) {
        viewModelScope.launch {
            deleteRequest.value = DeleteSpeciesRequest(
                species = species,
                activeBatches = itemsRepository.countActiveBatchesOfType(species.name),
            )
        }
    }

    /**
     * Удаляет вид из открытого вопроса; при идущих закладках ничего не делает.
     *
     * Вопрос читается из своего потока, а не из [state]: подписки на состояние может не
     * быть — оно гаснет через пять секунд после ухода с экрана, — а открытый диалог
     * существует независимо от того, собрано ли состояние в эту секунду.
     */
    private fun confirmDeleteSpecies() {
        val request = deleteRequest.value ?: return
        deleteRequest.value = null
        if (request.activeBatches > 0) return
        viewModelScope.launch { itemsRepository.deleteCustomSpecies(request.species) }
    }

    companion object {
        /**
         * Имя файла по умолчанию: «incubator-2026-09-03.db».
         *
         * Дата в ISO, а не в привычном приложению «дд.ММ.гггг»: копий за разные дни
         * набирается несколько, и в списке файлов они должны выстраиваться по порядку
         * сами. Расширение `.db` — чтобы файл открылся тем же приложением обратно, а не
         * ушёл в просмотрщик текста.
         */
        fun defaultExportName(): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            return "incubator-$stamp.db"
        }
    }
}
