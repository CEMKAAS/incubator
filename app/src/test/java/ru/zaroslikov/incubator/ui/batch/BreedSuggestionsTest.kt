package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Закрепляет, какие из известных пород форма предлагает в списке под строкой породы:
 * все, кроме уже стоящих в других строках. Сужение по набранному — дело самого поля
 * (`SuggestingSheetTextField`), и здесь его нет.
 */
class BreedSuggestionsTest {

    private val known = listOf("Ломан Браун", "Хайсекс", "Адлерская")

    private fun state(vararg names: String) =
        BatchUiState(breeds = names.map { BreedUiState(name = it) })

    @Test
    fun `одна строка предлагает всё, что бы в ней ни стояло`() {
        assertEquals(known, state("").breedSuggestions(known, 0))
        assertEquals(known, state("лом").breedSuggestions(known, 0))
        assertEquals(known, state("Хайсекс").breedSuggestions(known, 0))
    }

    @Test
    fun `порода другой строки не предлагается, без учёта регистра и краёв`() {
        val split = state("хайсекс ", "")
        assertEquals(listOf("Ломан Браун", "Адлерская"), split.breedSuggestions(known, 1))
        assertEquals(known, split.breedSuggestions(known, 0))
    }

    @Test
    fun `пустой список известных — пустой список`() {
        assertEquals(emptyList<String>(), state("").breedSuggestions(emptyList(), 0))
    }
}
