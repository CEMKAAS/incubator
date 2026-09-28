package ru.zaroslikov.incubator.ui.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Закрепляет, как форма с несколькими породами разъезжается на закладки
 * ([BatchUiState.toBatches]): по одной на строку, своё у строки, общее у формы.
 */
class BatchSplitTest {

    private val form = BatchUiState(
        title = " Весенняя партия ",
        type = "Курицы",
        data = "01.08.2026",
        time = "08:00",
        note = "из своего стада",
        airing = true,
        incubatorId = 7,
        eggAll = "99",
        price = "5",
        pricePerEgg = false,
    )

    @Test
    fun `одна строка — одна закладка с породой из неё`() {
        val batches = form.copy(breeds = listOf(BreedUiState(name = " Ломан Браун "))).toBatches()

        val single = batches.single()
        assertEquals(" Весенняя партия ", single.title)
        assertEquals("Ломан Браун", single.breed)
        assertEquals(99, single.eggAll)
        assertEquals(5, single.price)
        assertFalse(single.pricePerEgg)
    }

    @Test
    fun `две строки — две закладки, своё у строки и общее у формы`() {
        val batches = form.copy(
            breeds = listOf(
                BreedUiState(name = "Ломан Браун", eggs = "20", price = "22", pricePerEgg = true),
                BreedUiState(name = "Хайсекс", eggs = "30", price = "900", pricePerEgg = false),
            ),
        ).toBatches()

        assertEquals(2, batches.size)
        val (loman, hisex) = batches
        assertEquals("Весенняя партия — Ломан Браун", loman.title)
        assertEquals("Ломан Браун", loman.breed)
        assertEquals(20, loman.eggAll)
        assertEquals(22, loman.price)
        assertTrue(loman.pricePerEgg)

        assertEquals("Весенняя партия — Хайсекс", hisex.title)
        assertEquals("Хайсекс", hisex.breed)
        assertEquals(30, hisex.eggAll)
        assertEquals(900, hisex.price)
        assertFalse(hisex.pricePerEgg)

        // Общее — у обеих одно и то же, и поля формы про яйца и цену в них не попали.
        batches.forEach {
            assertEquals("Курицы", it.type)
            assertEquals("01.08.2026", it.data)
            assertEquals("08:00", it.time)
            assertEquals("из своего стада", it.note)
            assertEquals("true", it.airing)
            assertEquals(7L, it.incubatorId)
            assertEquals(0L, it.id)
        }
    }

    @Test
    fun `сумма по строкам — то, что форма показывает в «Количество яиц» и «Стоимость»`() {
        val split = form.copy(
            breeds = listOf(
                BreedUiState(name = "Ломан Браун", eggs = "20", price = "22", pricePerEgg = true),
                BreedUiState(name = "Хайсекс", eggs = "30", price = "900", pricePerEgg = false),
            ),
        )

        assertTrue(split.splitByBreeds)
        assertEquals(50, split.eggCount)
        assertEquals(1_340, split.breedPriceTotal)
    }

    @Test
    fun `одна порода дважды — не две закладки`() {
        val twice = listOf(
            BreedUiState(name = "Хайсекс", eggs = "20"),
            BreedUiState(name = " хайсекс", eggs = "30"),
        )
        assertFalse(form.copy(breeds = twice).breedNamesDistinct)
        assertTrue(form.copy(breeds = twice.take(1)).breedNamesDistinct)
        assertTrue(
            form.copy(breeds = listOf(twice.first(), BreedUiState(name = "Ломан Браун", eggs = "1")))
                .breedNamesDistinct,
        )
    }

    @Test
    fun `подпись под карточками не считает пустую строку закладкой`() {
        assertEquals("Весенняя партия", splitBatchTitle(" Весенняя партия ", ""))
        assertEquals("Весенняя партия — Хайсекс", splitBatchTitle(" Весенняя партия ", " Хайсекс "))
    }

    @Test
    fun `закладка возвращается в форму одной строкой породы`() {
        val batch = form.copy(breeds = listOf(BreedUiState(name = "Хайсекс"))).toBatch()
        val back = batch.toBatchUiState()

        assertEquals(listOf(BreedUiState(name = "Хайсекс")), back.breeds)
        assertEquals("Хайсекс", back.breed)
        assertFalse(back.splitByBreeds)
    }
}
