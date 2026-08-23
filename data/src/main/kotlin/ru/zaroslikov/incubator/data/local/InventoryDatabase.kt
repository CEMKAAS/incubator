package ru.zaroslikov.incubator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.ValueEntity


@Database(
    entities = [
        IncubatorEntity::class,
        BatchEntity::class,
        TimeEntity::class,
        ValueEntity::class,
        SpeciesEntity::class,
        MeasurementEntity::class,
    ],
    version = 5,
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var Instance: InventoryDatabase? = null

        /**
         * Первая версия не знала об инкубаторе как об устройстве: таблица `Incubator`
         * хранила закладки. Вторая вводит настоящий инкубатор, переименовывает закладку
         * в `Batch` и складывает все существующие закладки в один созданный инкубатор.
         *
         * Дочерние таблицы не переименовываются, а создаются заново с копированием.
         * Причина: minSdk 26 — это SQLite 3.18, где `ALTER TABLE ... RENAME TO` не
         * переписывает `REFERENCES` в других таблицах (поведение появилось в 3.25,
         * то есть только с API 29). Полагаться на него нельзя.
         *
         * fallbackToDestructiveMigration() здесь намеренно отсутствует: с ним это
         * обновление стёрло бы все закладки пользователей.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Освобождаем имя `Incubator` под устройство.
                db.execSQL("ALTER TABLE `Incubator` RENAME TO `Batch_tmp`")

                // 2. Устройство и единственная синтезированная строка.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Incubator` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`Name` TEXT NOT NULL, " +
                        "`Capacity` INTEGER NOT NULL, " +
                        "`Brand` TEXT NOT NULL, " +
                        "`Model` TEXT NOT NULL, " +
                        "`Price` INTEGER NOT NULL, " +
                        "`Note` TEXT NOT NULL, " +
                        "`AutoTurn` INTEGER NOT NULL, " +
                        "`AutoAiring` INTEGER NOT NULL)"
                )
                // Вместимость остаётся нулевой: в первой версии таких данных не было,
                // и выдумывать её за пользователя нельзя — интерфейс попросит заполнить.
                // Автоматику, наоборот, выводим из реальных закладок: если хоть одна шла
                // на автоперевороте, устройство им обладает.
                db.execSQL(
                    "INSERT INTO `Incubator` " +
                        "(`Name`,`Capacity`,`Brand`,`Model`,`Price`,`Note`,`AutoTurn`,`AutoAiring`) " +
                        "SELECT 'Мой инкубатор', 0, '', '', 0, '', " +
                        "COALESCE(MAX(CASE WHEN `Overturn` = 'true' THEN 1 ELSE 0 END), 0), " +
                        "COALESCE(MAX(CASE WHEN `Airing` = 'true' THEN 1 ELSE 0 END), 0) " +
                        "FROM `Batch_tmp`"
                )

                // 3. Закладки переезжают в `Batch` и получают ссылку на инкубатор.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`Name` TEXT NOT NULL, " +
                        "`Type` TEXT NOT NULL, " +
                        "`Date` TEXT NOT NULL, " +
                        "`Egg_all` INTEGER NOT NULL, " +
                        "`Egg_all_end` INTEGER NOT NULL, " +
                        "`Airing` TEXT NOT NULL, " +
                        "`Overturn` TEXT NOT NULL, " +
                        "`Archive` TEXT NOT NULL, " +
                        "`Date_end` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`incubatorId` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`incubatorId`) REFERENCES `Incubator`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `Batch` " +
                        "(`_id`,`Name`,`Type`,`Date`,`Egg_all`,`Egg_all_end`," +
                        "`Airing`,`Overturn`,`Archive`,`Date_end`,`note`,`incubatorId`) " +
                        "SELECT `_id`,`Name`,`Type`,`Date`,`Egg_all`,`Egg_all_end`," +
                        "`Airing`,`Overturn`,`Archive`,`Date_end`,`note`, " +
                        "(SELECT `_id` FROM `Incubator` LIMIT 1) FROM `Batch_tmp`"
                )

                // 4. Дочерние таблицы закладки.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_time` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_value` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`temp` TEXT NOT NULL, " +
                        "`damp` TEXT NOT NULL, " +
                        "`over` TEXT NOT NULL, " +
                        "`airing` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_species` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`species` TEXT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )

                // 5. Переносим напоминания и дни.
                db.execSQL(
                    "INSERT INTO `Batch_time` (`id`,`time`,`idPT`) " +
                        "SELECT `id`,`time`,`idPT` FROM `Incubator_time`"
                )
                db.execSQL(
                    "INSERT INTO `Batch_value` " +
                        "(`id`,`day`,`temp`,`damp`,`over`,`airing`,`note`,`idPT`) " +
                        "SELECT `id`,`day`,`temp`,`damp`,`over`,`airing`,`note`,`idPT` " +
                        "FROM `Incubator_value`"
                )

                // 6. Старые таблицы больше не нужны.
                db.execSQL("DROP TABLE `Incubator_time`")
                db.execSQL("DROP TABLE `Incubator_value`")
                db.execSQL("DROP TABLE `Batch_tmp`")

                // 7. Ведущий вид становится первым чипом на карточке.
                db.execSQL(
                    "INSERT INTO `Batch_species` (`species`,`idPT`) " +
                        "SELECT `Type`, `_id` FROM `Batch`"
                )
            }
        }

        /**
         * Форма закладки из макета
         * [12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555)
         * просит три вещи, которых схема не знала: породу внутри вида, стоимость партии
         * и текст у каждого напоминания.
         *
         * Это обычное добавление колонок, поэтому `ALTER TABLE ... ADD COLUMN` достаточно.
         * `DEFAULT` в DDL здесь безопасен, хотя сущности значений по умолчанию не
         * объявляют: Room сверяет defaultValue только когда его задаёт сама сущность
         * (TableInfo.Column.equalsCommon).
         *
         * `PricePerEgg` заполняется единицей, а не нулём: у существующих закладок цены
         * нет вовсе, и когда её впервые введут, разумнее считать её ценой за яйцо —
         * ровно так, как предлагает макет по умолчанию.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `Breed` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `Price` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `PricePerEgg` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `Batch_time` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Фактические замеры температуры и влажности — шторка закладки из макета
         * [14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893).
         *
         * Новая таблица, а не колонки в `Batch_value`: за один день инкубации замеров
         * бывает сколько угодно, а строка `Batch_value` — ровно одна на день, и хранит
         * она план, а не факт.
         *
         * Замер привязан к строке `Batch_value` — к дню расписания, а не к календарной дате:
         * дату начала закладки правят, дату на телефоне тоже, и замер, привязанный к числу,
         * после этого оказался бы не в том дне инкубации. Каскад доходит сюда через две
         * ступени: `Batch` → `Batch_value` → `Batch_measurement`.
         *
         * Данных для переноса нет — до этой версии замеров не существовало.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_measurement` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idValue` INTEGER NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`temp` TEXT NOT NULL, " +
                        "`damp` TEXT NOT NULL, " +
                        "`over` TEXT NOT NULL, " +
                        "`airing` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "FOREIGN KEY(`idValue`) REFERENCES `Batch_value`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        /**
         * Температура и влажность перестают быть текстом: в `Batch_value` (план) и
         * `Batch_measurement` (факт) колонки `temp` и `damp` становятся REAL.
         *
         * Строками они быть и не могли: приложение сравнивает замер с планом и считает
         * отклонение, а «60» минус «55» текстом не вычесть — до сих пор это делалось
         * разбором строки на каждом кадре отрисовки.
         *
         * Тип колонки в SQLite не меняют на месте, поэтому обе таблицы пересоздаются
         * и переливаются. Порядок здесь несущий: `Batch_measurement` ссылается на
         * `Batch_value`, а значит родителя нельзя ни удалить, ни подменить, пока живёт
         * ссылающийся на него ребёнок. Отсюда три шага — замеры уезжают в промежуточную
         * таблицу **без** внешнего ключа, дни пересобираются, замеры возвращаются уже с
         * ключом на новую таблицу. Идентификаторы дней переносятся как есть, поэтому
         * привязка замеров переживает перестройку.
         *
         * Как переводится текст в число:
         * — запятая заменяется точкой: «37,5» на цифровой клавиатуре набирается легко,
         *   и такие значения в базе есть — в том числе в расписании перепелов;
         * — строка без единой цифры («нет», пустая) становится NULL, а не нулём:
         *   ноль градусов — это значение, а «не указано» — отсутствие значения;
         * — из диапазона вроде «37-38» SQLite возьмёт 37. Потеря настоящая, но
         *   неизбежная: в Double диапазон не помещается, а других вариантов у колонки
         *   типа REAL нет.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {

            /** `CAST` поверх нормализации: без проверки на цифру «нет» превратилось бы в 0.0. */
            private fun realFrom(column: String): String =
                "CASE WHEN REPLACE(TRIM(`$column`), ',', '.') GLOB '*[0-9]*' " +
                    "THEN CAST(REPLACE(TRIM(`$column`), ',', '.') AS REAL) ELSE NULL END"

            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Замеры — во временную таблицу без внешнего ключа, чтобы на время
                //    перестройки ничто не ссылалось на `Batch_value`.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_measurement_tmp` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idValue` INTEGER NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`temp` REAL, " +
                        "`damp` REAL, " +
                        "`over` TEXT NOT NULL, " +
                        "`airing` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `Batch_measurement_tmp` " +
                        "(`id`,`idValue`,`time`,`temp`,`damp`,`over`,`airing`,`note`) " +
                        "SELECT `id`,`idValue`,`time`," +
                        "${realFrom("temp")},${realFrom("damp")}," +
                        "`over`,`airing`,`note` FROM `Batch_measurement`"
                )
                db.execSQL("DROP TABLE `Batch_measurement`")

                // 2. Дни расписания. Идентификаторы переносятся как есть — на них
                //    держится привязка замеров.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_value_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`temp` REAL, " +
                        "`damp` REAL, " +
                        "`over` TEXT NOT NULL, " +
                        "`airing` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `Batch_value_new` " +
                        "(`id`,`day`,`temp`,`damp`,`over`,`airing`,`note`,`idPT`) " +
                        "SELECT `id`,`day`," +
                        "${realFrom("temp")},${realFrom("damp")}," +
                        "`over`,`airing`,`note`,`idPT` FROM `Batch_value`"
                )
                db.execSQL("DROP TABLE `Batch_value`")
                db.execSQL("ALTER TABLE `Batch_value_new` RENAME TO `Batch_value`")

                // 3. Замеры возвращаются, уже с ключом на пересобранные дни.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_measurement` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idValue` INTEGER NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`temp` REAL, " +
                        "`damp` REAL, " +
                        "`over` TEXT NOT NULL, " +
                        "`airing` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "FOREIGN KEY(`idValue`) REFERENCES `Batch_value`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `Batch_measurement` " +
                        "(`id`,`idValue`,`time`,`temp`,`damp`,`over`,`airing`,`note`) " +
                        "SELECT `id`,`idValue`,`time`,`temp`,`damp`,`over`,`airing`,`note` " +
                        "FROM `Batch_measurement_tmp`"
                )
                db.execSQL("DROP TABLE `Batch_measurement_tmp`")
            }
        }

        fun getDatabase(context: Context): InventoryDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, InventoryDatabase::class.java, "incubator_database")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
