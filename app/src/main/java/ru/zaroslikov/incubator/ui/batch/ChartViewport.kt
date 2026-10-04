package ru.zaroslikov.incubator.ui.batch

/**
 * Масштаб и сдвиг графика «Аналитики за день», раскрытого на весь экран.
 *
 * Арифметика без Compose, как [analyticsOf]; проверяется `ChartViewportTest` на JVM.
 *
 * Система координат — область графика: при [scale] = 1 содержимое совпадает с ней, при
 * большем — в [scale] раз шире и выше, [offsetX] / [offsetY] — сдвиг в пикселях. По `x`
 * он неположителен, по `y` неотрицателен (ось значений растёт вверх, пиксели — вниз); оба
 * ограничены так, чтобы край содержимого не отходил от края области — пустое поле
 * читалось бы как неотрисованный график. [IDENTITY] — как в карточке.
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
     * Жест: масштаб [zoom] вокруг точки ([cx], [cy]) — относительно левого нижнего угла
     * области, `y` вверх отрицателен, — затем сдвиг на ([panX], [panY]) в пикселях экрана,
     * с прижатием к границам.
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
