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
import ru.zaroslikov.incubator.qr.QrDetails
import ru.zaroslikov.incubator.qr.QrLink
import ru.zaroslikov.incubator.qr.QrModules
import ru.zaroslikov.incubator.qr.appIconBitmap
import ru.zaroslikov.incubator.qr.qrModules
import ru.zaroslikov.incubator.ui.mvi.StatefulMviViewModel
import ru.zaroslikov.incubator.ui.start.modelLine
import java.io.File

/**
 * Что стоит под кодом. [Compact] — название, как было всегда: наклейка на прибор, которую
 * читают камерой. [Detailed] добавляет бренд с моделью и примечание инкубатора — для листа,
 * который вешают рядом с прибором и читают глазами («левый лоток греет хуже», «вода —
 * через день»). Код в обоих режимах один и тот же, меняется только подпись под ним.
 */
enum class QrMode { Compact, Detailed }

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
    /**
     * Режим подписи — и на экране, и в картинке: примечание, показанное на карточке, уходит и в
     * сохранённый файл. Переживает
     * смену инкубатора — это выбор человека о том, что он печатает, а не свойство прибора.
     */
    val mode: QrMode = QrMode.Compact,
) {
    /**
     * Режим, который действительно рисуется и печатается: без примечания «Подробно» не
     * существует, и выбранный когда-то режим на инкубаторе без заметок читается как «Кратко».
     * Сам [mode] при этом не сбрасывается — выбор вернётся, как только примечание появится.
     */
    val effectiveMode: QrMode
        get() = if (incubator?.note.isNullOrBlank()) QrMode.Compact else mode
}

sealed interface IncubatorQrIntent {
    data class Load(val incubatorId: Long) : IncubatorQrIntent

    /** Пишет PNG в место, выбранное в системном окне «Сохранить». */
    data class SaveTo(val target: Uri) : IncubatorQrIntent

    /** Готовит PNG в кэше и просит экран открыть окно «Поделиться». */
    data object Share : IncubatorQrIntent

    /** «Кратко» или «Подробно» — что подписать под кодом. */
    data class SetMode(val mode: QrMode) : IncubatorQrIntent
}

sealed interface IncubatorQrEffect {
    data object Saved : IncubatorQrEffect
    data class ReadyToShare(val uri: Uri) : IncubatorQrEffect
    data class Failed(val message: String) : IncubatorQrEffect
}

/**
 * Шторка «QR-код инкубатора»: код, его картинка на печать и её отправка. Сетка зависит только от
 * идентификатора и строится один раз в [load] на `Dispatchers.Default`. Название подписывается под
 * кодом при записи файла — из текущего ответа базы. PNG пишется здесь, а не в контроллере
 * `AppContainer`: это сто килобайт, и недописанный файл повторяют нажатием.
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
            is IncubatorQrIntent.SetMode -> reduce { copy(mode = intent.mode) }
        }
    }

    private fun load(incubatorId: Long) {
        // Тот же инкубатор — повторный `Load` после поворота, и сбрасывать нечего: сетка
        // та же, подписка на базу жива, а `busy` идущей записи должен пережить поворот,
        // иначе кнопки оживут посреди записи и вторая начнётся поверх первой.
        if (current.incubatorId == incubatorId && loadJob?.isActive == true) return
        loadJob?.cancel()
        reduce { IncubatorQrState(incubatorId = incubatorId, busy = busy, mode = mode) }
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
        val incubator = state.incubator
        val label = incubator?.name.orEmpty()
        val details = if (state.effectiveMode == QrMode.Detailed && incubator != null) {
            QrDetails(subtitle = modelLine(incubator.brand, incubator.model), note = incubator.note)
        } else null
        // Значок берётся здесь, до ухода с главного потока: `getApplicationIcon` — обращение
        // к системе, и оно синхронное и быстрое, а рисовать растр всё равно на IO.
        val logo = appIconBitmap(application, QrBitmap.logoSizePx(modules))
        return { QrBitmap.png(modules, label, logo, details) }
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
