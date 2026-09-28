package ru.zaroslikov.incubator.ui.qr

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.domain.model.Incubator
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.qr.QrBitmap
import ru.zaroslikov.incubator.qr.QrLink
import ru.zaroslikov.incubator.qr.QrModules
import ru.zaroslikov.incubator.qr.appIconBitmap
import ru.zaroslikov.incubator.qr.qrModules
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import java.io.File

@Immutable
data class IncubatorQrState(
    /** Чей код загружен; ноль — ничей. Нужен, чтобы отличить повторный `Load` от нового. */
    val incubatorId: Long = 0L,
    /** Инкубатор, чей код показан; `null` до ответа базы или если его удалили. */
    val incubator: Incubator? = null,
    /** Сетка кода — считается один раз на инкубатор, экран только рисует её. */
    val modules: QrModules? = null,
    /** База ответила хотя бы раз: до этого шторка показывает колесо, а не пустоту. */
    val loaded: Boolean = false,
    /** Файл пишется — кнопки на это время погашены, чтобы не писать его дважды. */
    val busy: Boolean = false,
)

sealed interface IncubatorQrIntent {
    data class Load(val incubatorId: Long) : IncubatorQrIntent

    /** Пишет PNG в место, выбранное в системном окне «Сохранить». */
    data class SaveTo(val target: Uri) : IncubatorQrIntent

    /** Готовит PNG в кэше и просит экран открыть окно «Поделиться». */
    data object Share : IncubatorQrIntent
}

sealed interface IncubatorQrEffect {
    data object Saved : IncubatorQrEffect
    data class ReadyToShare(val uri: Uri) : IncubatorQrEffect
    data class Failed(val message: String) : IncubatorQrEffect
}

/**
 * Шторка «QR-код инкубатора»: код, его картинка на печать и её отправка.
 *
 * Сетка кода зависит только от идентификатора и строится один раз в [load], на
 * `Dispatchers.Default`: ZXing считает её за миллисекунды, но считать её на главном
 * потоке при каждой перерисовке всё равно незачем. Название подписывается под кодом
 * в момент записи файла — оно берётся из текущего ответа базы, так что переименованный
 * инкубатор получает файл с новым именем, а код в нём тот же.
 *
 * Запись файла — здесь, а не в контроллере из `AppContainer`, как у расписания: файл
 * — сотня килобайт и пишется за доли секунды, а `viewModelScope` переживает и поворот,
 * и закрытие шторки, пока жив экран под ней. Закрыть экран инкубатора за эти доли
 * секунды можно, но тогда речь идёт о PNG-картинке, которую нажмут заново, а не о
 * файле базы, который нельзя оставить пустым.
 */
class IncubatorQrViewModel(
    private val application: Application,
    private val itemsRepository: ItemsRepository,
) : StatefulMviViewModel<IncubatorQrState, IncubatorQrIntent, IncubatorQrEffect>(IncubatorQrState()) {

    private var loadJob: Job? = null

    override fun onIntent(intent: IncubatorQrIntent) {
        when (intent) {
            is IncubatorQrIntent.Load -> load(intent.incubatorId)
            is IncubatorQrIntent.SaveTo -> write(intent.target)
            IncubatorQrIntent.Share -> share()
        }
    }

    private fun load(incubatorId: Long) {
        // Тот же инкубатор — повторный `Load` после поворота, и сбрасывать нечего: сетка
        // та же, подписка на базу жива, а `busy` идущей записи должен пережить поворот,
        // иначе кнопки оживут посреди записи и вторая начнётся поверх первой.
        if (current.incubatorId == incubatorId && loadJob?.isActive == true) return
        loadJob?.cancel()
        reduce { IncubatorQrState(incubatorId = incubatorId, busy = busy) }
        loadJob = viewModelScope.launch {
            val modules = withContext(Dispatchers.Default) { qrModules(QrLink.encode(incubatorId)) }
            reduce { copy(modules = modules) }
            itemsRepository.getIncubator(incubatorId).collect { incubator ->
                reduce { copy(incubator = incubator, loaded = true) }
            }
        }
    }

    /**
     * `openOutputStream` отдаёт `null`, когда провайдер документа дал место, в которое
     * писать нельзя, — это отказ, а не исключение, и обрабатывается как отказ.
     */
    private fun write(target: Uri) {
        val bytes = pngOrNull() ?: return
        reduce { copy(busy = true) }
        viewModelScope.launch {
            val result = runCatchingIo {
                val stream = application.contentResolver.openOutputStream(target)
                    ?: error("файл недоступен для записи")
                stream.use { it.write(bytes()) }
            }
            reduce { copy(busy = false) }
            sendEffect(
                result.fold(
                    onSuccess = { IncubatorQrEffect.Saved },
                    onFailure = { IncubatorQrEffect.Failed(failureText(it, "сохранить")) },
                )
            )
        }
    }

    /**
     * Кладёт картинку в свой подкаталог кэша — внутри `exports/`, который отдаёт наружу
     * FileProvider (см. `file_paths.xml`), — и чистит его перед записью: нужен ровно
     * один файл, тот, что отправляют.
     */
    private fun share() {
        val bytes = pngOrNull() ?: return
        val name = current.incubator?.name.orEmpty()
        reduce { copy(busy = true) }
        viewModelScope.launch {
            val result = runCatchingIo {
                    val dir = File(application.cacheDir, SHARE_DIR)
                    dir.mkdirs()
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, fileNameFor(name))
                    file.writeBytes(bytes())
                    FileProvider.getUriForFile(
                        application,
                        "${application.packageName}.fileprovider",
                        file,
                    )
            }
            reduce { copy(busy = false) }
            sendEffect(
                result.fold(
                    onSuccess = { IncubatorQrEffect.ReadyToShare(it) },
                    onFailure = { IncubatorQrEffect.Failed(failureText(it, "подготовить")) },
                )
            )
        }
    }

    /** Отложенная сборка PNG по текущему состоянию, либо `null`, когда собирать нечего. */
    private fun pngOrNull(): (() -> ByteArray)? {
        val state = current
        if (state.busy) return null
        val modules = state.modules ?: return null
        val label = state.incubator?.name.orEmpty()
        // Значок берётся здесь, до ухода с главного потока: `getApplicationIcon` — обращение
        // к системе, и оно синхронное и быстрое, а рисовать растр всё равно на IO.
        val logo = appIconBitmap(application, QrBitmap.logoSizePx(modules))
        return { QrBitmap.png(modules, label, logo) }
    }

    /**
     * `runCatching` на IO, который не глотает отмену: `runCatching` ловит и
     * `CancellationException`, и уничтоженная посреди записи ViewModel отчитывалась бы
     * «не удалось» в канал, который никто не читает, — а отмена должна остаться отменой.
     */
    private suspend fun <T> runCatchingIo(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) { block() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun failureText(error: Throwable, verb: String): String {
        val detail = error.message ?: "неизвестная ошибка"
        return "Не удалось $verb картинку с QR-кодом: $detail"
    }

    companion object {
        /** Подкаталог кэша для отправки; лежит внутри `exports/`, который знает `file_paths.xml`. */
        private const val SHARE_DIR = "exports/qr"

        /**
         * Имя файла: «QR-код — Блиц 72.png». Символы, запрещённые в именах файлов,
         * заменяются пробелом — то же правило, что у файла расписания.
         */
        fun fileNameFor(incubatorName: String): String {
            val name = incubatorName
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(60)
            return if (name.isBlank()) "QR-код инкубатора.png" else "QR-код — $name.png"
        }
    }
}
