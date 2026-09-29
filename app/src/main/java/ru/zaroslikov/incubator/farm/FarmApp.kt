package ru.zaroslikov.incubator.farm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Что «Моё хозяйство» на этом телефоне может сделать с птенцами.
 *
 * - [Absent] — не установлено, и предлагать нечего;
 * - [Outdated] — стоит версия до договора: она ловит только `myferma://template`, ссылка
 *   [FarmLink] на неё не резолвится. Открывать такое хозяйство бессмысленно — оно
 *   проигнорирует птенцов, — зато можно предложить его обновить;
 * - [Ready] — ссылка находит активность, форма откроется.
 */
enum class FarmStatus { Absent, Outdated, Ready }

/**
 * «Моё хозяйство» на этом телефоне: есть ли, умеет ли принять птенцов, и как их отдать.
 *
 * «Умеет» — значит, найдётся ли активность на саму ссылку [FarmLink], а не просто
 * «пакет есть»: разница между ними и есть [FarmStatus.Outdated]. С Android 11 чужой
 * пакет виден только объявленным в манифесте (`<queries><package …/>`), и имя там
 * повторено дословно — манифест констант Kotlin не видит.
 */
object FarmApp {

    /**
     * Страница хозяйства в RuStore — туда ведёт «Обновить». Тот же вид ссылки, что у
     * страницы самого Инкубатора в `ReviewController`: её открывает приложение RuStore,
     * а без него — браузер.
     */
    const val STORE_URL = "https://apps.rustore.ru/app/${FarmLink.PACKAGE}"

    fun intentFor(chicks: FarmChicks): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(FarmLink.encode(chicks))).setPackage(FarmLink.PACKAGE)

    fun status(context: Context): FarmStatus {
        val pm = context.packageManager
        return try {
            if (!isInstalled(pm)) return FarmStatus.Absent
            // Пробная ссылка того же вида: фильтр хозяйства сверяет схему, хост и путь, а
            // параметры запроса на выбор активности не влияют.
            val probe = intentFor(FarmChicks(type = "", name = "", breed = "", count = 1, date = ""))
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(probe, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(probe, 0)
            }
            if (resolved != null) FarmStatus.Ready else FarmStatus.Outdated
        } catch (_: RuntimeException) {
            FarmStatus.Absent
        }
    }

    private fun isInstalled(pm: PackageManager): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(FarmLink.PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(FarmLink.PACKAGE, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Открывает форму в хозяйстве; `false` — открыть не вышло (удалили между делом). */
    fun send(context: Context, chicks: FarmChicks): Boolean = start(context, intentFor(chicks))

    /** Открывает страницу хозяйства в магазине; `false` — открыть нечем. */
    fun openStorePage(context: Context): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL)))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
