package ru.zaroslikov.incubator.analytics

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.appmetrica.analytics.profile.Attribute
import io.appmetrica.analytics.profile.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.model.status
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.domain.stats.IncubatorStats
import ru.zaroslikov.incubator.domain.stats.incubatorStats
import ru.zaroslikov.incubator.settings.AppSettings

/**
 * Атрибуты профиля — то, чем режутся когорты, сегменты и удержание.
 *
 * Само удержание AppMetrica считает по сессиям и без единой строчки здесь, и когорту
 * «поставили в марте» тоже собирает сама. Чего она не знает — это **чем установка
 * является**: одним инкубатором или пятью, курами или перепелами, с напоминаниями или
 * без. А интересны именно такие разрезы: «у кого удержание выше — у тех, кто завёл
 * второй инкубатор, или у тех, кто остался с одним» — вопрос, ради которого профиль и
 * заводится, и без атрибутов он не задаётся никак.
 *
 * Считается по базе, а не копится по событиям. Событийный счётчик («+1 закладка»)
 * разошёлся бы с базой в первый же импорт: файл базы приезжает с другого телефона со
 * своими закладками, ни одна из которых на этой установке событием не отмечалась.
 * Пересчёт по базе переживает и импорт, и «Удалить все данные», и переустановку поверх
 * восстановленной копии — ровно по той же причине, по которой расписание напоминаний
 * не хранится, а выводится (`ReminderSync`).
 *
 * Запускается из `MainActivity.onCreate` при каждом открытии приложения, рядом с той же
 * пересборкой напоминаний. Не из `Application.onCreate`: процесс поднимает и WorkManager
 * ради напоминания, и чтение всей базы в такую минуту — работа впустую, человека за
 * телефоном в этот момент нет.
 *
 * Личного здесь нет ничего, кроме того, что человек сам вписал в поля инкубатора:
 * идентификатор профиля — случайный UUID установки ([AppSettings.installationId]), а не
 * идентификатор устройства.
 */
class AnalyticsProfile(
    private val context: Context,
    private val itemsRepository: ItemsRepository,
    private val appSettings: AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Пересчитывает профиль в фоне; вызывающему ждать нечего. */
    fun syncInBackground() {
        scope.launch { runCatching { sync() } }
    }

    /**
     * Читает хозяйство и отправляет атрибуты.
     *
     * Считает тем же `incubatorStats`, что и вкладка «Аналитика» в меню приложения:
     * она задаёт ровно тот же вопрос по тем же закладкам, и два разных ответа на него —
     * это отчёт, спорящий с экраном.
     */
    suspend fun sync() {
        val incubators = itemsRepository.getAllIncubators().first()
        val batches = itemsRepository.getAllBatches().first()
        val candlings = itemsRepository.getAllCandlings().first()
        val customSpecies = itemsRepository.getCustomSpecies().first()
        val stats = incubatorStats(batches, candlings)

        val main = mainIncubator(incubators, batches)
        val profile = UserProfile.newBuilder()

        profile.number(HOUSEHOLD_INCUBATORS, incubators.size)
        profile.number(HOUSEHOLD_BATCHES, batches.size)
        profile.number(HOUSEHOLD_ACTIVE_BATCHES, batches.count { it.status == BatchStatus.Active })
        profile.number(HOUSEHOLD_FINISHED_BATCHES, stats.total.finishedBatches)
        profile.number(HOUSEHOLD_STOPPED_BATCHES, batches.count { it.status == BatchStatus.Stopped })
        profile.number(HOUSEHOLD_EGGS, stats.totalEggs)
        profile.number(HOUSEHOLD_HATCHED, stats.hatched)
        profile.number(HOUSEHOLD_CUSTOM_SPECIES, customSpecies.size)

        // Эффективности может не быть вовсе — ни одной завершённой закладки. Тогда
        // атрибут снимается, а не отправляется нулём: «неизвестно» и «ноль процентов»
        // это разные ответы, и ноль в разрезе смешал бы новичков с теми, у кого не
        // вывелось ничего.
        profile.number(HATCH_RATE, stats.hatchRate)

        profile.text(SPECIES, mainSpecies(batches))
        profile.text(BRAND, main?.brand)
        profile.text(MODEL, main?.model)
        profile.text(THEME, appSettings.themeMode.name)
        profile.text(TEMPERATURE_UNIT, appSettings.temperatureUnit.name)
        profile.text(CURRENCY, appSettings.currency.name)
        profile.text(VERSION, BuildConfig.VERSION_NAME)
        profile.apply(Attribute.customBoolean(REMINDERS).withValue(appSettings.remindersEnabled))

        // Версия, на которой установку увидели впервые. Не перезаписывается: она и есть
        // когорта «пришли на такой-то версии», а `withValue` затирал бы её при каждом
        // обновлении и превращал в копию [VERSION].
        profile.apply(
            Attribute.customString(FIRST_VERSION).withValueIfUndefined(BuildConfig.VERSION_NAME)
        )

        // Системное разрешение, а не выключатель в настройках: приложение без права на
        // уведомления не разбудит никого, что бы у него ни стояло внутри, и в разрезе
        // удержания это совсем другая установка.
        profile.apply(Attribute.notificationsEnabled().withValue(notificationsAllowed()))

        Analytics.reportProfile(profile.build())
    }

    /**
     * Числовой атрибут; `null` — атрибут снимается.
     *
     * Снимается, а не подменяется нулём: ноль в разрезе — полноправное значение, и
     * «нечего сказать» в нём растворилось бы без следа.
     */
    private fun UserProfile.Builder.number(name: String, value: Int?) {
        val attribute = Attribute.customNumber(name)
        apply(if (value == null) attribute.withValueReset() else attribute.withValue(value.toDouble()))
    }

    /** Строковый атрибут; пустое и `null` — атрибут снимается, по той же причине. */
    private fun UserProfile.Builder.text(name: String, value: String?) {
        val attribute = Attribute.customString(name)
        val trimmed = value?.trim().orEmpty()
        apply(if (trimmed.isEmpty()) attribute.withValueReset() else attribute.withValue(trimmed))
    }

    /**
     * Ведущий вид птицы хозяйства — тот, которого заложено больше всего яиц.
     *
     * По яйцам, а не по числу закладок: три закладки перепелов по двадцать яиц не
     * делают хозяйство перепелиным рядом с одной куриной на сто пятьдесят.
     *
     * Считается по закладкам напрямую, а не по [IncubatorStats.bySpecies]: тот разрез
     * собран только из завершённых закладок (так его показывает вкладка «Статистика»),
     * а ведущий вид — вопрос про заложенное, и идущая закладка на него отвечает.
     * Имя вторым ключом — чтобы при равенстве ответ не прыгал от запуска к запуску.
     */
    private fun mainSpecies(batches: List<Batch>): String? =
        batches
            .groupBy { it.type }
            .map { (type, rows) -> type to rows.sumOf { it.eggAll } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .firstOrNull()?.first

    /**
     * Основной инкубатор — тот, в котором больше всего закладок.
     *
     * Атрибут профиля один, а устройств бывает несколько, и выбирать приходится. По
     * числу закладок, потому что вопрос к этому атрибуту всегда один и тот же: «на чём
     * человек работает». Устройство, купленное и однажды попробованное, на этот вопрос
     * не отвечает.
     */
    private fun mainIncubator(incubators: List<Incubator>, batches: List<Batch>): Incubator? {
        if (incubators.isEmpty()) return null
        val byIncubator = batches.groupingBy { it.incubatorId }.eachCount()
        // Второй ключ — идентификатор: без него два устройства с равным числом
        // закладок менялись бы местами при каждом пересчёте, и атрибут дёргался бы
        // туда-сюда на ровном месте.
        return incubators.maxWithOrNull(
            compareBy<Incubator> { byIncubator[it.id] ?: 0 }.thenByDescending { it.id }
        )
    }

    /** Разрешены ли уведомления системой — то, без чего напоминания не работают вовсе. */
    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private companion object {
        // Имена атрибутов уходят в отчёт как есть — по ним же строятся сегменты,
        // поэтому они здесь константами, а не литералами по месту: опечатка в одном
        // из них завела бы второй атрибут-двойник, и разрез разъехался бы надвое.
        const val HOUSEHOLD_INCUBATORS = "Инкубаторов"
        const val HOUSEHOLD_BATCHES = "Закладок"
        const val HOUSEHOLD_ACTIVE_BATCHES = "Закладок идёт"
        const val HOUSEHOLD_FINISHED_BATCHES = "Закладок завершено"
        const val HOUSEHOLD_STOPPED_BATCHES = "Закладок прервано"
        const val HOUSEHOLD_EGGS = "Яиц заложено"
        const val HOUSEHOLD_HATCHED = "Птенцов выведено"
        const val HOUSEHOLD_CUSTOM_SPECIES = "Своих видов"
        const val HATCH_RATE = "Эффективность"
        const val SPECIES = "Основной вид"
        const val BRAND = "Бренд"
        const val MODEL = "Модель"
        const val REMINDERS = "Напоминания"
        const val THEME = "Тема"
        const val TEMPERATURE_UNIT = "Градусы"
        const val CURRENCY = "Валюта"
        const val VERSION = "Версия"
        const val FIRST_VERSION = "Первая версия"
    }
}
