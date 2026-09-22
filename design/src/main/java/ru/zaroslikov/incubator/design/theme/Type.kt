package ru.zaroslikov.incubator.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import ru.zaroslikov.incubator.design.R


val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Шрифты макета: Fraunces — заголовки, Inter — текст, JetBrains Mono — надзаголовки и числа.
 *
 * Оговорка: в макете у Fraunces выставлены вариативные оси SOFT/WONK. Downloadable Fonts
 * их не передают, поэтому начертание будет базовым SemiBold, без «вонкости» из Figma.
 */
val displayFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Fraunces"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Fraunces"), fontProvider = provider, weight = FontWeight.SemiBold),
)

/**
 * Курсивное начертание объявлено отдельно ради [DesignType.Note]: без него Compose
 * наклонил бы прямой Inter сам (синтез), и это выглядит заметно хуже настоящего курсива.
 * Если провайдер шрифтов курсив не отдаст, синтез всё равно сработает — пояснение
 * останется наклонным, просто не таким ровным.
 */
val bodyFontFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Bold),
    Font(
        googleFont = GoogleFont("Inter"),
        fontProvider = provider,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
    ),
)

val monoFontFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.SemiBold),
)

// Default Material 3 typography values
val baseline = Typography()

// Set of Material typography styles to start with
val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
)

/**
 * Стили, снятые с макета один в один. Размеры и интерлиньяж — из Figma, не на глаз.
 */
object DesignType {
    /**
     * Надзаголовок из макета: JetBrains Mono 11 / 16.5, трекинг 2.2, капслок.
     *
     * Сейчас его никто не рисует — «МОЯ ПАСЕКА» с первого экрана убрана, — но стиль
     * снят с макета и остаётся на месте, чтобы надзаголовок вернулся одной строкой.
     */
    val Eyebrow = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
        letterSpacing = 2.2.sp,
    )

    /** Заголовок экрана «Инкубаторы»: Fraunces SemiBold 34 / 34. */
    val ScreenTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 34.sp,
    )

    /** Число в плитке статистики: Fraunces SemiBold 28 / 28. */
    val StatValue = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 28.sp,
    )

    /** Заголовок карточки: Fraunces SemiBold 21 / 26.25. */
    val CardTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.25.sp,
    )

    /** Подпись/подзаголовок: Inter 12 / 18. */
    val Caption = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** Выделенная часть подписи (дата вывода): Inter Medium 12 / 18. */
    val CaptionEmphasis = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** Моноширинный подзаголовок — модель инкубатора: JetBrains Mono 12 / 18. */
    val Mono = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** Процент заполнения: JetBrains Mono Medium 12 / 18. */
    val MonoEmphasis = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )

    /** Подпись поля в форме: Inter Medium 13 / 19.5. */
    val FieldLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Значение и подсказка в поле ввода: Inter 15. */
    val FieldValue = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.5.sp,
    )

    /** Срок инкубации на плитке вида птицы: JetBrains Mono 10 / 15. */
    val MonoMicro = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )

    /** Числовое поле — в макете моноширинное: JetBrains Mono 15 / 22.5. */
    val MonoField = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.5.sp,
    )

    /** Подпись переключателя: Inter Medium 14 / 21. */
    val ToggleLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** Заголовок шторки: Fraunces SemiBold 22 / 33. */
    val SheetTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 33.sp,
    )

    /** Надпись на главной кнопке: Inter Medium 16 / 24. */
    val ButtonLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    // --- Экран инкубатора (узел 5:547): зелёная шапка, вкладки, карточки закладок ---

    /**
     * Надзаголовок из макета — модель и вместимость капсом: JetBrains Mono 12 / 18, трекинг 2.16.
     * Сейчас не используется: строка модели переехала под название инкубатора и набрана
     * `Caption`. Оставлен рядом с `Eyebrow` — вернуть надзаголовок это одна строка.
     */
    val HeaderEyebrow = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 2.16.sp,
    )

    /** Название инкубатора в шапке: Fraunces SemiBold 30 / 37.5. */
    val HeaderTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 37.5.sp,
    )

    /** Число в строке показателей шапки: Fraunces SemiBold 22 / 22. */
    val HeaderStatValue = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 22.sp,
    )

    /** Подпись под числом в шапке и текст статус-чипа: Inter 11 / 16.5. */
    val Micro = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )

    /**
     * Пояснение к функции — абзац под карточкой или полем, который рассказывает, что
     * настройка делает и чего не делает: «Меняется только знак. Суммы по курсу не
     * пересчитываются…», «Вид общий для всех инкубаторов…». Тот же размер, что у [Micro],
     * но курсивом: подпись к числу и рассказ о том, как это работает, — разные вещи, и
     * набранные одинаково они читались бы как одна.
     *
     * Курсив — ровно для таких абзацев. Подпись под числом, текст чипа, подсказка
     * «22 ₽ за яйцо», сообщение об ошибке и пустое состояние остаются [Micro] / [Body]:
     * это не объяснение функции, а её данные.
     */
    val Note = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )

    /**
     * Заглушка пустого списка — «В этом инкубаторе пока нет закладок», «Закладок пока
     * нет — здесь появится история выводов», «Пока ни одного своего вида»: Inter Bold
     * 13 / 19.5. Жирным, чтобы она была третьим голосом рядом с заголовком и его
     * подписью, а не второй подписью: под шапкой с названием и «2 места» строка тем же
     * начертанием читалась как ещё одно пояснение, а не как ответ «здесь пусто».
     *
     * Только для пустого состояния. Пояснение к функции — [Note], подпись — [Caption].
     */
    val Placeholder = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Текст статус-чипа закладки: Inter Medium 11 / 16.5. */
    val ChipLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )

    /** Надпись вкладки в переключателе: Inter Medium 13 / 19.5. */
    val TabLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Заголовок карточки закладки: Fraunces SemiBold 18 / 27. */
    val BatchTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    )

    /** Правая колонка карточки — «через 8 дн.», «+21 птенцов»: JetBrains Mono Medium 13 / 19.5. */
    val MonoAccent = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Число в плитке вкладки «Статистика»: Fraunces SemiBold 24 / 24. */
    val MetricValue = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 24.sp,
    )

    /** Заголовок блока внутри вкладки: Fraunces SemiBold 17 / 25.5. */
    val SectionTitle = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 25.5.sp,
    )

    /** Строка легенды и текст-вывод: Inter 13 / 19.5. */
    val Body = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Название операции в «Финансах»: Inter Medium 14 / 21. */
    val ListItemTitle = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** Чистая прибыль крупно: Fraunces SemiBold 36 / 36. */
    val MoneyLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 36.sp,
    )

    /** Сумма в плитке доходов/расходов: JetBrains Mono SemiBold 16 / 24. */
    val MoneyTile = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Сумма в строке операции: JetBrains Mono SemiBold 14 / 21. */
    val MoneyRow = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    // --- Шторка закладки (узел 14:4893) ---

    /** Заголовок блока в шторке — «Замеры за сегодня»: Fraunces SemiBold 16 / 24. */
    val CardHeading = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Значение в плитке-справке — «48», «8 авг.»: Inter Medium 13 / 19.5. */
    val TileValue = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Замер крупно — «36.6°»: Fraunces SemiBold 26 / 26. */
    val MeasureValue = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 26.sp,
    )

    /** «цель 37.8°» и подписи шкалы: JetBrains Mono 11 / 16.5. */
    val MonoSmall = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )

    /** Вердикт об отклонении: JetBrains Mono Medium 11 / 16.5. */
    val MonoSmallEmphasis = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )

    /** Подпись плашки и шкалы, капслок: JetBrains Mono 10 / 15, трекинг 0.25. */
    val PillLabel = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.25.sp,
    )

    /** Число на плашке режима: JetBrains Mono SemiBold 16 / 24. */
    val PillValue = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** Время в строке замера: JetBrains Mono 13 / 19.5. */
    val MonoRow = TextStyle(
        fontFamily = monoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    // --- Аналитика за день (узел 21:9088) ---

    /** Число в плитке аналитики — «36.3°»: Fraunces SemiBold 20 / 20. */
    val AnalyticsValue = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 20.sp,
    )

    /** Заголовок карточки графика: Inter Medium 13 / 19.5. */
    val ChartTitle = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )

    /** Подписи осей и времени на графике: Inter 11 / 16.5. */
    val ChartAxisLabel = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.5.sp,
    )
}
