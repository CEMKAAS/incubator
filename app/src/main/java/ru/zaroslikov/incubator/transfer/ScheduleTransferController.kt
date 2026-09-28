package ru.zaroslikov.incubator.transfer

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.zaroslikov.incubator.BuildConfig
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.exportable
import ru.zaroslikov.incubator.domain.repository.ItemsRepository
import ru.zaroslikov.incubator.ui.batch.averagedScheduleOf
import ru.zaroslikov.incubator.ui.todayText
import java.io.File

/** Чем занят экспорт расписания и что он хочет сказать. Пара к `TransferState` базы. */
sealed interface ScheduleTransferState {
    data object Idle : ScheduleTransferState

    /** Файл собирается или пишется. Меню на это время ничего не запускает второй раз. */
    data object Working : ScheduleTransferState

    /** Файл записан туда, куда указал человек. */
    data object Saved : ScheduleTransferState

    /**
     * Файл лежит в кэше и ждёт системного окна «Поделиться». Показать его может только
     * экран — у контроллера нет активности; экран забирает [uri] и сбрасывает состояние.
     */
    data class ReadyToShare(val uri: Uri) : ScheduleTransferState

    data class Failed(val message: String) : ScheduleTransferState
}

/**
 * Экспорт и чтение файла расписания — вне жизни экрана, по той же причине, что и
 * `DataTransferController`: запись файла, начатая из меню карточки, должна дойти до
 * конца, даже если экран инкубатора успели закрыть, — иначе в выбранном месте останется
 * пустой файл с именем закладки. Работа здесь короткая (файл — килобайты), но длина не
 * отменяет правила, а место, откуда пишут файл на диск, в приложении одно.
 *
 * Чтение ([read]) — обычная `suspend`-функция, а не состояние: результат нужен форме
 * закладки прямо сейчас, в её `viewModelScope`, и если форму закрыли, не дождавшись, —
 * файл просто не применён, терять там нечего.
 */
class ScheduleTransferController(
    private val application: Application,
    private val itemsRepository: ItemsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ScheduleTransferState>(ScheduleTransferState.Idle)
    val state: StateFlow<ScheduleTransferState> = _state.asStateFlow()

    fun clear() {
        if (_state.value !is ScheduleTransferState.Working) _state.value = ScheduleTransferState.Idle
    }

    /**
     * Пишет расписание закладки в выбранное человеком место.
     *
     * `openOutputStream` отдаёт `null`, когда провайдер документа дал место, в которое
     * писать нельзя, — это отказ, а не исключение, и обрабатывается как отказ.
     */
    fun saveToFile(batch: Batch, target: Uri) {
        if (_state.value is ScheduleTransferState.Working) return
        _state.value = ScheduleTransferState.Working
        scope.launch {
            val result = runCatching {
                val bytes = ScheduleFileCodec.encode(exportOf(batch))
                val stream = application.contentResolver.openOutputStream(target)
                    ?: error("файл недоступен для записи")
                stream.use { it.write(bytes) }
            }
            _state.value = result.fold(
                onSuccess = { ScheduleTransferState.Saved },
                onFailure = { ScheduleTransferState.Failed(failureText(it, "сохранить")) },
            )
        }
    }

    /**
     * Готовит файл к отправке в другое приложение. Кладёт его в свой подкаталог кэша
     * ([SHARE_DIR], внутри того же `exports/`, который отдаёт наружу FileProvider) и
     * чистит подкаталог перед записью: нужен ровно один файл — тот, что отправляют.
     */
    fun share(batch: Batch) {
        if (_state.value is ScheduleTransferState.Working) return
        _state.value = ScheduleTransferState.Working
        scope.launch {
            val result = runCatching {
                val bytes = ScheduleFileCodec.encode(exportOf(batch))
                val dir = File(application.cacheDir, SHARE_DIR)
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, fileNameFor(batch))
                file.writeBytes(bytes)
                FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    file,
                )
            }
            _state.value = result.fold(
                onSuccess = { ScheduleTransferState.ReadyToShare(it) },
                onFailure = { ScheduleTransferState.Failed(failureText(it, "подготовить")) },
            )
        }
    }

    /**
     * Читает файл расписания. Бросает [ScheduleFileException] с готовым текстом, когда
     * файл не тот, повреждён или от более новой версии, и обычное исключение, когда его
     * не удалось прочитать вовсе.
     */
    suspend fun read(source: Uri): ScheduleExport = withContext(Dispatchers.IO) {
        val bytes = application.contentResolver.openInputStream(source)
            ?.use { it.readBytes() }
            ?: throw ScheduleFileException("Файл недоступен для чтения.")
        if (bytes.size > MAX_FILE_BYTES) {
            // Файл расписания — килобайты; мегабайты сюда мог принести только не тот файл.
            throw ScheduleFileException("Это не файл расписания приложения: он слишком большой.")
        }
        ScheduleFileCodec.decode(bytes)
    }

    /**
     * Собирает файл из базы: вид закладки, её дни, среднее по её замерам, если вид
     * свой — его описание, и на показ — породу с брендом и моделью инкубатора.
     * Только для удачно завершённой закладки ([Batch.exportable]) — правило одно на
     * меню карточки и на этот вход, чтобы обойти его нельзя было ни с той, ни с другой
     * стороны.
     */
    private suspend fun exportOf(batch: Batch): ScheduleExport {
        check(batch.exportable) { "экспортируется только расписание удачно завершённой закладки" }
        val plan = itemsRepository.getValueArchive(batch.id).sortedBy { it.day }
        check(plan.isNotEmpty()) { "у закладки нет расписания по дням" }
        // Среднее по замерам считается здесь, пока дни ещё со своими идентификаторами:
        // замер знает свой день только через Value.id.
        val measurements = itemsRepository.getBatchMeasurements(batch.id).first()
        val fact = if (measurements.isEmpty()) null else averagedScheduleOf(plan, measurements)
        val species = itemsRepository.getCustomSpecies().first()
            .firstOrNull { it.name == batch.type }
        // Инкубатора может уже не быть — устройство удалили, закладка осталась в
        // выгрузке; тогда бренд и модель просто пустые, как у неуказанных.
        val incubator = itemsRepository.getIncubator(batch.incubatorId).first()
        return ScheduleExport(
            type = batch.type,
            plan = plan.map { it.copy(id = 0, idPT = 0) },
            fact = fact?.map { it.copy(id = 0, idPT = 0) },
            measurementCount = measurements.size,
            customSpecies = species,
            breed = batch.breed,
            incubatorBrand = incubator?.brand?.trim().orEmpty(),
            incubatorModel = incubator?.model?.trim().orEmpty(),
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = todayText(),
        )
    }

    private fun failureText(error: Throwable, verb: String): String {
        val detail = error.message ?: "неизвестная ошибка"
        return "Не удалось $verb файл расписания: $detail"
    }

    companion object {
        /** Подкаталог кэша для отправки; лежит внутри `exports/`, который знает `file_paths.xml`. */
        private const val SHARE_DIR = "exports/schedules"

        /** Файл расписания — единицы килобайт; потолок с большим запасом. */
        private const val MAX_FILE_BYTES = 4 * 1024 * 1024

        /**
         * Имя файла: «Расписание Курицы — Весенняя партия.incs». Вид — первым, потому
         * что файл про него: в списке файлов его будут искать по птице. Название закладки
         * — чтобы два расписания одной птицы различались. Символы, запрещённые в именах
         * файлов, заменяются пробелом.
         */
        fun fileNameFor(batch: Batch): String {
            fun clean(s: String) = s
                .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val type = clean(batch.type).take(40)
            val title = clean(batch.title).take(40)
            val name = if (title.isBlank() || title == type) type else "$type — $title"
            return "Расписание $name.${ScheduleFileCodec.EXTENSION}"
        }
    }
}
