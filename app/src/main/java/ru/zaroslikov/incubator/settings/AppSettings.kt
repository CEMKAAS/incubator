package ru.zaroslikov.incubator.settings

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.zaroslikov.incubator.airing.AiringTimerRecord
import ru.zaroslikov.incubator.airing.AiringTimerState
import ru.zaroslikov.incubator.design.theme.ThemeMode
import java.util.UUID

/**
 * Настройки самого приложения — то, что не относится ни к устройству, ни к закладке.
 *
 * Живут в `SharedPreferences`, а не в базе, и это разные вещи по природе: база — данные
 * пользователя, которые он переносит с телефона на телефон файлом (см. `DatabaseTransfer`
 * в `:data`), а настройки принадлежат установке приложения. Выключатель напоминаний,
 * уехавший вместе с чужой базой, включил бы человеку уведомления, которых он не просил.
 *
 * Файлов два, и делит их не удобство, а резервная копия Android. `app_prefs` — то, что
 * человек про себя выбрал: выключатель напоминаний, тема, градусы и валюта; пусть едет в облако и
 * встречает его на новом телефоне таким же. `device_prefs` — свойства **этой установки**:
 * идентификатор профиля аналитики, отметка об уборке старых напоминаний, код версии для
 * рекламы, флаги первого запуска. Им уезжать нельзя: восстановленный идентификатор
 * означал бы два телефона в одном профиле, восстановленная отметка об уборке — мусор
 * прежних работ, который уже никто не разгребёт, а восстановленные флаги первого запуска
 * — что новый телефон считает себя виденным.
 *
 * Разделить пришлось именно файлами: правила бэкапа работают по файлам, отдельный ключ
 * из `app_prefs.xml` исключить нечем. Ключи, заведённые до этого разделения, переезжают
 * при первом обращении ([migrateInstallationKeys]) — иначе у обновившихся сменился бы
 * `installationId`, а вместе с ним и когорта.
 */
class AppSettings(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Свойства установки — то, что не должно приезжать из чужой резервной копии. */
    private val devicePrefs: SharedPreferences =
        appContext.getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE)
            .also { migrateInstallationKeys(it) }

    /**
     * Показывать ли напоминания о проверке инкубатора.
     *
     * По умолчанию включены: до появления этого выключателя они работали всегда, и
     * обновление приложения не должно молча замолчать у тех, кто их себе поставил.
     *
     * Выключатель не снимает запланированную работу в WorkManager, а глушит её в
     * [ru.zaroslikov.incubator.work.ReminderWorker]: расписание напоминаний принадлежит
     * закладке — какие времена и с каким текстом, — и снять его значило бы забыть,
     * а обратно поставить было бы уже неоткуда. Работа продолжает просыпаться и молча
     * заканчиваться, а закладка сохраняет свои времена в целости.
     */
    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(value) {
            prefs.edit { putBoolean(KEY_REMINDERS, value) }
        }

    /**
     * Прибрано ли за напоминаниями прежних версий.
     *
     * До этой версии работы помечались названием закладки и назывались одним лишь
     * временем; ни одна нынешняя метка их не находит, а перебрать названия
     * недостаточно — закладку могли переименовать уже после постановки. Поэтому
     * первая же пересборка расписания снимает всю работу приложения разом
     * (`WorkManagerRepository.purgeLegacyWork`) и ставит его заново по базе, а этот
     * флаг следит, чтобы это случилось один раз, а не при каждом запуске.
     *
     * Живёт рядом с выключателем, а не в базе, и по той же причине: это свойство
     * установки. Уехав вместе с чужой базой, он оставил бы на новом телефоне мусор
     * прежних работ неубранным.
     */
    var remindersMigrated: Boolean
        get() = devicePrefs.getBoolean(KEY_REMINDERS_MIGRATED, false)
        set(value) {
            devicePrefs.edit { putBoolean(KEY_REMINDERS_MIGRATED, value) }
        }

    /** Тот же флаг потоком — для экрана, который должен перерисоваться на переключение. */
    fun remindersEnabledFlow(): Flow<Boolean> = booleanFlow(KEY_REMINDERS, default = true)

    /**
     * Тема оформления: как у системы, светлая или тёмная.
     *
     * По умолчанию — как у системы: до появления выбора приложение всегда было светлым,
     * а системная тема у большинства и стоит на светлой, так что обновление ничего не
     * перекрашивает само по себе. Хранится именем значения, а не порядковым номером:
     * добавь когда-нибудь четвёртый режим в середину перечисления — и число указало бы
     * на другую тему, а имя останется именем.
     *
     * Здесь, а не в базе, — по той же причине, что и выключатель напоминаний: тема
     * принадлежит этому телефону и его хозяину, а не хозяйству, которое переносят файлом.
     */
    var themeMode: ThemeMode
        get() = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit { putString(KEY_THEME_MODE, value.name) }
            syncNightMode()
        }

    /**
     * Сообщает системе ночной режим этого приложения по выбранной теме.
     *
     * Compose перекрашивается по потоку и без этого; система же о выборе не знает и
     * рисует стартовое окно (`windowBackground` из `values` / `values-night`) по теме
     * телефона — «Тёмная» на дневном телефоне вспыхивала бы кремовым при каждом
     * холодном старте. С Android 12 у приложения есть свой ночной режим,
     * [UiModeManager.setApplicationNightMode]: система запоминает его и берёт для
     * ресурсов приложения, в том числе для стартового окна. На старых версиях такого
     * рычага нет, и там фон окна ставит `MainActivity` до `setContent` — это закрывает
     * всё, кроме первого кадра.
     *
     * Вызывается из сеттера и один раз при старте приложения: настройки едут в
     * резервной копии Android, а режим у `UiModeManager` — нет, и после восстановления
     * на новом телефоне они разошлись бы.
     */
    fun syncNightMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = appContext.getSystemService(UiModeManager::class.java) ?: return
        val mode = when (themeMode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        // Без сравнения с текущим: `nightMode` у менеджера — режим системы, а не приложения,
        // а повторная установка того же значения ничего не перезапускает.
        manager.setApplicationNightMode(mode)
    }

    /**
     * Тема потоком — на неё подписана `MainActivity`, чтобы перекрасить приложение в
     * момент переключения, а не при следующем запуске.
     */
    fun themeModeFlow(): Flow<ThemeMode> = prefFlow(KEY_THEME_MODE) { themeMode }

    /**
     * Градусы, в которых показывают температуру: Цельсий или Фаренгейт.
     *
     * По умолчанию Цельсий — так приложение считало всегда, и в базе температура
     * хранится в нём же (см. [TemperatureUnit]). Именем, а не номером, как и тема.
     * В `app_prefs`, а не в свойствах установки: это выбор человека, и на новом
     * телефоне он должен встретить его таким же.
     */
    var temperatureUnit: TemperatureUnit
        get() = TemperatureUnit.fromName(prefs.getString(KEY_TEMPERATURE_UNIT, null))
        set(value) {
            prefs.edit { putString(KEY_TEMPERATURE_UNIT, value.name) }
        }

    /** Валюта сумм — знак после числа. По умолчанию рубль, как было до появления выбора. */
    var currency: Currency
        get() = Currency.fromName(prefs.getString(KEY_CURRENCY, null))
        set(value) {
            prefs.edit { putString(KEY_CURRENCY, value.name) }
        }

    /** Обе единицы разом, только что выбранное — сразу: на этот поток подписан корень приложения. */
    fun unitsFlow(): Flow<Units> =
        combine(
            prefFlow(KEY_TEMPERATURE_UNIT) { temperatureUnit },
            prefFlow(KEY_CURRENCY) { currency },
        ) { temperature, currency -> Units(temperature, currency) }

    /** Текущие единицы без подписки — для первого кадра и для ViewModel. */
    val units: Units
        get() = Units(temperatureUnit, currency)

    /**
     * Ждёт ли человека инструкция — экран «Как пользоваться» перед главным.
     *
     * **По умолчанию поднят, то есть инструкцию видит и тот, кто поставил приложение
     * впервые, и тот, кто обновился.** Она объясняет не новшества этой версии, а само
     * приложение — что такое инкубатор, что такое закладка, какие экраны листаются, — и
     * человеку с тремя инкубаторами всё это нужно ровно так же. Разница между ним и
     * новичком одна, и она наступает уже после инструкции: см. [addIncubatorPending].
     *
     * Снимается, когда инструкцию дочитали или пропустили ([guideFinished]), и больше не
     * поднимается. Отдельный флаг, а не сам `is_first_launch` из `MainActivity`, потому
     * что тот гаснет в первую же секунду, как только его прочли: убей приложение посреди
     * инструкции — и второй запуск был бы уже «не первым», а инструкция — непоказанной.
     * Этот держится, пока её не закроют, и потому переживает смерть процесса и поворот.
     */
    val guidePending: Boolean
        get() = devicePrefs.getBoolean(KEY_GUIDE_PENDING, true)

    /**
     * Открыть ли форму инкубатора сразу после инструкции.
     *
     * Поднимается только на настоящем первом запуске: пустому приложению нечего
     * показывать, и первый шаг оно делает за человека само. Обновившемуся эта форма не
     * нужна — его инкубаторы на месте, и она встала бы поверх списка предложением
     * завести ещё один, — поэтому его инструкция заканчивается главным экраном.
     *
     * Флаг переживает смерть процесса по той же причине, что и [guidePending]:
     * `is_first_launch` гаснет при первом чтении, а между ним и формой стоит инструкция,
     * которую можно читать сколько угодно долго.
     */
    var addIncubatorPending: Boolean
        get() = devicePrefs.getBoolean(KEY_ADD_INCUBATOR_PENDING, false)
        set(value) {
            devicePrefs.edit { putBoolean(KEY_ADD_INCUBATOR_PENDING, value) }
        }

    /**
     * Инструкцию закрыли — дочитав или пропустив.
     *
     * Оба флага гасятся **одной** записью, а не двумя подряд: процесс, убитый между
     * ними, оставил бы [addIncubatorPending] поднятым навсегда — снимать его было бы уже
     * некому, инструкция-то больше не появится, — и форма инкубатора вставала бы при
     * каждом запуске.
     */
    fun guideFinished() {
        devicePrefs.edit {
            putBoolean(KEY_GUIDE_PENDING, false)
            putBoolean(KEY_ADD_INCUBATOR_PENDING, false)
        }
    }

    /**
     * Случайный идентификатор этой установки — им подписан профиль в AppMetrica.
     *
     * Нужен затем, что без идентификатора профиля атрибуты отправлять некому: отчёт
     * «Профили» и сегменты по атрибутам строятся вокруг него (см.
     * [ru.zaroslikov.incubator.analytics.Analytics.activate]). Свой UUID, а не
     * идентификатор устройства и не рекламный: приложению не нужно знать, чей это
     * телефон, — нужно лишь не путать две установки между собой.
     *
     * Заводится при первом чтении и больше не меняется: сменившийся идентификатор
     * выглядел бы в отчёте новым человеком, и вся история хозяйства оборвалась бы
     * ровно там. Живёт в настройках, а не в базе, по той же причине, что и остальное
     * здесь: это свойство установки, а не хозяйства, и уезжать вместе с файлом базы на
     * другой телефон ему нельзя — там уже своя установка со своим идентификатором.
     *
     * Записывается сразу же, а не при выходе: `Application.onCreate` случается в каждом
     * процессе приложения, в том числе в том, который WorkManager поднимает ради
     * напоминания, и второй процесс должен прочитать уже готовое значение, а не
     * придумать своё.
     */
    val installationId: String
        get() {
            devicePrefs.getString(KEY_INSTALLATION_ID, null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            devicePrefs.edit(commit = true) { putString(KEY_INSTALLATION_ID, generated) }
            return generated
        }

    /**
     * Код версии, который реклама при запуске видела в прошлый раз; `null` — ни разу.
     *
     * По нему узнаётся первый запуск установки и первый запуск после обновления — в
     * обоих реклама при запуске молчит (см. `shouldMuteAppOpenAd` в `ads/`).
     * `MainActivity` сравнивает его с `BuildConfig.VERSION_CODE` и тут же записывает
     * текущий: следующий запуск той же версии — уже не первый. Отсюда следствие для
     * выпуска: **`versionCode` надо поднимать с каждой версией**, иначе обновление не
     * отличить от обычного запуска.
     *
     * Свойство установки, как и всё здесь: приехав с чужой базой, оно заставило бы новый
     * телефон считать чужую версию своей.
     */
    var adsVersionCode: Int?
        get() =
            if (devicePrefs.contains(KEY_ADS_VERSION)) devicePrefs.getInt(KEY_ADS_VERSION, 0) else null
        set(value) {
            devicePrefs.edit {
                if (value == null) remove(KEY_ADS_VERSION) else putInt(KEY_ADS_VERSION, value)
            }
        }


    /**
     * Код версии, в которой уже просили оценить приложение; `null` — ни разу не просили.
     *
     * Просьба приходит после доведённой до срока закладки (см. `rustore/ReviewController`),
     * и без этой отметки она приходила бы после каждой: у того, кто ведёт три инкубатора,
     * это несколько раз в месяц. Раз на версию — потому что версия и есть повод спросить
     * заново: приложение с тех пор изменилось, а прежняя оценка относилась к прежнему.
     *
     * Свойство установки, как и всё в этом файле: приехав из чужой резервной копии, оно
     * молча съело бы просьбу на новом телефоне — или, наоборот, показало её человеку,
     * который уже оценил приложение на старом.
     */
    var reviewAskedVersion: Int?
        get() =
            if (devicePrefs.contains(KEY_REVIEW_VERSION)) {
                devicePrefs.getInt(KEY_REVIEW_VERSION, 0)
            } else {
                null
            }
        set(value) {
            devicePrefs.edit {
                if (value == null) remove(KEY_REVIEW_VERSION) else putInt(KEY_REVIEW_VERSION, value)
            }
        }

    /**
     * Таймер проветривания — единственный на приложение, см. `airing/AiringTimerState`.
     *
     * Свойство установки, и это важнее обычного: таймер бежит на этом телефоне, и
     * восстановленный из чужой копии он либо зазвенел бы о проветривании, которого здесь
     * не было, либо вписал бы чужие минуты в первый открытый замер. Хранится плоской
     * записью ([AiringTimerRecord]) — семь примитивов, ради которых сериализатор не нужен.
     *
     * `commit`, а не `apply`: службу, которая читает запись, система может поднять в
     * другой момент жизни процесса, и записанное должно быть на диске к тому времени.
     */
    var airingTimer: AiringTimerState
        get() = AiringTimerRecord(
            phase = devicePrefs.getString(KEY_TIMER_PHASE, null) ?: AiringTimerRecord.PHASE_IDLE,
            id = devicePrefs.getLong(KEY_TIMER_ID, 0L),
            incubatorId = devicePrefs.getLong(KEY_TIMER_INCUBATOR, 0L),
            batchId = devicePrefs.getLong(KEY_TIMER_BATCH, 0L),
            label = devicePrefs.getString(KEY_TIMER_LABEL, null) ?: "",
            startedAt = devicePrefs.getLong(KEY_TIMER_STARTED_AT, 0L),
            endAt = devicePrefs.getLong(KEY_TIMER_END_AT, 0L),
            minutes = devicePrefs.getInt(KEY_TIMER_MINUTES, 0),
            ringing = devicePrefs.getBoolean(KEY_TIMER_RINGING, false),
            taken = devicePrefs.getBoolean(KEY_TIMER_TAKEN, false),
        ).toState()
        set(value) {
            val record = AiringTimerRecord.of(value)
            devicePrefs.edit(commit = true) {
                putString(KEY_TIMER_PHASE, record.phase)
                putLong(KEY_TIMER_ID, record.id)
                putLong(KEY_TIMER_INCUBATOR, record.incubatorId)
                putLong(KEY_TIMER_BATCH, record.batchId)
                putString(KEY_TIMER_LABEL, record.label)
                putLong(KEY_TIMER_STARTED_AT, record.startedAt)
                putLong(KEY_TIMER_END_AT, record.endAt)
                putInt(KEY_TIMER_MINUTES, record.minutes)
                putBoolean(KEY_TIMER_RINGING, record.ringing)
                putBoolean(KEY_TIMER_TAKEN, record.taken)
            }
        }


    /**
     * Переносит свойства установки из `app_prefs` в `device_prefs` — один раз на телефон.
     *
     * До разделения все ключи лежали в одном файле. Просто начать читать их из нового
     * значило бы выдать обновившемуся новый [installationId] — то есть оборвать историю
     * его хозяйства в аналитике ровно на этой версии — и заново показать инструкцию с
     * формой инкубатора поверх списка его инкубаторов.
     *
     * Из старого файла ключи удаляются: иначе они остались бы там уезжать в резервную
     * копию, ради чего всё и затевалось.
     *
     * `commit`, а не `apply`, и по той же причине, что у [installationId]:
     * `Application.onCreate` случается в каждом процессе приложения, включая тот, что
     * WorkManager поднимает ради напоминания, и второй процесс должен застать перенос
     * законченным. Стоит это одной синхронной записи на телефон за всю его жизнь.
     */
    private fun migrateInstallationKeys(device: SharedPreferences) {
        if (device.getBoolean(KEY_MIGRATED, false)) return
        val moved = INSTALLATION_KEYS.filter { prefs.contains(it) }
        if (moved.isEmpty()) {
            device.edit(commit = true) { putBoolean(KEY_MIGRATED, true) }
            return
        }
        device.edit(commit = true) {
            moved.forEach { key ->
                when (val value = prefs.all[key]) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    else -> Unit
                }
            }
            putBoolean(KEY_MIGRATED, true)
        }
        prefs.edit { moved.forEach { remove(it) } }
    }

    private fun booleanFlow(key: String, default: Boolean): Flow<Boolean> =
        prefFlow(key) { prefs.getBoolean(key, default) }

    /**
     * Значение под ключом [key] потоком: текущее сразу и новое на каждое изменение.
     *
     * Слушатель приходит с ключом, но реагируем и на `null`: так система сообщает
     * о полной очистке настроек, и после неё значение тоже стало другим.
     */
    private fun <T> prefFlow(key: String, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private const val PREFS_NAME = "app_prefs"

        /**
         * Второй файл настроек — тот, что исключён из резервной копии Android
         * (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`). Имя файла и
         * есть то, чем правила бэкапа умеют оперировать, поэтому оно повторено там
         * дословно: переименуешь здесь — и ключи установки поедут в облако молча.
         */
        private const val DEVICE_PREFS_NAME = "device_prefs"
        private const val KEY_REMINDERS = "reminders_enabled"
        private const val KEY_REMINDERS_MIGRATED = "reminders_migrated"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_GUIDE_PENDING = "guide_pending"
        private const val KEY_ADD_INCUBATOR_PENDING = "add_incubator_pending"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_ADS_VERSION = "ads_version_code"

        /**
         * В списке [INSTALLATION_KEYS] его нет и быть не может: переезжают туда ключи,
         * которые до разделения файлов лежали в `app_prefs`, а этот заведён уже после и
         * ни в одной прежней версии не существовал.
         */
        private const val KEY_REVIEW_VERSION = "review_asked_version"
        private const val KEY_MIGRATED = "installation_keys_moved"
        private const val KEY_TIMER_PHASE = "airing_timer_phase"
        private const val KEY_TIMER_ID = "airing_timer_id"
        private const val KEY_TIMER_INCUBATOR = "airing_timer_incubator"
        private const val KEY_TIMER_BATCH = "airing_timer_batch"
        private const val KEY_TIMER_LABEL = "airing_timer_label"
        private const val KEY_TIMER_STARTED_AT = "airing_timer_started_at"
        private const val KEY_TIMER_END_AT = "airing_timer_end_at"
        private const val KEY_TIMER_MINUTES = "airing_timer_minutes"
        private const val KEY_TIMER_RINGING = "airing_timer_ringing"
        private const val KEY_TIMER_TAKEN = "airing_timer_taken"

        /** Ключи, переезжающие из [PREFS_NAME] в [DEVICE_PREFS_NAME] на первом запуске. */
        private val INSTALLATION_KEYS = listOf(
            KEY_REMINDERS_MIGRATED,
            KEY_GUIDE_PENDING,
            KEY_ADD_INCUBATOR_PENDING,
            KEY_INSTALLATION_ID,
            KEY_ADS_VERSION,
        )
    }
}
