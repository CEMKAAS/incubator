package ru.zaroslikov.incubator.di

import android.content.Context
import ru.zaroslikov.incubator.analytics.AnalyticsProfile
import ru.zaroslikov.incubator.data.provideItemsRepository
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.repository.WorkRepository
import android.app.Application
import ru.zaroslikov.incubator.MainActivity
import ru.zaroslikov.incubator.account.AccountRepository
import ru.zaroslikov.incubator.ads.AppOpenAdController
import ru.zaroslikov.incubator.airing.AiringTimerController
import ru.zaroslikov.incubator.rustore.AppUpdateController
import ru.zaroslikov.incubator.rustore.ReviewController
import ru.zaroslikov.incubator.settings.AppSettings
import ru.zaroslikov.incubator.settings.DataTransferController
import ru.zaroslikov.incubator.stories.StoriesRepository
import ru.zaroslikov.incubator.transfer.ScheduleTransferController
import ru.zaroslikov.incubator.work.ReminderSync
import ru.zaroslikov.incubator.work.ReminderScheduler

interface AppContainer {
    val itemsRepository: ItemsRepository
    val workRepository : WorkRepository

    /**
     * Пересборка расписания напоминаний по базе — то, чем уведомления переживают
     * экспорт/импорт. Запускается из `MainActivity` при каждом открытии приложения;
     * почему именно оттуда и почему пересборка целиком, написано в [ReminderSync].
     */
    val reminderSync: ReminderSync

    /** Настройки установки приложения — выключатель напоминаний и всё, что к нему добавится. */
    val appSettings: AppSettings

    /**
     * Пересчёт атрибутов профиля AppMetrica по базе — то, чем режутся когорты и
     * сегменты. Запускается из `MainActivity` при каждом открытии приложения, рядом с
     * [reminderSync] и по той же причине: см. [AnalyticsProfile].
     */
    val analyticsProfile: AnalyticsProfile

    /**
     * Перенос базы. Живёт в контейнере, а не в ViewModel экрана настроек, потому что
     * обязан пережить закрытие экрана: подмена базы, доведённая до конца после того,
     * как ViewModel уничтожена, не смогла бы попросить перезапуск.
     */
    val transferController: DataTransferController

    /**
     * Файл расписания — экспорт из меню карточки и чтение в форме новой закладки. В
     * контейнере по той же причине, что и [transferController]: запись файла, начатую
     * из меню, нельзя бросать вместе с экраном. См. [ScheduleTransferController].
     */
    val scheduleTransfer: ScheduleTransferController

    /**
     * Реклама при запуске. В контейнере, а не в `MainActivity`, потому что следит за
     * выходом на передний план всего процесса и должна стоять раньше первой активности;
     * `MainActivity` только велит ей молчать на первом запуске. См. [AppOpenAdController].
     */
    val appOpenAds: AppOpenAdController

    /**
     * Обновление приложения через RuStore. В контейнере, а не в ViewModel экрана, ровно
     * по той же причине, что и [transferController]: загрузка идёт в RuStore и переживает
     * и экран, и поворот, и уход в фон, а состояние её должно пережить их вместе с ней.
     * См. [AppUpdateController].
     */
    val appUpdate: AppUpdateController

    /**
     * Просьба оценить приложение. Здесь, потому что приходит она из двух разных мест —
     * из диалога завершения закладки и из «О приложении», — а правило «не чаще раза на
     * версию» у неё одно на оба. См. [ReviewController].
     */
    val review: ReviewController

    /**
     * Таймер проветривания. В контейнере, потому что ставят его из формы замера, а
     * кончается он где угодно — в другой шторке, в другом приложении, с погашенным
     * экраном, — и результат должен дождаться формы, которая его заберёт. Служба и
     * приёмник кнопок уведомления достают его отсюда же. См. [AiringTimerController].
     */
    val airingTimer: AiringTimerController

    /**
     * Аккаунт по почте и паролю. В контейнере по причине [transferController]: выход и
     * отзыв токена доходят до конца и без экрана. См. [AccountRepository].
     */
    val account: AccountRepository

    /**
     * Истории главного экрана с сервера. В контейнере, а не во ViewModel: лента одна на
     * процесс, и каждое возвращение на главный экран не должно перезапрашивать её заново.
     * См. [StoriesRepository].
     */
    val stories: StoriesRepository
}

class AppDataContainer(private val application: Application) : AppContainer {
    private val context: Context = application
    override val itemsRepository: ItemsRepository by lazy {
        provideItemsRepository(context)
    }
    override val appSettings = AppSettings(context)
    override val transferController =
        DataTransferController(application) { account.logout(eraseProfile = false) }
    override val scheduleTransfer by lazy { ScheduleTransferController(application, itemsRepository) }
    // Не lazy: наблюдатель за процессом обязан встать до того, как первая активность
    // выйдет на экран, иначе холодный старт пройдёт мимо него.
    override val appOpenAds =
        AppOpenAdController(application, appSettings, MainActivity::class.java)
    // Пересчёт расписания и есть [workRepository]: единственное, что умеют напоминания
    // снаружи, — «пересобери по базе», и делает это он.
    override val reminderSync by lazy {
        ReminderSync(itemsRepository, ReminderScheduler(context), appSettings)
    }
    override val workRepository: WorkRepository get() = reminderSync
    override val analyticsProfile by lazy {
        AnalyticsProfile(context, itemsRepository, appSettings) { account.currentState() }
    }
    // Оба lazy: до RuStore приложение доходит не в каждом запуске (процесс напоминания
    // не доходит никогда), а создание менеджера лезет в систему за провайдером магазина.
    override val appUpdate by lazy { AppUpdateController(application) }
    override val review by lazy { ReviewController(application, appSettings) }
    // Lazy: состояние читается с диска при создании, и процессу, который WorkManager
    // поднимает ради напоминания, это чтение ни к чему; служба и приёмник, которым
    // контроллер нужен, достают его отсюда и создают при первом обращении.
    override val airingTimer by lazy { AiringTimerController(application, appSettings) }
    // Lazy: процессу напоминания аккаунт не нужен, а создание читает Keystore.
    override val account by lazy { AccountRepository(application, itemsRepository) }
    // Lazy: процессу напоминания истории не нужны.
    override val stories by lazy { StoriesRepository(application, account, appSettings) }
}
