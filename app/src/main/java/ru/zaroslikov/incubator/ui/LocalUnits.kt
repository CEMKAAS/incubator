package ru.zaroslikov.incubator.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.zaroslikov.incubator.settings.Units

/**
 * Единицы из «Настроек» — градусы и валюта — для всего, что рисует числа. `CompositionLocal`, чтобы
 * не тянуть значение через сигнатуры десятков мест; `MainActivity` подставляет его из
 * `AppSettings.unitsFlow()`. `static`: меняется редко, подписка каждого читателя стоила бы дороже.
 * Только для показа: строки в полях ввода ViewModel переводит сама — см. `ValueFormat`.
 */
val LocalUnits = staticCompositionLocalOf { Units.DEFAULT }
