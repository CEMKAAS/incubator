package ru.zaroslikov.incubator.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.zaroslikov.incubator.data.ImportResult
import ru.zaroslikov.incubator.data.exportDatabase
import ru.zaroslikov.incubator.data.importDatabase
import ru.zaroslikov.incubator.data.wipeDatabase
import ru.zaroslikov.incubator.vkid.VkId
import java.io.File
import kotlin.system.exitProcess

/**
 * Чем сейчас занят перенос базы и что он хочет сказать.
 *
 * Отдельное состояние, а не пара булевых флагов: у переноса пять исходов, и говорить о
 * них надо по-разному. Два из них — [Imported] и [FailedNeedsRestart] — вдобавок
 * обязывают перезапустить процесс.
 */
sealed interface TransferState {
    data object Idle : TransferState

    /** Идёт чтение или запись файла. Кнопки на это время выключены. */
    data object Working : TransferState

    /** Копия сохранена. Ничего перезапускать не нужно. */
    data object Exported : TransferState

    /**
     * Копия готова и лежит в кэше; её [uri] ждёт системного окна «Поделиться».
     *
     * Не «готово», а «сделай следующий шаг»: показать это окно может только экран, у
     * контроллера нет активности, из которой запускают намерение. Диалога у состояния
     * поэтому нет — экран забирает [uri], отдаёт его системе и сбрасывает состояние
     * сам. Если экран к этому моменту закрыт, состояние просто дождётся возвращения:
     * файл в кэше, ничего не потеряно и ничего не подменено.
     */
    data class ReadyToShare(val uri: Uri) : TransferState

    /**
     * База удалена. Как и после импорта, соединение с ней закрыто, а живые ViewModel
     * говорят о данных, которых больше нет, — процесс перезапускается сам.
     */
    data object Wiped : TransferState

    /**
     * База заменена. Соединение с прежней закрыто, и всё, что успело её прочитать, —
     * открытые потоки, живые ViewModel — говорит о данных, которых больше нет.
     * Единственный выход отсюда — перезапуск процесса, и он произойдёт сам.
     */
    data object Imported : TransferState

    /**
     * Замена сорвалась, файл вернули на место — но соединение с базой уже закрыто.
     *
     * Отдельно от [Failed] потому, что на диске данные целы, а процесс работать дальше
     * не может: первый же экран за диалогом упал бы. Перезапуск здесь такой же
     * обязательный, как и после успеха.
     */
    data class FailedNeedsRestart(val message: String) : TransferState

    /** Не вышло, и ничего не тронуто. [message] приходит из `:data` уже человеческим. */
    data class Failed(val message: String) : TransferState
}

/**
 * Перенос базы — вне жизни экрана, и это главное в этом классе.
 *
 * Раньше и экспорт, и импорт жили в `viewModelScope` экрана настроек, и там пряталась
 * дыра, которую видно только в работе. Достаточно было нажать «назад» через секунду
 * после выбора файла: `importDatabase` — обычная блокирующая функция без точек
 * приостановки, поэтому отмена корутины её не рвала, и подмена доходила до конца, — а
 * вот записать итог было уже некуда, ViewModel к тому времени уничтожена. База
 * заменена, соединение с прежней закрыто, диалога с перезапуском никто не показал, и
 * приложение продолжало работать до первого обращения к базе, то есть до падения.
 *
 * Поэтому перенос принадлежит приложению, а не экрану: его область жизни переживает и
 * поворот, и уход назад, и закрытие экрана настроек. Экран лишь подписывается на
 * [state] и показывает то, что там есть.
 *
 * **Перезапуск после удавшегося импорта безусловен и не спрашивает.** Кнопка «ладно, я
 * останусь» здесь была бы обещанием, которого приложение не может выполнить: работать
 * на закрытой базе оно не умеет. Поэтому диалог сообщает, а не спрашивает, и через
 * [RESTART_DELAY_MILLIS] процесс умирает — задержки хватает, чтобы прочитать строку, и
 * она же не даёт перезапуску выглядеть падением.
 */
class DataTransferController(
    private val application: Application,
    /** Выход из аккаунта: «удалить всё» стирает профиль, а без сессии профиля не бывает. */
    private val signOut: () -> Unit = {},
) {

    /**
     * `SupervisorJob` и `Dispatchers.IO`: работа файловая, и сбой одной попытки не
     * должен уносить область целиком — за первой попыткой бывает вторая.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    /** Убирает сообщение об исходе. Перезапуск после импорта этим не отменяется. */
    fun clear() {
        val current = _state.value
        // Ждущие перезапуска исходы не сбрасываются: убрать диалог значило бы вернуть
        // человека к экранам, которые сейчас упадут.
        if (current is TransferState.Imported ||
            current is TransferState.Wiped ||
            current is TransferState.FailedNeedsRestart
        ) return
        _state.value = TransferState.Idle
    }

    /**
     * Пишет копию базы в выбранный пользователем файл.
     *
     * `openOutputStream` возвращает `null`, когда провайдер документа отдал место, в
     * которое писать нельзя. Это не исключение, а обычный отказ, и обработан он как
     * отказ — иначе экран молча решил бы, что копия сохранена.
     */
    fun export(target: Uri) {
        if (_state.value is TransferState.Working) return
        _state.value = TransferState.Working
        scope.launch {
            val result = runCatching {
                val stream = application.contentResolver.openOutputStream(target)
                    ?: error("файл недоступен для записи")
                stream.use { exportDatabase(application, it) }
            }
            _state.value = result.fold(
                onSuccess = { TransferState.Exported },
                onFailure = { TransferState.Failed(failureText(it, "сохранить")) },
            )
        }
    }

    /**
     * Готовит копию базы к отправке в другое приложение и переводит состояние в
     * [TransferState.ReadyToShare].
     *
     * Копия пишется в кэш, а не отдаётся из рабочего файла напрямую, по двум причинам.
     * Рабочая база живёт в приватном каталоге, которого [androidx.core.content.FileProvider]
     * не отдаёт и отдавать не должен; и она открыта на запись — чужое приложение
     * получило бы её в неизвестном состоянии, без контрольной точки WAL, то есть без
     * последних замеров. Копия же снята [exportDatabase] со всеми его гарантиями.
     *
     * Каталог перед записью чистится: имя файла содержит дату, так что за месяц копий
     * набралось бы столько же, сколько отправок, и все они молча заняли бы место в
     * кэше. Нужна ровно одна — та, которую отправляют сейчас.
     */
    fun shareCopy(fileName: String) {
        if (_state.value is TransferState.Working) return
        _state.value = TransferState.Working
        scope.launch {
            val result = runCatching {
                val dir = File(application.cacheDir, SHARE_DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, fileName)
                file.outputStream().use { exportDatabase(application, it) }
                FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    file,
                )
            }
            _state.value = result.fold(
                onSuccess = { TransferState.ReadyToShare(it) },
                onFailure = { TransferState.Failed(failureText(it, "подготовить")) },
            )
        }
    }

    /**
     * Удаляет всю базу и перезапускает приложение.
     *
     * Спрашивает не здесь: подтверждение берёт экран, до вызова. Контроллеру остаётся
     * работа, которую нельзя бросить на полпути, — и по той же причине, что и импорт,
     * она живёт вне экрана: удаление, начатое отсюда, доходит до конца даже если экран
     * закрыли, и перезапустить приложение после него есть кому.
     *
     * Отказ здесь — тоже [TransferState.FailedNeedsRestart], а не [TransferState.Failed]:
     * `wipeDatabase` закрывает соединение первым делом, поэтому что бы дальше ни пошло
     * не так, живой процесс работать на закрытой базе уже не сможет.
     */
    fun wipe() {
        if (_state.value is TransferState.Working) return
        _state.value = TransferState.Working
        scope.launch {
            // «Удалить всё» стирает и профиль, а он есть только у вошедшего: выход из
            // аккаунта — здесь же, иначе после перезапуска вход остался бы без профиля.
            // До удаления базы и без записи в неё: файл сейчас уйдёт целиком, а запись в
            // закрытую базу уронила бы процесс раньше обещанного перезапуска.
            signOut()
            val next = runCatching { wipeDatabase(application) }.fold(
                onSuccess = { TransferState.Wiped },
                onFailure = {
                    TransferState.FailedNeedsRestart(
                        "Не удалось удалить данные: ${it.message ?: "неизвестная ошибка"}. " +
                            "Приложение нужно перезапустить."
                    )
                },
            )
            _state.value = next
            // Токен VK лежит не в базе, а у SDK.
            // Выход — попутно с паузой перед перезапуском и не дольше неё: оставшийся
            // токен безвреден (следующий вход его перепишет), а задерживать перезапуск
            // ради сети незачем.
            launch { withTimeoutOrNull(RESTART_DELAY_MILLIS) { VkId.logout() } }
            delay(RESTART_DELAY_MILLIS)
            withContext(Dispatchers.Main) { restart() }
        }
    }

    /** Заменяет базу выбранным файлом. Проверки и подмена — целиком в `:data`. */
    fun import(source: Uri) {
        if (_state.value is TransferState.Working) return
        _state.value = TransferState.Working
        scope.launch {
            val result = runCatching {
                val stream = application.contentResolver.openInputStream(source)
                    ?: error("файл недоступен для чтения")
                stream.use { importDatabase(application, it) }
            }
            val next = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is ImportResult.Success -> TransferState.Imported
                        is ImportResult.Rejected -> TransferState.Failed(outcome.reason)
                        is ImportResult.RestoredNeedsRestart ->
                            TransferState.FailedNeedsRestart(outcome.reason)
                    }
                },
                onFailure = { TransferState.Failed(failureText(it, "прочитать")) },
            )
            _state.value = next

            if (next is TransferState.Imported || next is TransferState.FailedNeedsRestart) {
                delay(RESTART_DELAY_MILLIS)
                withContext(Dispatchers.Main) { restart() }
            }
        }
    }

    /**
     * Перезапускает приложение.
     *
     * Именно смерть процесса, а не пересоздание активности: базу держит синглтон в
     * `:data`, репозиторий — контейнер приложения, а данные из прежней базы — все живые
     * ViewModel и их подписки. Смерть процесса снимает всё это разом и ничего не
     * забывает.
     *
     * `exitProcess` вызывается безусловно, даже когда запустить себя обратно нечем:
     * остаться живым — худший из двух исходов. Человек откроет приложение сам, а вот
     * падение на первом же списке выглядело бы как съеденные импортом данные.
     */
    private fun restart() {
        val launch = application.packageManager.getLaunchIntentForPackage(application.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            try {
                application.startActivity(launch)
            } catch (e: Exception) {
                // Открыть себя заново не вышло — умираем всё равно.
            }
        }
        exitProcess(0)
    }

    private fun failureText(error: Throwable, verb: String): String {
        val detail = error.message ?: "неизвестная ошибка"
        return "Не удалось $verb файл: $detail"
    }

    private companion object {
        /** Сколько диалог успевает побыть на экране до перезапуска. */
        const val RESTART_DELAY_MILLIS = 2_200L

        /** Подкаталог кэша, из которого FileProvider отдаёт копию наружу. */
        const val SHARE_DIR = "exports"
    }
}
