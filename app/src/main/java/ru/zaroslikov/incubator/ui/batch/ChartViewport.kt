package ru.zaroslikov.incubator.ui.batch

/**
 * Масштаб и сдвиг графика «Аналитики за день», раскрытого на весь экран.
 *
 * Ровно арифметика, без Compose — как [analyticsOf]: жест приходит из `pointerInput`,
 * а куда от него сдвинуть содержимое и где его остановить, считается здесь и
 * проверяется `ChartViewportTest` на JVM.
 *
 * Система координат — область графика (внутри полей под оси): содержимое при [scale] = 1
 * совпадает с ней, при большем — шире и выше её в [scale] раз, и [offsetX] / [offsetY]
 * говорят, на сколько пикселей оно сдвинуто. Сдвиг по `x` неположителен (содержимое
 * уезжает влево), по `y` неотрицателен (уезжает вниз, потому что ось значений растёт
 * вверх, а пиксели — вниз); оба ограничены так, чтобы край содержимого не отходил от
 * края области — за ним нет ничего, и пустое поле на весь экран читалось бы как график,
 * который забыли нарисовать. [IDENTITY] — как в карточке: ни масштаба, ни сдвига.
 */
internal data class ChartViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    /** Увеличение включено: сетку дробят, подписи времени прореживают заново. */
    val zoomed: Boolean get() = scale > 1f + 1e-3f

    /**
     * Во сколько частей делить шаг оси: степень двойки, не больше масштаба. При 2× шаг
     * делится пополам, при 4× — на четыре; между ними — как у меньшей степени, чтобы
     * засечки не появлялись и не исчезали на каждом движении пальцев.
     */
    val subdivisions: Int
        get() {
            var parts = 1
            while (parts * 2 <= scale + 1e-3f) parts *= 2
            return parts
        }

    /** `x` содержимого в пикселях области: [fraction] — доля по времени от 0 до 1. */
    fun x(fraction: Float, plotWidth: Float): Float = offsetX + fraction * plotWidth * scale

    /** `y` содержимого от нижнего края области: [fraction] — доля по значению от 0 до 1. */
    fun yFromBottom(fraction: Float, plotHeight: Float): Float =
        offsetY - fraction * plotHeight * scale

    /**
     * Жест: масштаб [zoom] вокруг точки ([cx], [cy]) — координаты относительно левого
     * нижнего угла области, `y` вверх отрицателен, — затем сдвиг на ([panX], [panY]) в
     * пикселях экрана. Точка под пальцами остаётся под ними: сначала содержимое
     * растягивается вокруг неё, потом едет за пальцами, и всё вместе прижимается к
     * границам.
     */
    fun transformed(
        zoom: Float,
        cx: Float,
        cy: Float,
        panX: Float,
        panY: Float,
        plotWidth: Float,
        plotHeight: Float,
    ): ChartViewport {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = newScale / scale
        // Точка содержимого под центром жеста сохраняет своё место на экране.
        val newOffsetX = cx - (cx - offsetX) * ratio + panX
        val newOffsetY = cy - (cy - offsetY) * ratio + panY
        return ChartViewport(newScale, newOffsetX, newOffsetY).clamped(plotWidth, plotHeight)
    }

    /** Сдвиг, при котором содержимое не отходит от краёв области. */
    fun clamped(plotWidth: Float, plotHeight: Float): ChartViewport {
        val overflowX = plotWidth * (scale - 1f)
        val overflowY = plotHeight * (scale - 1f)
        return copy(
            offsetX = offsetX.coerceIn(-overflowX, 0f),
            offsetY = offsetY.coerceIn(0f, overflowY),
        )
    }

    companion object {
        val IDENTITY = ChartViewport()

        const val MIN_SCALE = 1f

        /** Дальше восьми крат один час на весь экран — мельче замеры не пишут. */
        const val MAX_SCALE = 8f
    }
}
