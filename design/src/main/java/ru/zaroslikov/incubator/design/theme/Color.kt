package ru.zaroslikov.incubator.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF3F7D5C)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFCDEDA3)
val onPrimaryContainerLight = Color(0xFF102000)
val secondaryLight = Color(0xFF586249)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDCE7C8)
val onSecondaryContainerLight = Color(0xFF151E0B)
val tertiaryLight = Color(0xFF3F6836)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBFEFB1)
val onTertiaryContainerLight = Color(0xFF002201)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFFAF8F3)
val onBackgroundLight = Color(0xFF2B2620)
val surfaceLight = Color(0xFFFAF8F3)
val onSurfaceLight = Color(0xFF2B2620)
val surfaceVariantLight = Color(0xFFE1E4D5)
val onSurfaceVariantLight = Color(0xFF8A8072)
val outlineLight = Color(0xFFECE5D8)
val outlineVariantLight = Color(0xFFECE5D8)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2F312A)
val inverseOnSurfaceLight = Color(0xFFF1F2E6)
val inversePrimaryLight = Color(0xFFB1D18A)
val surfaceDimLight = Color(0xFFDADBD0)
val surfaceBrightLight = Color(0xFFF9FAEF)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFFFFFFF)
val surfaceContainerLight = Color(0xFFFFFFFF)
val surfaceContainerHighLight = Color(0xFFE8E9DE)
val surfaceContainerHighestLight = Color(0xFFE2E3D8)

// Тёмная схема Material 3 — тёплая, родня кремово-зелёной светлой (см. [DarkDesignColors]).
// Сгенерированная зеленовато-чёрная схема, что стояла здесь раньше, не была ни тёмной
// версией макета, ни чем-то, что кто-нибудь видел: приложение всегда рисовалось светлым.
val primaryDark = Color(0xFF6DB48C)
val onPrimaryDark = Color(0xFF0E2A1B)
val primaryContainerDark = Color(0xFF2E5C44)
val onPrimaryContainerDark = Color(0xFFCDEDA3)
val secondaryDark = Color(0xFFBFCBAD)
val onSecondaryDark = Color(0xFF2A331E)
val secondaryContainerDark = Color(0xFF404A33)
val onSecondaryContainerDark = Color(0xFFDCE7C8)
val tertiaryDark = Color(0xFFA4D396)
val onTertiaryDark = Color(0xFF10380C)
val tertiaryContainerDark = Color(0xFF275021)
val onTertiaryContainerDark = Color(0xFFBFEFB1)
val errorDark = Color(0xFFE07B66)
val onErrorDark = Color(0xFF3A1410)
val errorContainerDark = Color(0xFF3A231E)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF161411)
val onBackgroundDark = Color(0xFFEDE7DC)
val surfaceDark = Color(0xFF161411)
val onSurfaceDark = Color(0xFFEDE7DC)
val surfaceVariantDark = Color(0xFF2A2622)
val onSurfaceVariantDark = Color(0xFFA39A8B)
val outlineDark = Color(0xFF2E2A24)
val outlineVariantDark = Color(0xFF2E2A24)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFEDE7DC)
val inverseOnSurfaceDark = Color(0xFF2B2620)
val inversePrimaryDark = Color(0xFF3F7D5C)
val surfaceDimDark = Color(0xFF161411)
val surfaceBrightDark = Color(0xFF3B3631)
val surfaceContainerLowestDark = Color(0xFF110F0D)
val surfaceContainerLowDark = Color(0xFF201D19)
val surfaceContainerDark = Color(0xFF201D19)
val surfaceContainerHighDark = Color(0xFF2A2622)
val surfaceContainerHighestDark = Color(0xFF33302A)

/**
 * Цвета макета, которым нет слота в Material 3, — по одному набору на тему.
 *
 * В файле Figma переменных-токенов нет — только сырые hex, поэтому светлые значения
 * сняты с макета напрямую. Тёмного макета не существует: [DarkDesignColors] выведен
 * из светлого по правилу «тот же смысл, обратная светлота» — кремовые плашки становятся
 * тёплыми тёмно-серыми, белые карточки — на ступень светлее фона, а акцент и красный
 * светлеют, чтобы читаться на тёмном.
 *
 * До темы это был `object` с константами; теперь это класс, и в вёрстке к нему ходят
 * через [DesignPalette] — свойство, читающее текущий набор из композиции. Имена
 * токенов не менялись нарочно: у них три сотни мест вызова, и все они остались как есть.
 */
@Immutable
data class DesignColors(
    /** Белая (в тёмной — приподнятая) поверхность карточки, поля, «таблетки». */
    val Surface: Color,
    /** Текст и значки поверх [Accent]: белый на светлой, тёмно-зелёный на тёмной. */
    val OnAccent: Color,
    val ProgressTrack: Color,
    /**
     * Кольцо прогресса у доведённой до вывода закладки — тот же зелёный, что [Accent],
     * но на ступень темнее.
     *
     * Отдельный токен, а не [Accent], потому что кольцо отвечает на два вопроса сразу:
     * сколько пройдено и чем кончилось. Полное кольцо цвета «идёт» ничем не отличалось
     * от кольца закладки на последнем дне инкубации, а это разные вещи — у одной вывод
     * уже записан, у другой ещё нет. Тёмная зелень говорит «дошло до конца», оставаясь
     * той же зеленью: исход хороший, и красный с серым заняты другими ответами.
     */
    val ProgressDone: Color,
    val Accent: Color,
    val DateEmphasis: Color,
    val CardBorder: Color,

    /** Звёздочка обязательного поля в форме инкубатора. */
    val Required: Color,

    /** Фон круглой кнопки закрытия в шторке. */
    val SheetIconButton: Color,

    /** Выбранная плитка вида птицы в форме закладки (узел 12:4783). */
    val SpeciesTileSelected: Color,

    /** Плитка-итог под полем стоимости: #F2EDE3 60 %. */
    val PriceSummarySurface: Color,

    /** Фон круглого чипа вида птицы. */
    val ChipChicken: Color,
    val ChipQuail: Color,
    val ChipGoose: Color,
    val ChipTurkey: Color,
    val ChipDuck: Color,

    /** Зелёная шапка экрана инкубатора и текст на ней. */
    val HeaderSurface: Color,
    val HeaderTitle: Color,
    val HeaderMuted: Color,
    val HeaderIcon: Color,

    /**
     * Та же шапка у инкубатора, убранного в архив.
     *
     * Зелёный в приложении означает «идёт, в работе» — им же покрашены акцент, чип
     * «Инкубация» и кнопка завершения, когда срок подошёл. Выведенное из работы
     * устройство под тем же зелёным читалось бы как действующее, поэтому шапка уходит
     * в тёплый серый — родню [StatusDoneText], нейтрального цвета «Завершено».
     * Он достаточно тёмный, чтобы белый текст на нём остался белым текстом: три цвета
     * надписей в шапке не меняются, меняется только фон под ними.
     */
    val HeaderSurfaceArchived: Color,

    /** Дорожка сегментированного переключателя вкладок и едущая по ней «таблетка». */
    val TabTrack: Color,
    val TabPill: Color,

    /**
     * Статус-чип закладки: «Инкубация» — зелёный, «Завершено» — нейтральный,
     * «Прервано» — красный.
     *
     * Прерванная закладка берёт пару [Expense] / [ExpenseSurface], те же цвета, что и
     * кнопка досрочного завершения с её плашкой «в расход»: чип — итог того самого
     * действия, и узнаётся по цвету, которым о нём предупреждали. Отдельных имён у пары
     * нет, чтобы красный в приложении оставался один.
     */
    val StatusActiveSurface: Color,
    val StatusActiveText: Color,
    val StatusDoneSurface: Color,
    val StatusDoneText: Color,

    /** Пунктирная кнопка «Добавить …» под списком. */
    val DashedSurface: Color,
    val DashedBorder: Color,

    /** Подсказка-вывод внизу вкладки «Статистика». */
    val InsightSurface: Color,

    /** Расход в «Финансах»; доход — [Accent]. */
    val Expense: Color,
    val ExpenseSurface: Color,
    val IncomeSurface: Color,

    /**
     * Столбцы диаграммы «Яйца по видам птицы». В макете цвет задан позицией
     * (виды отсортированы по убыванию), а не самим видом птицы.
     */
    val ChartBars: List<Color>,

    // --- Шторка закладки (узел 14:4893) ---

    /** Выделенная плитка «Осталось сейчас»: заливка #E6F0EA 60 % и зелёная рамка 25 %. */
    val HighlightBorder: Color,

    /** Плитка замера «Температура» / «Влажность»: #F2EDE3 60 %. */
    val MeasureTile: Color,

    /** Блок ввода замера: #F2EDE3 30 %. */
    val MeasureForm: Color,

    /** Строка записанного замера: #F2EDE3 40 %. */
    val MeasureRow: Color,

    /** Плашка режима на завтра и дорожка шкалы отклонения. */
    val PillSurface: Color,

    // --- Аналитика за день (узел 21:9088) ---

    /**
     * Линия температуры. В макете она зелёная, как и всё остальное в приложении, но
     * зелёный тут уже занят акцентом и вердиктом «в норме»; красный — цвет самой
     * величины, и рядом с синей влажностью две линии уже не перепутать.
     */
    val ChartTemp: Color,

    /** Линия влажности — вторая величина на том же графике, со своей осью справа. */
    val ChartDamp: Color,

    /** Вертикальная черта переворота. */
    val ChartTurn: Color,

    /** Черта заметки — та же черта, что у переворота, но пунктиром и приглушённая. */
    val ChartNote: Color,

    /** Закрашенное окно проветривания: [Accent] 14 %. */
    val ChartAiring: Color,

    /** Сетка графика. */
    val ChartGrid: Color,

    /** Подписи осей и времени под графиком. */
    val ChartAxisLabel: Color,

    // --- Таблица расписания в форме закладки ---

    /**
     * Заливка клетки, чей план совпадает с планом закладок, уже идущих в приборе в этот
     * день, — в пределах «в норме» плиток замера. Тот же зелёный, что у дохода и у
     * плашки «идёт»: цвет «всё в порядке» в приложении один.
     */
    val ScheduleMatch: Color,

    /**
     * Клетка с допустимым расхождением — «небольшое отклонение» плиток. Янтарный, а не
     * второй зелёный и не бледный красный: между «хорошо» и «плохо» нужен третий цвет,
     * который не читается ни как тот, ни как другой; берётся от черты переворота
     * ([ChartTurn]) — единственного янтарного, что в палитре уже есть.
     */
    val ScheduleTolerable: Color,

    /** Клетка, расходящаяся с соседями заметно, — тот же красный, что у расхода. */
    val ScheduleConflict: Color,
)

/** Светлая тема — значения сняты с макета. */
val LightDesignColors = DesignColors(
    Surface = Color(0xFFFFFFFF),
    OnAccent = Color(0xFFFFFFFF),
    ProgressTrack = Color(0xFFF2EDE3),
    ProgressDone = Color(0xFF2E5C44),
    Accent = Color(0xFF3F7D5C),
    DateEmphasis = Color(0xFF4A3410),
    CardBorder = Color(0xFFECE5D8),
    Required = Color(0xFFC25B45),
    SheetIconButton = Color(0xFFF2EDE3),
    SpeciesTileSelected = Color(0xFFFBF1DA),
    PriceSummarySurface = Color(0x99F2EDE3),
    ChipChicken = Color(0xFFFBF1DA),
    ChipQuail = Color(0xFFEFEAE0),
    ChipGoose = Color(0xFFEAF0F6),
    ChipTurkey = Color(0xFFF8E6E1),
    ChipDuck = Color(0xFFEFEAE0),
    HeaderSurface = Color(0xFF3F7D5C),
    HeaderTitle = Color(0xFFFFFFFF),
    HeaderMuted = Color(0xB3FFFFFF), // белый 70 %
    HeaderIcon = Color(0xCCFFFFFF), // белый 80 %
    HeaderSurfaceArchived = Color(0xFF6E675C),
    TabTrack = Color(0xFFF2EDE3),
    TabPill = Color(0xFFFFFFFF),
    StatusActiveSurface = Color(0xFFE6F0EA),
    StatusActiveText = Color(0xFF3F7D5C),
    StatusDoneSurface = Color(0xFFF2EDE3),
    StatusDoneText = Color(0xFF8A8072),
    DashedSurface = Color(0x80E6F0EA), // #E6F0EA 50 %
    DashedBorder = Color(0x663F7D5C), // #3F7D5C 40 %
    InsightSurface = Color(0x99E6F0EA), // #E6F0EA 60 %
    Expense = Color(0xFFC25B45),
    ExpenseSurface = Color(0xFFF8E6E1),
    IncomeSurface = Color(0xFFE6F0EA),
    ChartBars = listOf(
        Color(0xFF8A7247),
        Color(0xFFB5811F),
        Color(0xFF3F7D5C),
        Color(0xFF6E8CA8),
        Color(0xFFA8705F),
    ),
    HighlightBorder = Color(0x403F7D5C),
    MeasureTile = Color(0x99F2EDE3),
    MeasureForm = Color(0x4DF2EDE3),
    MeasureRow = Color(0x66F2EDE3),
    PillSurface = Color(0xFFF2EDE3),
    ChartTemp = Color(0xFFC25B45),
    ChartDamp = Color(0xFF3D7EA6),
    ChartTurn = Color(0xFFB5811F),
    ChartNote = Color(0xFF8A8072),
    ChartAiring = Color(0x243F7D5C),
    ChartGrid = Color(0xFFF2EDE3),
    ChartAxisLabel = Color(0xFF8A8072),
    ScheduleMatch = Color(0xFFE6F0EA),
    ScheduleTolerable = Color(0xFFFBF0D6),
    ScheduleConflict = Color(0xFFF8E6E1),
)

/**
 * Тёмная тема.
 *
 * Фон — тёплый почти чёрный (#161411, он же `backgroundDark`), карточка на ступень
 * светлее (#201D19), кремовые плашки макета — ещё на ступень (#2A2622). Акцент
 * посветлел до #6DB48C, чтобы держать контраст с фоном, и потому текст поверх него
 * тёмный ([OnAccent]); шапка же остаётся глубоко-зелёной (#2E5C44), потому что на ней
 * белые надписи, и три цвета этих надписей не меняются. Красный расхода — #E07B66,
 * по той же причине. Столбцы диаграммы и линии графика — те же оттенки, но светлее.
 */
val DarkDesignColors = DesignColors(
    Surface = Color(0xFF201D19),
    OnAccent = Color(0xFF0E2A1B),
    ProgressTrack = Color(0xFF2A2622),
    // Светлый акцент приложения: на тёмной карточке он заметно темнее здешнего #6DB48C,
    // но всё ещё держит контраст с фоном (≈3.4:1 при толщине дуги в 6 dp).
    ProgressDone = Color(0xFF3F7D5C),
    Accent = Color(0xFF6DB48C),
    DateEmphasis = Color(0xFFE8D5B0),
    CardBorder = Color(0xFF2E2A24),
    Required = Color(0xFFE07B66),
    SheetIconButton = Color(0xFF2A2622),
    SpeciesTileSelected = Color(0xFF3A3220),
    PriceSummarySurface = Color(0x992A2622),
    ChipChicken = Color(0xFF3A3220),
    ChipQuail = Color(0xFF2C2A26),
    ChipGoose = Color(0xFF24303A),
    ChipTurkey = Color(0xFF3A2622),
    ChipDuck = Color(0xFF2C2A26),
    HeaderSurface = Color(0xFF2E5C44),
    HeaderTitle = Color(0xFFFFFFFF),
    HeaderMuted = Color(0xB3FFFFFF),
    HeaderIcon = Color(0xCCFFFFFF),
    HeaderSurfaceArchived = Color(0xFF4A453E),
    TabTrack = Color(0xFF2A2622),
    TabPill = Color(0xFF3B3631),
    StatusActiveSurface = Color(0xFF1E3328),
    StatusActiveText = Color(0xFF6DB48C),
    StatusDoneSurface = Color(0xFF2A2622),
    StatusDoneText = Color(0xFFA39A8B),
    DashedSurface = Color(0x801E3328),
    DashedBorder = Color(0x666DB48C),
    InsightSurface = Color(0x991E3328),
    Expense = Color(0xFFE07B66),
    ExpenseSurface = Color(0xFF3A231E),
    IncomeSurface = Color(0xFF1E3328),
    ChartBars = listOf(
        Color(0xFFB89A63),
        Color(0xFFD4A03A),
        Color(0xFF6DB48C),
        Color(0xFF8FB0CC),
        Color(0xFFD0907C),
    ),
    HighlightBorder = Color(0x406DB48C),
    MeasureTile = Color(0x992A2622),
    MeasureForm = Color(0x4D2A2622),
    MeasureRow = Color(0x662A2622),
    PillSurface = Color(0xFF2A2622),
    ChartTemp = Color(0xFFE07B66),
    ChartDamp = Color(0xFF6FA8D0),
    ChartTurn = Color(0xFFD4A03A),
    ChartNote = Color(0xFFA39A8B),
    ChartAiring = Color(0x336DB48C),
    ChartGrid = Color(0xFF2A2622),
    ChartAxisLabel = Color(0xFFA39A8B),
    ScheduleMatch = Color(0xFF1E3328),
    ScheduleTolerable = Color(0xFF3A3220),
    ScheduleConflict = Color(0xFF3A231E),
)

/**
 * Набор цветов текущей темы — то, что раньше было `object DesignPalette`.
 *
 * Читается из [LocalDesignPalette], который ставит [IncubatorTheme]; вне композиции
 * его нет, и это правильно: цвет зависит от темы, а тема — от того, где рисуют.
 */
val DesignPalette: DesignColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDesignPalette.current

/** Поставляется [IncubatorTheme]; вне темы — светлый набор, чтобы превью не падало. */
val LocalDesignPalette = staticCompositionLocalOf { LightDesignColors }
