package ru.zaroslikov.incubator.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.zaroslikov.incubator.domain.model.Batch
import ru.zaroslikov.incubator.domain.model.BatchStatus
import ru.zaroslikov.incubator.domain.model.Candling

/**
 * Закрепляет арифметику вкладки «Статистика».
 *
 * Проверять есть что не потому, что суммы сложные, а потому, что в них легко подменить
 * знаменатель: процент вывода считается по завершённым закладкам, а яйца — по всем, и
 * стоит перепутать одно с другим, как идущая закладка занизит эффективность инкубатора
 * вдвое, ничего при этом не сломав.
 */
class IncubatorStatsTest {

    private fun batch(
        id: Long,
        type: String = "Курицы",
        breed: String = "",
        eggAll: Int = 0,
        eggAllEND: Int = 0,
        arhive: String = "1",
        endReason: String = "",
        dateEnd: String = "01.01.2026",
        eggRejected: Int = 0,
        title: String = "Закладка $id",
        incubatorId: Long = 1,
    ) = Batch(
        id = id,
        title = title,
        type = type,
        data = "01.01.2026",
        eggAll = eggAll,
        eggAllEND = eggAllEND,
        airing = "0",
        over = "0",
        arhive = arhive,
        dateEnd = dateEnd,
        note = "",
        incubatorId = incubatorId,
        breed = breed,
        endReason = endReason,
        eggRejected = eggRejected,
    )

    @Test
    fun `пустой инкубатор даёт пустую статистику`() {
        val stats = incubatorStats(emptyList(), emptyList())

        assertEquals(0, stats.totalEggs)
        assertEquals(0, stats.hatched)
        assertEquals(0, stats.rejected)
        assertNull(stats.hatchRate)
        assertTrue(stats.bySpecies.isEmpty())
        assertTrue(stats.breeds.isEmpty())
        assertTrue(stats.history.isEmpty())
    }

    @Test
    fun `яйца считаются по всем закладкам, а процент — только по завершённым`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, eggAll = 100, eggAllEND = 80, arhive = "1"),
                batch(id = 2, eggAll = 100, eggAllEND = 0, arhive = "0"),
            ),
            emptyList(),
        )

        assertEquals(200, stats.totalEggs)
        assertEquals(80, stats.hatched)
        // Не 40 %: идущая закладка в знаменатель не входит.
        assertEquals(80, stats.hatchRate)
    }

    @Test
    fun `без завершённых закладок эффективность неизвестна, а не ноль`() {
        val stats = incubatorStats(
            listOf(batch(id = 1, eggAll = 50, arhive = "0")),
            emptyList(),
        )

        assertEquals(50, stats.totalEggs)
        assertNull(stats.hatchRate)
        // Идущая закладка в разрезы по видам не входит вовсе — итога у неё ещё нет.
        assertTrue(stats.bySpecies.isEmpty())
    }

    @Test
    fun `отбраковка складывается из графы закладки и овоскопирований`() {
        val stats = incubatorStats(
            listOf(batch(id = 7, eggAll = 100, eggAllEND = 70, eggRejected = 4)),
            listOf(
                Candling(id = 1, idPT = 7, day = 7, date = "08.01.2026", rejected = 12),
                Candling(id = 2, idPT = 7, day = 14, date = "15.01.2026", rejected = 6),
                // Овоскопирование чужой закладки в сумму не попадает.
                Candling(id = 3, idPT = 99, day = 7, date = "08.01.2026", rejected = 50),
            ),
        )

        assertEquals(22, stats.rejected)
        assertEquals(22, stats.bySpecies.single().slice.rejected)
    }


    @Test
    fun `виды сортируются по яйцам, внутри вида — породы`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, type = "Курицы", breed = "Ломан Браун", eggAll = 30, eggAllEND = 27),
                batch(id = 2, type = "Курицы", breed = "Хайсекс", eggAll = 70, eggAllEND = 35),
                batch(id = 3, type = "Утки", breed = "Пекинская", eggAll = 40, eggAllEND = 20),
            ),
            emptyList(),
        )

        assertEquals(listOf("Курицы", "Утки"), stats.bySpecies.map { it.slice.name })

        val chickens = stats.bySpecies.first()
        assertEquals(100, chickens.slice.eggs)
        assertEquals(62, chickens.slice.hatched)
        assertEquals(62, chickens.slice.rate)
        assertEquals(listOf("Хайсекс", "Ломан Браун"), chickens.breeds.map { it.name })
        assertEquals(50, chickens.breeds.first().rate)
        assertEquals(90, chickens.breeds.last().rate)
    }

    @Test
    fun `незаполненная порода собирается в одну строку`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, breed = "", eggAll = 10, eggAllEND = 5),
                batch(id = 2, breed = "   ", eggAll = 10, eggAllEND = 5),
                batch(id = 3, breed = "Ломан Браун", eggAll = 40, eggAllEND = 36),
            ),
            emptyList(),
        )

        assertEquals(listOf("Ломан Браун", NO_BREED), stats.breeds.map { it.name })
        val noBreed = stats.breeds.last()
        assertEquals(2, noBreed.batches)
        assertEquals(20, noBreed.eggs)
        assertEquals(50, noBreed.rate)
    }

    @Test
    fun `закладки одной породы складываются в одну строку`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, type = "Курицы", breed = "Ломан Браун", eggAll = 20, eggAllEND = 18),
                batch(id = 2, type = "Курицы", breed = "Ломан Браун", eggAll = 30, eggAllEND = 21),
            ),
            emptyList(),
        )

        val breed = stats.breeds.single()
        assertEquals("Ломан Браун", breed.name)
        assertEquals(2, breed.batches)
        assertEquals(50, breed.eggs)
        assertEquals(78, breed.rate) // 39 из 50, с округлением вниз
    }

    @Test
    fun `породы идут группами по видам, в порядке самих видов`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, type = "Утки", breed = "Пекинская", eggAll = 40, eggAllEND = 30),
                batch(id = 2, type = "Курицы", breed = "Хайсекс", eggAll = 30, eggAllEND = 20),
                batch(id = 3, type = "Курицы", breed = "Ломан Браун", eggAll = 90, eggAllEND = 80),
            ),
            emptyList(),
        )

        // Виды — по яйцам (120 против 40), породы внутри вида — тоже.
        assertEquals(listOf("Курицы", "Утки"), stats.bySpecies.map { it.slice.name })
        assertEquals(
            listOf("Ломан Браун", "Хайсекс"),
            stats.bySpecies.first().breeds.map { it.name },
        )
        // Плоский список для «лучшей породы» разворачивает те же группы по порядку.
        assertEquals(
            listOf("Ломан Браун", "Хайсекс", "Пекинская"),
            stats.breeds.map { it.name },
        )
    }

    @Test
    fun `порода складывается из своих закладок, и породы вида сходятся с видом`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, type = "Курицы", breed = "Ломан Браун", eggAll = 20, eggAllEND = 18),
                batch(id = 2, type = "Курицы", breed = "Хайсекс", eggAll = 30, eggAllEND = 22),
                batch(id = 3, type = "Курицы", breed = "Хайсекс", eggAll = 10, eggAllEND = 8),
            ),
            emptyList(),
        )

        // Вид считает все три — 60 яиц, 48 птенцов.
        val chickens = stats.bySpecies.single()
        assertEquals(60, chickens.slice.eggs)
        assertEquals(48, chickens.slice.hatched)
        assertEquals(3, chickens.slice.batches)

        // «Хайсекс» складывается из двух закладок, «Ломан Браун» — из одной.
        val hisex = chickens.breeds.first { it.name == "Хайсекс" }
        assertEquals(40, hisex.eggs)
        assertEquals(30, hisex.hatched)
        assertEquals(2, hisex.batches)
        assertEquals(75, hisex.rate)
        val loman = chickens.breeds.first { it.name == "Ломан Браун" }
        assertEquals(20, loman.eggs)
        assertEquals(90, loman.rate)
        assertEquals(1, loman.batches)

        // Сумма по породам сходится с видом: ни одно яйцо не потерялось и не удвоилось.
        assertEquals(chickens.slice.eggs, chickens.breeds.sumOf { it.eggs })
        assertEquals(chickens.slice.hatched, chickens.breeds.sumOf { it.hatched })
    }

    @Test
    fun `отбраковка породы складывается из графы её закладок и их овоскопирований`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, breed = "Ломан Браун", eggAll = 20, eggAllEND = 18, eggRejected = 2),
                batch(id = 2, breed = "Хайсекс", eggAll = 30, eggAllEND = 22, eggRejected = 4),
                batch(id = 3, breed = "Хайсекс", eggAll = 10, eggAllEND = 8, eggRejected = 2),
            ),
            listOf(
                Candling(id = 1, idPT = 1, day = 7, date = "08.01.2026", rejected = 1),
                Candling(id = 2, idPT = 2, day = 7, date = "08.01.2026", rejected = 4),
            ),
        )

        // Инкубатор знает всю отбраковку: 2 + 1, 4 + 4, 2.
        assertEquals(13, stats.rejected)
        // Породе достаётся отбраковка её закладок целиком: в закладке одна порода, и
        // делить нечего.
        assertEquals(3, stats.breeds.first { it.name == "Ломан Браун" }.rejected)
        assertEquals(10, stats.breeds.first { it.name == "Хайсекс" }.rejected)
        // И сумма по породам — это отбраковка инкубатора.
        assertEquals(stats.rejected, stats.breeds.sumOf { it.rejected })
    }

    @Test
    fun `идущие закладки не входят в разрезы по видам и породам`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, arhive = "0", breed = "Ломан Браун", eggAll = 20),
                batch(id = 2, arhive = "0", breed = "Хайсекс", eggAll = 30),
                batch(id = 3, breed = "Хайсекс", eggAll = 10, eggAllEND = 8),
            ),
            emptyList(),
        )

        // Яйца идущих закладок ещё не вывелись: ни в столбец вида, ни в строку породы
        // они не попадают. Порода, у которой всё ещё в инкубаторе, не появляется вовсе.
        val hisex = stats.breeds.single()
        assertEquals("Хайсекс", hisex.name)
        assertEquals(10, hisex.eggs)
        assertEquals(8, hisex.hatched)
        assertEquals(80, hisex.rate)
        assertEquals(0, hisex.activeEggs)
        assertEquals(10, stats.bySpecies.single().slice.eggs)

        // А в общих числах наверху идущие по-прежнему видны.
        assertEquals(60, stats.totalEggs)
        assertEquals(50, stats.total.activeEggs)
    }

    @Test
    fun `история несёт породу закладки`() {
        val stats = incubatorStats(
            listOf(batch(id = 1, breed = "Ломан Браун", eggAll = 50, eggAllEND = 40)),
            emptyList(),
        )

        assertEquals("Ломан Браун", stats.history.single().breed)
    }

    @Test
    fun `в истории только завершённые, свежие сверху`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, dateEnd = "05.01.2026", eggAll = 10, eggAllEND = 9),
                batch(id = 2, dateEnd = "31.12.2025", eggAll = 10, eggAllEND = 8),
                batch(id = 3, dateEnd = "20.02.2026", eggAll = 10, eggAllEND = 7),
                batch(id = 4, arhive = "0", dateEnd = ""),
            ),
            emptyList(),
        )

        assertEquals(listOf(3L, 1L, 2L), stats.history.map { it.batchId })
    }

    @Test
    fun `прерванная закладка остаётся в истории и в подсчётах`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, eggAll = 100, eggAllEND = 90),
                batch(
                    id = 2,
                    eggAll = 100,
                    eggAllEND = 0,
                    endReason = "Отключили свет",
                    dateEnd = "02.01.2026",
                ),
            ),
            emptyList(),
        )

        // Сорвавшаяся закладка тянет эффективность вниз — так и должно быть.
        assertEquals(45, stats.hatchRate)
        assertEquals(2, stats.history.size)
        assertEquals(BatchStatus.Stopped, stats.history.first { it.batchId == 2L }.status)
        assertEquals(0, stats.history.first { it.batchId == 2L }.rate)
    }

    @Test
    fun `закладка без даты завершения уезжает вниз истории`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, dateEnd = ""),
                batch(id = 2, dateEnd = "01.01.2020"),
            ),
            emptyList(),
        )

        assertEquals(listOf(2L, 1L), stats.history.map { it.batchId })
    }

    @Test
    fun `у закладки без яиц процент неизвестен, а не ноль`() {
        val stats = incubatorStats(listOf(batch(id = 1, eggAll = 0)), emptyList())

        assertNull(stats.history.single().rate)
        assertNull(stats.hatchRate)
    }

    @Test
    fun `заголовок истории берёт вид, когда название пустое`() {
        val stats = incubatorStats(
            listOf(batch(id = 1, title = "", type = "Гуси", eggAll = 10, eggAllEND = 5)),
            emptyList(),
        )

        assertEquals("Гуси", stats.history.single().title)
    }

    @Test
    fun `без карты имён инкубатор в истории пуст`() {
        val stats = incubatorStats(
            listOf(batch(id = 1, eggAll = 10, eggAllEND = 5)),
            emptyList(),
        )

        // Разрез в один инкубатор: экрану нечего печатать, и строка остаётся прежней.
        assertEquals("", stats.history.single().incubator)
    }

    @Test
    fun `с картой имён каждая строка истории называет свой инкубатор`() {
        val stats = incubatorStats(
            batches = listOf(
                batch(id = 1, incubatorId = 7, eggAll = 10, eggAllEND = 5),
                batch(id = 2, incubatorId = 8, eggAll = 20, eggAllEND = 18, dateEnd = "02.01.2026"),
            ),
            candlings = emptyList(),
            incubatorNames = mapOf(7L to "Несушка БИ-2", 8L to "Блиц 72"),
        )

        assertEquals(
            mapOf(1L to "Несушка БИ-2", 2L to "Блиц 72"),
            stats.history.associate { it.batchId to it.incubator },
        )
    }

    @Test
    fun `инкубатор, которого нет в карте, не ломает строку`() {
        val stats = incubatorStats(
            batches = listOf(batch(id = 1, incubatorId = 9, eggAll = 10, eggAllEND = 5)),
            candlings = emptyList(),
            // Удалённое или ещё не подгруженное устройство — обычный ответ, а не сбой:
            // строка просто не называет инкубатора, как в разрезе одного.
            incubatorNames = mapOf(7L to "Несушка БИ-2"),
        )

        assertEquals("", stats.history.single().incubator)
    }

    @Test
    fun `строка истории раскладывает отбраковку по овоскопированиям`() {
        val stats = incubatorStats(
            batches = listOf(batch(id = 1, eggAll = 30, eggAllEND = 20, eggRejected = 4)),
            candlings = listOf(
                // Порядок записей не важен — в строке они идут по дням.
                Candling(idPT = 1, day = 18, date = "", rejected = 0),
                Candling(idPT = 1, day = 7, date = "", rejected = 6),
                // Чужая закладка в разбор не попадает.
                Candling(idPT = 2, day = 7, date = "", rejected = 9),
            ),
        )

        val record = stats.history.single()
        assertEquals(4, record.manualRejected)
        assertEquals(
            listOf(CandlingCull(day = 7, stage = 1, rejected = 6), CandlingCull(day = 18, stage = 0, rejected = 0)),
            record.candlingCulls,
        )
        assertEquals(record.rejected, record.manualRejected + record.candlingCulls.sumOf { it.rejected })
    }

    @Test
    fun `порода несёт свой вид птицы`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, type = "Курицы", breed = "Ломан Браун", eggAll = 40, eggAllEND = 36),
                batch(id = 2, type = "Утки", breed = "", eggAll = 20, eggAllEND = 10),
                batch(id = 3, type = "Гуси", breed = "", eggAll = 10, eggAllEND = 5),
            ),
            emptyList(),
        )

        val loman = stats.breeds.first { it.name == "Ломан Браун" }
        assertEquals("Курицы", loman.species)

        // «Без породы» не сливает виды в одну строку: у уток своя, у гусей своя, и
        // каждая знает, под чьим заголовком стоит.
        val noBreeds = stats.breeds.filter { it.name == NO_BREED }
        assertEquals(listOf("Утки", "Гуси"), noBreeds.map { it.species })
        assertEquals(listOf(20, 10), noBreeds.map { it.eggs })

        // Разрез по виду вида в себе не несёт: он и есть имя строки.
        assertTrue(stats.bySpecies.all { it.slice.species.isBlank() })
    }

    @Test
    fun `идущие закладки видны отдельно от завершённых`() {
        val stats = incubatorStats(
            listOf(
                batch(id = 1, eggAll = 100, eggAllEND = 80),
                batch(id = 2, eggAll = 50, eggAllEND = 0, arhive = "0"),
            ),
            emptyList(),
        )

        // Яйца — по всем закладкам, процент — по завершённым, и разница между
        // знаменателями теперь называется вслух: её показывает экран.
        assertEquals(150, stats.totalEggs)
        assertEquals(100, stats.total.finishedEggs)
        assertEquals(50, stats.total.activeEggs)
        assertEquals(1, stats.total.activeBatches)
        assertEquals(1, stats.total.finishedBatches)
        assertEquals(80, stats.hatchRate)
    }
}
