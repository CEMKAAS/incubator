package ru.zaroslikov.incubator.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.zaroslikov.incubator.data.DATABASE_VERSION

/**
 * Миграции — единственное в этом проекте, чего не поймать чтением кода: они написаны
 * строками SQL, а совпадение результата с ожидаемой схемой сверяет Room и только при
 * открытии базы, то есть на телефоне у пользователя. До этого файла не была проверена
 * ни одна из тринадцати.
 *
 * Здесь проверяется последняя — она же образец для остальных, которые стоит закрыть тем
 * же способом. Первую (1 → 2) так проверить нельзя: `exportSchema` включили уже после
 * неё, и `1.json`, из которого `MigrationTestHelper` создаёт базу, не существует.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InventoryDatabase::class.java,
    )

    /**
     * Индексы по внешним ключам появляются, а данные остаются на месте.
     *
     * `runMigrationsAndValidate` сверяет получившуюся схему с `14.json` — это и есть
     * главная проверка: имя индекса, отличающееся от того, что генерирует Room, здесь же
     * и обнаружится, а на телефоне обернулось бы отказом открыть базу.
     */
    @Test
    fun migrate13To14_addsForeignKeyIndices_andKeepsData() {
        helper.createDatabase(TEST_DB, 13).use { db ->
            db.execSQL(
                "INSERT INTO Incubator " +
                    "(_id, Name, Capacity, Brand, Model, Price, Note, AutoTurn, AutoAiring, Hidden) " +
                    "VALUES (1, 'Блиц', 72, 'Несушка', 'BI-72', 0, '', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO Batch " +
                    "(_id, Name, Type, Date, Egg_all, Egg_all_end, Airing, Overturn, Archive, " +
                    "Date_end, note, incubatorId, Breed, Price, PricePerEgg, EndReason, " +
                    "ChickPrice, ChickPricePerHead, Hidden, Time, Egg_rejected) " +
                    "VALUES (1, 'Куры', 'Курицы', '01.09.2026', 60, 0, 'false', 'false', '0', " +
                    "'', '', 1, '', 0, 1, '', 0, 1, 0, '08:00', 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_13_14,
        )

        val expected = listOf(
            "index_Batch_incubatorId",
            "index_Batch_value_idPT",
            "index_Batch_measurement_idValue",
            "index_Batch_time_idPT",
            "index_Batch_species_idPT",
        )
        val actual = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { cursor ->
            while (cursor.moveToNext()) actual += cursor.getString(0)
        }
        expected.forEach { assertTrue("нет индекса $it", it in actual) }

        db.query("SELECT Name FROM Batch WHERE _id = 1").use { cursor ->
            assertTrue("закладка пропала при миграции", cursor.moveToFirst())
            assertEquals("Куры", cursor.getString(0))
        }
    }

    /**
     * Породы переезжают из колонки `Batch.Breed` в строки `Batch_breed`.
     *
     * Непустая порода становится одной строкой с яйцами и выводом своей закладки —
     * ровно тем, чем одна порода и была; пустая (и из одних пробелов) не даёт строки
     * вовсе, потому что «Без породы» — это отсутствие строки, а не строка с пустым
     * именем. Колонка после переноса пуста у всех: второго ответа на вопрос «какая
     * порода» в базе не остаётся.
     */
    @Test
    fun migrate14To15_movesBreedIntoRows_andBlanksTheColumn() {
        helper.createDatabase(TEST_DB, 14).use { db ->
            db.execSQL(
                "INSERT INTO Incubator " +
                    "(_id, Name, Capacity, Brand, Model, Price, Note, AutoTurn, AutoAiring, Hidden) " +
                    "VALUES (1, 'Блиц', 72, 'Несушка', 'BI-72', 0, '', 0, 0, 0)"
            )
            val columns = "(_id, Name, Type, Date, Egg_all, Egg_all_end, Airing, Overturn, Archive, " +
                "Date_end, note, incubatorId, Breed, Price, PricePerEgg, EndReason, " +
                "ChickPrice, ChickPricePerHead, Hidden, Time, Egg_rejected)"
            db.execSQL(
                "INSERT INTO Batch $columns VALUES (1, 'Куры', 'Курицы', '01.08.2026', 60, 48, " +
                    "'false', 'false', '1', '22.08.2026', '', 1, ' Ломан Браун ', 0, 1, '', 0, 1, 0, '08:00', 0)"
            )
            db.execSQL(
                "INSERT INTO Batch $columns VALUES (2, 'Утки', 'Утки', '01.09.2026', 20, 0, " +
                    "'false', 'false', '0', '', '', 1, '   ', 0, 1, '', 0, 1, 0, '08:00', 0)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            15,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_14_15,
        )

        db.query("SELECT idPT, name, eggs, hatched FROM Batch_breed ORDER BY id").use { cursor ->
            assertTrue("порода не перенесена в строку", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Ломан Браун", cursor.getString(1))
            assertEquals(60, cursor.getInt(2))
            assertEquals(48, cursor.getInt(3))
            assertTrue("пустая порода дала строку", !cursor.moveToNext())
        }
        db.query("SELECT COUNT(*) FROM Batch WHERE Breed != ''").use { cursor ->
            cursor.moveToFirst()
            assertEquals("колонка Breed не опустошена", 0, cursor.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_Batch_breed_idPT'")
            .use { cursor -> assertTrue("нет индекса по внешнему ключу", cursor.moveToFirst()) }
    }

    /**
     * Порода получает отбраковку и обе цены, а выбраковка овоскопирования — свою таблицу.
     *
     * Проверяется перенос: у закладки с единственной породой строка обязана получить
     * её брак и её цены — до v16 порода и была цельной закладкой, и другого источника
     * этих чисел не существует. Заодно проверяется, что `pricePerEgg` переехал именно
     * тем значением, каким стоял у закладки, а не значением по умолчанию: перепутать
     * «за яйцо» с «за всё» значит переврать цену в разы, и молча.
     */
    @Test
    fun migrate15To16_movesCullAndMoneyIntoTheSingleBreed() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                "INSERT INTO Incubator " +
                    "(_id, Name, Capacity, Brand, Model, Price, Note, AutoTurn, AutoAiring, Hidden) " +
                    "VALUES (1, 'Блиц', 72, 'Несушка', 'BI-72', 0, '', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO Batch " +
                    "(_id, Name, Type, Date, Egg_all, Egg_all_end, Airing, Overturn, Archive, " +
                    "Date_end, note, incubatorId, Breed, Price, PricePerEgg, EndReason, " +
                    "ChickPrice, ChickPricePerHead, Hidden, Time, Egg_rejected) " +
                    "VALUES (1, 'Куры', 'Курицы', '01.08.2026', 60, 48, 'false', 'false', '1', " +
                    "'22.08.2026', '', 1, '', 900, 0, '', 350, 1, 0, '08:00', 4)"
            )
            db.execSQL(
                "INSERT INTO Batch_breed (id, idPT, name, eggs, hatched) " +
                    "VALUES (1, 1, 'Ломан Браун', 60, 48)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_15_16,
        )

        db.query(
            "SELECT rejected, price, pricePerEgg, chickPrice, chickPricePerHead " +
                "FROM Batch_breed WHERE id = 1"
        ).use { cursor ->
            assertTrue("порода пропала при миграции", cursor.moveToFirst())
            assertEquals("брак закладки не достался её единственной породе", 4, cursor.getInt(0))
            assertEquals(900, cursor.getInt(1))
            assertEquals("«за всё» превратилось в «за яйцо»", 0, cursor.getInt(2))
            assertEquals(350, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
        }

        // Переносить в неё нечего — выбраковку писали одним числом на лоток, — но сама
        // таблица должна существовать вместе со своими индексами.
        db.query(
            "SELECT name FROM sqlite_master WHERE name IN " +
                "('Batch_candling_breed', 'index_Batch_candling_breed_idCandling_breedId', " +
                "'index_Batch_candling_breed_breedId')"
        ).use { cursor ->
            assertEquals("таблица выбраковки по породам создана не целиком", 3, cursor.count)
        }
    }

    /**
     * Метка замера по инкубатору появляется пустой, а сами замеры остаются на месте.
     *
     * Главная проверка — сверка с `17.json`: nullable `TEXT` без умолчания должен
     * совпасть с тем, что Room ждёт от `String?` без `defaultValue`.
     */
    @Test
    fun migrate16To17_addsGroupId_andKeepsMeasurements() {
        helper.createDatabase(TEST_DB, 16).use { db ->
            db.execSQL(
                "INSERT INTO Incubator " +
                    "(_id, Name, Capacity, Brand, Model, Price, Note, AutoTurn, AutoAiring, Hidden) " +
                    "VALUES (1, 'Блиц', 72, 'Несушка', 'BI-72', 0, '', 0, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO Batch " +
                    "(_id, Name, Type, Date, Egg_all, Egg_all_end, Airing, Overturn, Archive, " +
                    "Date_end, note, incubatorId, Breed, Price, PricePerEgg, EndReason, " +
                    "ChickPrice, ChickPricePerHead, Hidden, Time, Egg_rejected) " +
                    "VALUES (1, 'Куры', 'Курицы', '01.08.2026', 60, 0, 'false', 'false', '0', " +
                    "'', '', 1, '', 0, 1, '', 0, 1, 0, '08:00', 0)"
            )
            db.execSQL(
                "INSERT INTO Batch_value (id, day, temp, damp, over, airingCount, airingTime, note, idPT) " +
                    "VALUES (1, 1, 37.8, 55.0, 3, 2, 5, '', 1)"
            )
            db.execSQL(
                "INSERT INTO Batch_measurement " +
                    "(id, idValue, time, temp, damp, over, airingCount, airingTime, note) " +
                    "VALUES (1, 1, '08:15', 37.6, 57.0, 1, NULL, NULL, 'долил воды')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            17,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_16_17,
        )

        db.query("SELECT temp, note, groupId FROM Batch_measurement WHERE id = 1").use { cursor ->
            assertTrue("замер пропал при миграции", cursor.moveToFirst())
            assertEquals(37.6, cursor.getDouble(0), 0.0)
            assertEquals("долил воды", cursor.getString(1))
            assertTrue("у старого замера не должно быть метки группы", cursor.isNull(2))
        }
    }

    /**
     * Порода возвращается в колонку `Batch.Breed`, а таблицы пород уходят.
     *
     * Закладка с одной породой получает её имя, с двумя — оба через запятую в порядке
     * вставки (то, что печаталось на карточке), без пород — пустую строку. Числа
     * закладки не трогаются: они и были суммами. Сверка с `18.json` при
     * `validateDroppedTables = true` — главная проверка: обе таблицы должны исчезнуть,
     * иначе Room откажется открыть базу.
     */
    @Test
    fun migrate17To18_returnsBreedToTheColumn_andDropsTheTables() {
        helper.createDatabase(TEST_DB, 17).use { db ->
            db.execSQL(
                "INSERT INTO Incubator " +
                    "(_id, Name, Capacity, Brand, Model, Price, Note, AutoTurn, AutoAiring, Hidden) " +
                    "VALUES (1, 'Блиц', 72, 'Несушка', 'BI-72', 0, '', 0, 0, 0)"
            )
            for (id in 1..3) {
                db.execSQL(
                    "INSERT INTO Batch " +
                        "(_id, Name, Type, Date, Egg_all, Egg_all_end, Airing, Overturn, Archive, " +
                        "Date_end, note, incubatorId, Breed, Price, PricePerEgg, EndReason, " +
                        "ChickPrice, ChickPricePerHead, Hidden, Time, Egg_rejected) " +
                        "VALUES ($id, 'Куры $id', 'Курицы', '01.08.2026', 50, 40, 'false', 'false', '1', " +
                        "'22.08.2026', '', 1, '', 1340, 0, '', 0, 1, 0, '08:00', 6)"
                )
            }
            db.execSQL(
                "INSERT INTO Batch_breed (id, idPT, name, eggs, hatched, rejected, price, pricePerEgg, chickPrice, chickPricePerHead) VALUES " +
                    "(1, 1, 'Ломан Браун', 20, 18, 2, 22, 1, 0, 1), " +
                    "(2, 1, 'Хайсекс', 30, 22, 4, 900, 0, 0, 1), " +
                    "(3, 2, ' Адлерская ', 50, 40, 6, 1340, 0, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO Batch_candling (id, idPT, day, date, rejected) VALUES (1, 1, 7, '08.08.2026', 5)"
            )
            db.execSQL(
                "INSERT INTO Batch_candling_breed (id, idCandling, breedId, rejected) VALUES (1, 1, 1, 1), (2, 1, 2, 4)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_17_18,
        )

        db.query("SELECT _id, Breed, Egg_all, Egg_all_end, Egg_rejected, Price FROM Batch ORDER BY _id").use { cursor ->
            assertEquals(3, cursor.count)
            cursor.moveToFirst()
            assertEquals("Ломан Браун, Хайсекс", cursor.getString(1))
            assertEquals(50, cursor.getInt(2))
            assertEquals(40, cursor.getInt(3))
            assertEquals(6, cursor.getInt(4))
            assertEquals(1340, cursor.getInt(5))
            cursor.moveToNext()
            assertEquals("Адлерская", cursor.getString(1))
            cursor.moveToNext()
            assertEquals("", cursor.getString(1))
        }
        db.query("SELECT rejected FROM Batch_candling WHERE id = 1").use { cursor ->
            assertTrue("овоскопирование пропало при миграции", cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE name IN ('Batch_breed', 'Batch_candling_breed')"
        ).use { cursor ->
            assertEquals("таблицы пород не удалены", 0, cursor.count)
        }
    }

    /**
     * Имя владельца переживает добавление колонок профиля, а новые колонки у старой
     * строки — пустые строки и `NULL`, то есть «не заполнено» и «VK не привязан».
     */
    @Test
    fun migrate18To19_addsProfileColumns_andKeepsTheName() {
        helper.createDatabase(TEST_DB, 18).use { db ->
            db.execSQL("INSERT INTO User (_id, Name) VALUES (1, 'Семён')")
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            19,
            /* validateDroppedTables = */ true,
            InventoryDatabase.MIGRATION_18_19,
        )

        db.query("SELECT Name, Farm, City, Avatar, VkUserId FROM User WHERE _id = 1").use { cursor ->
            assertTrue("строка владельца пропала при миграции", cursor.moveToFirst())
            assertEquals("Семён", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertTrue("у старого профиля не должно быть фото", cursor.isNull(3))
            assertTrue("старый профиль не должен считаться привязанным к VK", cursor.isNull(4))
        }
    }

    /**
     * `DATABASE_VERSION` в `DatabaseTransfer` — ручной дубликат `@Database(version)`, и
     * связи между двумя числами нет никакой. Забыть поднять первое значит начать
     * отвергать при импорте собственный экспорт этой же сборки — и обнаружится это у
     * человека, а не на сборке.
     *
     * Сверяется не с аннотацией, а с `user_version` открытой базы: аннотация
     * `@Database` хранится с двоичным удержанием, и в рантайме её нет. Зато `user_version`
     * — это ровно то число, которое туда положил Room и с которым потом сравнивает
     * входящий файл проверка импорта.
     */
    @Test
    fun transferConstantMatchesSchemaVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = InventoryDatabase.getDatabase(context).openHelper.writableDatabase
        val version = db.query("PRAGMA user_version").use {
            if (it.moveToFirst()) it.getInt(0) else -1
        }
        assertEquals(version, DATABASE_VERSION)
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
