package ru.zaroslikov.incubator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.zaroslikov.incubator.data.DATABASE_NAME
import ru.zaroslikov.incubator.data.entity.BatchEntity
import ru.zaroslikov.incubator.data.entity.CandlingEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesDayEntity
import ru.zaroslikov.incubator.data.entity.CustomSpeciesEntity
import ru.zaroslikov.incubator.data.entity.IncubatorEntity
import ru.zaroslikov.incubator.data.entity.MeasurementEntity
import ru.zaroslikov.incubator.data.entity.SpeciesEntity
import ru.zaroslikov.incubator.data.entity.TimeEntity
import ru.zaroslikov.incubator.data.entity.UserEntity
import ru.zaroslikov.incubator.data.entity.ValueEntity


@Database(
    entities = [
        IncubatorEntity::class,
        BatchEntity::class,
        TimeEntity::class,
        ValueEntity::class,
        SpeciesEntity::class,
        MeasurementEntity::class,
        CandlingEntity::class,
        UserEntity::class,
        CustomSpeciesEntity::class,
        CustomSpeciesDayEntity::class,
    ],
    version = 20,
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

        /**
         * Появляется `Batch_candling` — итоги овоскопирований: сколько яиц выбраковали
         * и в какой день инкубации это было.
         *
         * До этой версии выбраковку приложение не записывало нигде, и «осталось сейчас»
         * в шторке закладки было равно заложенному просто потому, что вычитать было
         * нечего. Теперь есть.
         *
         * Отдельная таблица, а не колонка в `Batch`: овоскопирований за закладку два-три,
         * и одна колонка сохранила бы только их сумму. Ссылка идёт прямо на закладку, а
         * не на день расписания, как у замеров, — выбраковка это событие закладки, и
         * тянуть его через строку дня незачем; день инкубации лежит тут же колонкой.
         *
         * Уникальный индекс по паре `idPT` + `day` — овоскопирование в один день
         * проводят один раз, и вторая запись за тот же день означала бы, что выбраковку
         * посчитали дважды.
         *
         * Переносить нечего: до этой версии таких данных не существовало.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_candling` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`rejected` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_Batch_candling_idPT_day` " +
                        "ON `Batch_candling` (`idPT`, `day`)"
                )
            }
        }

        /**
         * Итог завершения закладки: `EndReason`, `ChickPrice`, `ChickPricePerHead`.
         *
         * Обычное добавление колонок — завершение теперь спрашивает разное в зависимости
         * от того, вышел ли срок. Досрочное просит причину (`EndReason`) и птенцов не
         * ждёт; завершение в срок просит их количество (`Egg_all_end`, колонка была с
         * самого начала) и цену — за одного либо за всех, ровно тем же способом, каким
         * закладка хранит стоимость яиц.
         *
         * `ChickPricePerHead` заполняется единицей, как и `PricePerEgg` в третьей версии:
         * цены птенцов у существующих закладок нет вовсе, а когда её впервые введут, «за
         * птенца» — то, с чего диалог начинает. Причина досрочного завершения
         * back-fill'у не поддаётся: у закладок, завершённых до этой версии, её никто не
         * спрашивал, и пустая строка там честно значит «неизвестно», а не «в срок».
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `EndReason` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `ChickPrice` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE `Batch` ADD COLUMN `ChickPricePerHead` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * `Batch.Hidden` — «убрана в архив»: закладка пропадает из списка инкубатора.
         *
         * Отдельная колонка, а не переиспользование `Archive`: та с первой версии
         * означает «инкубация окончена», и по ней же считаются средний вывод и деление
         * списка на активные и завершённые. Свести два смысла в одну колонку значило бы
         * либо прятать всё завершённое разом, либо не уметь прятать вовсе.
         *
         * Back-fill нулём — то есть «на виду»: до этой версии прятать было нечем, и все
         * существующие закладки видны, как и были. В подсчётах спрятанная участвует
         * наравне с прочими: она правда была, и вывод инкубатора считается по ней тоже.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `Hidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Перевороты и проветривания становятся числами, а проветривание — ещё и двумя
         * колонками: `airingCount` (сколько раз) и `airingTime` (сколько минут длится
         * одно проветривание). Колонка `airing` уходит из обеих таблиц.
         *
         * Текстом это не считалось. В плане лежало «2-3», «2 раза по 5 минут», «Авто»,
         * «нет» — и интерфейсу приходилось выдёргивать оттуда ведущее число регулярным
         * выражением: у «2 раза по 5 минут» так получались разы, а минуты пропадали.
         * В замере та же колонка означала уже другое — длительность одного
         * проветривания, — и одним полем нельзя было записать и сколько раз открывали
         * инкубатор, и насколько.
         *
         * Как переносятся старые значения:
         * — «Авто» → NULL. Это не ноль: ноль значит «не делать», NULL — «нормы нет,
         *   этим занят сам инкубатор» (та же договорённость, что у `setAutoIncubator`);
         * — диапазон переворотов «2-3», «4-6» → верхняя граница, 3 и 6. Одна из границ
         *   терялась в любом случае, а верхняя — то, о чём рекомендация просит;
         * — «2 раза по 5 минут» → 2 раза и 5 минут: разы — ведущее число, минуты —
         *   число после «по»;
         * — «нет» и всё прочее без цифр → 0 в плане: «не проветривать» — тоже норма;
         * — в замерах пустая строка → NULL: там она значит «не записали», а не «ноль
         *   раз». Замер хранил в `airing` минуты одного проветривания, поэтому непустое
         *   значение даёт одно проветривание указанной длины, а непустой `over` без
         *   цифр — один переворот: отметка «перевернул» была именно такой.
         *
         * Дальше — обычный для этой базы танец create-copy-drop: `Batch_measurement`
         * ссылается на `Batch_value`, а SQLite 3.18 не умеет ни `DROP COLUMN`, ни
         * переписывать `REFERENCES` при переименовании (см. MIGRATION_1_2), поэтому
         * замеры на время перестройки уезжают в таблицу без внешнего ключа.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {

            /**
             * «Авто» перечислено вариантами, а не сведено регистром: `LOWER()` в SQLite
             * работает только с латиницей. Записать это слово могло лишь приложение —
             * `setAutoIncubator` писало ровно «Авто», — так что список исчерпывающий.
             */
            private fun isAuto(column: String): String =
                "TRIM(`$column`) IN ('Авто', 'авто', 'АВТО')"

            /** Есть ли в значении хоть одна цифра — то же условие, что в MIGRATION_4_5. */
            private fun hasDigit(column: String): String = "TRIM(`$column`) GLOB '*[0-9]*'"

            /**
             * Верхняя граница диапазона: «2-3» → 3, «6» → 6, «нет» → 0.
             *
             * `CAST` сам берёт ведущее число и даёт ноль там, где числа нет; дефис в
             * этих строках встречается ровно один, поэтому хватает первого вхождения.
             */
            private fun upperBound(column: String): String {
                val value = "TRIM(`$column`)"
                return "CASE WHEN INSTR($value, '-') > 0 " +
                    "THEN CAST(SUBSTR($value, INSTR($value, '-') + 1) AS INTEGER) " +
                    "ELSE CAST($value AS INTEGER) END"
            }

            /** Минуты после «по» в «2 раза по 5 минут»; без «по» длительности нет. */
            private fun minutesAfterPo(column: String): String {
                val value = "TRIM(`$column`)"
                return "CASE WHEN INSTR($value, 'по ') > 0 " +
                    "THEN CAST(SUBSTR($value, INSTR($value, 'по ') + 3) AS INTEGER) " +
                    "ELSE 0 END"
            }

            override fun migrate(db: SupportSQLiteDatabase) {
                // Норма дня: «Авто» — отсутствие нормы, всё остальное — число.
                val planOver = "CASE WHEN ${isAuto("over")} THEN NULL " +
                    "ELSE ${upperBound("over")} END"
                val planAiringCount = "CASE WHEN ${isAuto("airing")} THEN NULL " +
                    "ELSE CAST(TRIM(`airing`) AS INTEGER) END"
                val planAiringTime = "CASE WHEN ${isAuto("airing")} THEN NULL " +
                    "ELSE ${minutesAfterPo("airing")} END"

                // Замер: пусто — «не записали», непусто без цифр — «было, а сколько
                // именно, запись не сохранила».
                val factOver = "CASE WHEN TRIM(`over`) = '' OR ${isAuto("over")} THEN NULL " +
                    "WHEN ${hasDigit("over")} THEN CAST(TRIM(`over`) AS INTEGER) " +
                    "ELSE 1 END"
                val factAiringCount = "CASE WHEN TRIM(`airing`) = '' THEN NULL ELSE 1 END"
                val factAiringTime = "CASE WHEN TRIM(`airing`) = '' THEN NULL " +
                    "WHEN ${hasDigit("airing")} THEN CAST(TRIM(`airing`) AS INTEGER) " +
                    "ELSE NULL END"

                // 1. Замеры — во временную таблицу без внешнего ключа: пока
                //    `Batch_value` пересобирается, ссылаться на неё нечему.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_measurement_tmp` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idValue` INTEGER NOT NULL, " +
                        "`time` TEXT NOT NULL, " +
                        "`temp` REAL, " +
                        "`damp` REAL, " +
                        "`over` INTEGER, " +
                        "`airingCount` INTEGER, " +
                        "`airingTime` INTEGER, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `Batch_measurement_tmp` " +
                        "(`id`,`idValue`,`time`,`temp`,`damp`,`over`,`airingCount`," +
                        "`airingTime`,`note`) " +
                        "SELECT `id`,`idValue`,`time`,`temp`,`damp`," +
                        "$factOver,$factAiringCount,$factAiringTime,`note` " +
                        "FROM `Batch_measurement`"
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
                        "`over` INTEGER, " +
                        "`airingCount` INTEGER, " +
                        "`airingTime` INTEGER, " +
                        "`note` TEXT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `Batch_value_new` " +
                        "(`id`,`day`,`temp`,`damp`,`over`,`airingCount`,`airingTime`," +
                        "`note`,`idPT`) " +
                        "SELECT `id`,`day`,`temp`,`damp`," +
                        "$planOver,$planAiringCount,$planAiringTime,`note`,`idPT` " +
                        "FROM `Batch_value`"
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
                        "`over` INTEGER, " +
                        "`airingCount` INTEGER, " +
                        "`airingTime` INTEGER, " +
                        "`note` TEXT NOT NULL, " +
                        "FOREIGN KEY(`idValue`) REFERENCES `Batch_value`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO `Batch_measurement` " +
                        "(`id`,`idValue`,`time`,`temp`,`damp`,`over`,`airingCount`," +
                        "`airingTime`,`note`) " +
                        "SELECT `id`,`idValue`,`time`,`temp`,`damp`,`over`,`airingCount`," +
                        "`airingTime`,`note` FROM `Batch_measurement_tmp`"
                )
                db.execSQL("DROP TABLE `Batch_measurement_tmp`")
            }
        }

        /**
         * `Batch.Time` — час закладки, и `Batch.Egg_rejected` — отбраковка, записанная
         * руками. Обычное добавление колонок.
         *
         * Время отдельной колонкой, а не приписанным к `Date`: дату режут на куски и в
         * SQL (`getAllIncubator` сортирует, переставляя её в ISO-порядок), и в
         * интерфейсе, где из неё считают день инкубации, — и всякий разбор сломался бы
         * о лишние пять символов. Back-fill пустой строкой: у закладок, созданных
         * раньше, времени никто не спрашивал, и «» тут значит «неизвестно», а не
         * «00:00» — полночь была бы выдумкой.
         *
         * Отбраковка back-fill'ится нулём и не спорит с `Batch_candling`: та хранит
         * выбраковку по овоскопированиям, эта — то, что убрали между ними. «Осталось»
         * вычитает обе.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `Time` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE `Batch` ADD COLUMN `Egg_rejected` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Заводит таблицу `User` — единственную строку с именем владельца хозяйства.
         *
         * Обычное создание таблицы, без переноса данных: имени пользователя в приложении
         * раньше не было нигде, и взять его неоткуда. Строка не создаётся и здесь —
         * пустая таблица честно значит «не спрашивали», тогда как пустое имя в ней
         * значило бы «спросили, и человек оставил поле пустым». Наружу репозиторий
         * отдаёт в обоих случаях одно и то же, но выдумывать ради этого строку незачем.
         *
         * DDL списан с того, что Room генерирует для [UserEntity]: ключ объявлен без
         * `autoGenerate`, поэтому он выносится в отдельный `PRIMARY KEY(...)` в конце,
         * а не пишется как `INTEGER PRIMARY KEY AUTOINCREMENT` в строке колонки.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `User` (" +
                        "`_id` INTEGER NOT NULL, " +
                        "`Name` TEXT NOT NULL, " +
                        "PRIMARY KEY(`_id`))"
                )
            }
        }

        /**
         * `Incubator.Hidden` — «убран в архив», ровно то же, что `Batch.Hidden` из
         * MIGRATION_7_8, и колонка названа так же намеренно: вопрос один и тот же.
         *
         * Обычное добавление колонки. Заполняется нулём, то есть «на виду»: спрятать
         * инкубатор до двенадцатой версии было нечем, и ни одно устройство не должно
         * пропасть с главного экрана из-за обновления.
         *
         * Второго флага, как `Batch.Archive`, здесь нет и не будет: у закладки есть
         * конец инкубации, а у устройства — нет, и «убран в архив» это единственное,
         * что о его месте в списке можно сказать.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Incubator` ADD COLUMN `Hidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Заводит `Custom_species` и `Custom_species_day` — свои виды птицы.
         *
         * Две таблицы, а не колонки: у вида столько дней, сколько человек описал, и срок
         * инкубации — это их число. В колонки такое не уложить, а одна строка на день —
         * то же самое, чем `Batch_value` описывает расписание закладки, и четыре
         * величины плана здесь названы и типизированы так же намеренно.
         *
         * Внешний ключ дня смотрит прямо в вид, а не через что-либо ещё: день сам по
         * себе ничего не значит, и удаление вида уносит его дни каскадом. Уникальный
         * индекс по паре `speciesId` + `day` — как у `Batch_candling` по `idPT` + `day`:
         * второй режим на тот же день означал бы, что неизвестно, какой из них верен.
         *
         * Уникальный индекс по `Name` — условие целостности, а не аккуратность: закладка
         * ссылается на вид по имени (`Batch.Type`, чип в `Batch_species`), ровно как на
         * встроенный, и два вида с одним именем сделали бы невозможным ответ на вопрос,
         * чей режим её породил. По той же причине переименование вида проходит по
         * закладкам — см. `ItemDao.saveCustomSpecies`.
         *
         * Переносить нечего: своих видов до этой версии не существовало, а встроенные
         * как жили кодом в `domain.incubation`, так и живут — таблица их не заменяет.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Custom_species` (" +
                        "`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`Name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_Custom_species_Name` " +
                        "ON `Custom_species` (`Name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Custom_species_day` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`speciesId` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, " +
                        "`temp` REAL, " +
                        "`damp` REAL, " +
                        "`over` INTEGER, " +
                        "`airingCount` INTEGER, " +
                        "`airingTime` INTEGER, " +
                        "`candling` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`speciesId`) REFERENCES `Custom_species`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_Custom_species_day_speciesId_day` " +
                        "ON `Custom_species_day` (`speciesId`, `day`)"
                )
            }
        }

        /**
         * Ставит индексы на пять внешних ключей: `Batch.incubatorId`, `Batch_value.idPT`,
         * `Batch_measurement.idValue`, `Batch_time.idPT`, `Batch_species.idPT`.
         *
         * До этой версии в базе было три индекса, и все три — уникальные, поставленные
         * ради целостности; ни одного по внешнему ключу не было. Дороже всего это
         * обходилось при удалении: `ON DELETE CASCADE` для каждой из семнадцати–тридцати
         * удаляемых строк `Batch_value` заставлял SQLite просмотреть `Batch_measurement`
         * целиком, а удаление инкубатора умножало это на число его закладок. Тем же
         * полным сканом шли и обычные выборки — дни закладки, замеры дня, времена
         * напоминаний.
         *
         * Обычное добавление индексов, без пересоздания таблиц: данные не трогаются,
         * откатывать нечего. Имена — ровно те, что генерирует Room (`index_<таблица>_<колонка>`);
         * своё имя не прошло бы проверку схемы при открытии.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Batch_incubatorId` ON `Batch` (`incubatorId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Batch_value_idPT` ON `Batch_value` (`idPT`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_Batch_measurement_idValue` " +
                        "ON `Batch_measurement` (`idValue`)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Batch_time_idPT` ON `Batch_time` (`idPT`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Batch_species_idPT` ON `Batch_species` (`idPT`)")
            }
        }

        /**
         * Породы закладки переезжают из текстовой колонки `Batch.Breed` в таблицу
         * `Batch_breed` — по строке на породу, с её яйцами и выводом.
         *
         * Одна колонка держала одну породу, а в лотке их бывает две и больше — и тогда
         * либо породу записывали одну, либо лоток резали на две закладки с одинаковым
         * расписанием, и «Эффективность по породам» получала закладки, которых не было.
         *
         * Перенос: каждая непустая `Breed` становится одной строкой с `Egg_all` и
         * `Egg_all_end` своей закладки — ровно то, чем одна порода и была. Затем колонка
         * опустошается: читать её больше некому, а старое значение рядом со строками
         * означало бы два ответа на вопрос «какая порода», и рано или поздно разных.
         * Сама колонка остаётся: убрать колонку в SQLite значит пересоздать `Batch`,
         * на которую ссылаются пять дочерних таблиц, — не та цена за пустое поле.
         *
         * DDL и имя индекса — те, что генерирует Room (`15.json`); проверка схемы при
         * открытии сверяет их посимвольно.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_breed` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idPT` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`eggs` INTEGER NOT NULL, " +
                        "`hatched` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idPT`) REFERENCES `Batch`(`_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Batch_breed_idPT` ON `Batch_breed` (`idPT`)")
                db.execSQL(
                    "INSERT INTO `Batch_breed` (`idPT`, `name`, `eggs`, `hatched`) " +
                        "SELECT `_id`, TRIM(`Breed`), `Egg_all`, `Egg_all_end` FROM `Batch` " +
                        "WHERE TRIM(`Breed`) != ''"
                )
                db.execSQL("UPDATE `Batch` SET `Breed` = ''")
            }
        }

        /**
         * Порода получает собственную отбраковку и обе свои цены, а выбраковка
         * овоскопирования — разбор по породам (`Batch_candling_breed`).
         *
         * До этой версии порода знала только «заложено» и «выведено», а брак и деньги
         * были у закладки целиком — то есть на вопрос «какая порода дороже обошлась и
         * какая больше отсеялась» ответа не было вовсе, хотя ради таких вопросов лоток
         * по породам и делят. Пять колонок — те же пары «величина + смысл», что у самой
         * закладки: `rejected`, `price` + `pricePerEgg`, `chickPrice` + `chickPricePerHead`.
         *
         * **Перенос идёт только в закладки с одной породой**, и это не осторожность, а
         * единственное, что здесь можно знать наверняка: до v16 всякая строка породы
         * была цельной закладкой (её создала MIGRATION_14_15 из колонки `Breed`), и её
         * брак с ценами — это брак и цены закладки. Разложить числа закладки с двумя
         * породами задним числом нельзя ничем, кроме выдумки; таких закладок, впрочем, и
         * не существует — делить лоток научились в той же версии, где появились колонки.
         * Условие по числу строк оставлено явным: оно и есть проверка этого допущения.
         *
         * Переносить в `Batch_candling_breed` нечего по той же причине: выбраковку
         * писали одним числом на лоток, и старые овоскопирования остаются без разбора —
         * закладка и инкубатор считают их целиком, а разрез по породам их не видит
         * (прежняя `IncubatorStats.breedLinesOf`).
         *
         * DDL и имена индексов — те, что генерирует Room (`16.json`): проверка схемы при
         * открытии сверяет их посимвольно.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch_breed` ADD COLUMN `rejected` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `Batch_breed` ADD COLUMN `price` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `Batch_breed` ADD COLUMN `pricePerEgg` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `Batch_breed` ADD COLUMN `chickPrice` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE `Batch_breed` ADD COLUMN `chickPricePerHead` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "UPDATE `Batch_breed` SET " +
                        "`rejected` = (SELECT `Egg_rejected` FROM `Batch` WHERE `Batch`.`_id` = `Batch_breed`.`idPT`), " +
                        "`price` = (SELECT `Price` FROM `Batch` WHERE `Batch`.`_id` = `Batch_breed`.`idPT`), " +
                        "`pricePerEgg` = (SELECT `PricePerEgg` FROM `Batch` WHERE `Batch`.`_id` = `Batch_breed`.`idPT`), " +
                        "`chickPrice` = (SELECT `ChickPrice` FROM `Batch` WHERE `Batch`.`_id` = `Batch_breed`.`idPT`), " +
                        "`chickPricePerHead` = (SELECT `ChickPricePerHead` FROM `Batch` WHERE `Batch`.`_id` = `Batch_breed`.`idPT`) " +
                        "WHERE (SELECT COUNT(*) FROM `Batch_breed` AS `other` " +
                        "WHERE `other`.`idPT` = `Batch_breed`.`idPT`) = 1"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Batch_candling_breed` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idCandling` INTEGER NOT NULL, " +
                        "`breedId` INTEGER NOT NULL, " +
                        "`rejected` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`idCandling`) REFERENCES `Batch_candling`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`breedId`) REFERENCES `Batch_breed`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_Batch_candling_breed_idCandling_breedId` " +
                        "ON `Batch_candling_breed` (`idCandling`, `breedId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_Batch_candling_breed_breedId` " +
                        "ON `Batch_candling_breed` (`breedId`)"
                )
            }
        }

        /**
         * Замер по инкубатору: метка `Batch_measurement.groupId`.
         *
         * Одна колонка, обычный `ADD COLUMN`, без умолчания — `NULL` у всего, что было
         * записано до неё, и это честно: до v17 замер вносили только внутри закладки, и
         * ни один из старых не снимался «на весь инкубатор». Новая шторка кладёт одно
         * показание прибора по копии в каждую идущую закладку и помечает копии одним
         * UUID; у самих копий структура прежняя, так что читать их продолжают все, кто
         * читал (`MeasurementEntity`). Индекса нет: выборка идёт по `idValue`, метка
         * сравнивается уже среди замеров одного дня. Nullable `TEXT` без `DEFAULT` —
         * ровно то, что Room генерирует для `String?` без `defaultValue` (`17.json`).
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch_measurement` ADD COLUMN `groupId` TEXT")
            }
        }

        /**
         * Порода возвращается в колонку `Batch.Breed`; таблицы `Batch_breed` и
         * `Batch_candling_breed` удаляются.
         *
         * Правило теперь простое: **в одной закладке одна порода**. Лоток с двумя породами
         * закладывают двумя закладками — форма умеет сделать это одним нажатием, — и
         * тогда у каждой породы свой вывод, своя отбраковка и свои деньги без всякого
         * второго учёта. Три версии (15–17) породы лежали строками с собственными
         * числами, и каждое число закладки приходилось держать в двух местах и сводить
         * в одно (`Batch.withBreeds`); эта версия возвращает одно поле, которое было до
         * пятнадцатой, — ровно потому, что колонка так и не была удалена.
         *
         * Перенос: имена строк закладки склеиваются через запятую в порядке вставки —
         * то, что и печаталось на карточке (прежняя `breedLabel`). Закладка с двумя породами
         * **не режется на две**: расписание, замеры и овоскопирования у неё одни на
         * всех, и вторая закладка с их копиями была бы закладкой, которой никто не
         * делал; а её общие числа — заложено, выведено, отбраковано, цена — уже суммы по
         * породам (MIGRATION_15_16 и `withBreeds` держали их такими), так что ни одно
         * яйцо и ни один рубль не теряются. Теряется только разрез по породам внутри
         * такой закладки, которого в новой модели нет. **Цена склейки названа честно**:
         * «Ломан Браун, Хайсекс» после миграции — обычная порода, и как обычная она
         * встанет отдельной строкой в статистике и в подсказках под полем породы, пока
         * закладку не поправят руками. Так выглядела бы и закладка, в которой две
         * породы вписали через запятую до пятнадцатой версии, — поле их не запрещало;
         * оставить одно имя из двух значило бы солгать о том, что лежало в лотке. Выбраковка овоскопирований по
         * породам уходит с таблицей по той же причине: `Batch_candling.rejected` —
         * её сумма и была.
         *
         * `group_concat` в SQLite порядка не обещает, но коррелированный подзапрос по
         * `idPT` идёт по индексу `index_Batch_breed_idPT`, то есть в порядке rowid внутри
         * закладки — как строки и вставлялись; тест миграции это закрепляет. Пустые
         * имена в строках не встречались (форма их не пускала), но `TRIM` на всякий случай.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `Batch` SET `Breed` = COALESCE((" +
                        "SELECT group_concat(TRIM(`name`), ', ') FROM `Batch_breed` " +
                        "WHERE `idPT` = `Batch`.`_id` AND TRIM(`name`) != ''), '')"
                )
                db.execSQL("DROP TABLE IF EXISTS `Batch_candling_breed`")
                db.execSQL("DROP TABLE IF EXISTS `Batch_breed`")
            }
        }

        /**
         * Потребление и тариф — у инкубатора и у закладки одними и теми же пятью
         * колонками: мощность, дневная и ночная цена киловатт-часа и часы ночного тарифа.
         *
         * Обычное добавление колонок, без переноса: о свете приложение до девятнадцатой
         * версии не знало ничего, и `NULL` у числовых — правда («не указано»), а не
         * значение по умолчанию. Часы — пустые строки, как `Batch.Time` в десятой: не
         * спрашивали. Закладка с пустыми полями считается по полям своего инкубатора
         * (`PowerSettings.over`), так что старые закладки получат свет, как только его
         * впишут в инкубатор.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("Incubator", "Batch")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `PowerWatts` INTEGER")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `TariffDay` REAL")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `TariffNight` REAL")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `NightStart` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `NightEnd` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /**
         * `Batch.TimeEnd` — час, в который закладку закончили: выключили инкубатор или
         * вынули птенцов. Диалог завершения спрашивает его вместе с датой ради счёта за
         * свет. Пустая строка у всех старых закладок — правда: не спрашивали; тогда концом
         * считается дата окончания в час закладки, как и до этой версии.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Batch` ADD COLUMN `TimeEnd` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Закрывает открытую базу и забывает её.
         *
         * Нужно ровно одному месту — импорту базы из файла: подменить файл под открытым
         * Room нельзя, соединение продолжит писать в старые страницы и в WAL. После
         * подмены синглтон должен быть пуст, чтобы следующий [getDatabase] открыл уже
         * новый файл и прогнал по нему миграции.
         */
        internal fun closeAndForget() {
            synchronized(this) {
                Instance?.close()
                Instance = null
            }
        }

        /**
         * Собирает Room по произвольному имени файла базы — со всеми миграциями,
         * но **не** записывая результат в синглтон.
         *
         * Нужно ровно одному месту: проверке импортируемого файла (`DatabaseTransfer`).
         * Убедиться, что чужой файл вообще откроется этой сборкой, нельзя иначе, как
         * открыв его настоящим Room — только он сверяет identity hash схемы и только он
         * знает, какие миграции по нему прогнать. Делать это надо до того, как рабочая
         * база закрыта, поэтому экземпляр здесь отдельный и закрывать его обязан вызвавший.
         */
        internal fun buildFor(context: Context, fileName: String): InventoryDatabase =
            builder(context, fileName).build()

        /**
         * Держит монитор синглтона на время [block].
         *
         * Нужно подмене файла при импорте. Между `closeAndForget()` и переименованием
         * есть промежуток, в который `getDatabase` открыт всему процессу: любой поток —
         * параллельный экспорт, только что созданная ViewModel — успел бы открыть
         * рабочий файл заново, Room положил бы рядом свежие `-wal`/`-shm`, и живое
         * соединение осталось бы на файле, который через мгновение подменят. Здесь берётся
         * тот же монитор, на котором синхронизирован [getDatabase], поэтому на время
         * переноса открыть базу не может никто. Вложенные `synchronized` на том же объекте
         * реентерабельны, так что [closeAndForget] и [getDatabase] внутри блока работают.
         */
        internal fun <T> exclusively(block: () -> T): T = synchronized(this) { block() }

        fun getDatabase(context: Context): InventoryDatabase {
            // Двойная проверка, и второй `Instance ?:` — не украшение. Без него два
            // потока, оба увидевшие null снаружи, по очереди создадут два экземпляра
            // Room над одним файлом: в поле останется второй, а первый — с живым
            // соединением — не закроется никогда, и `closeAndForget` его не найдёт.
            return Instance ?: synchronized(this) {
                Instance ?: builder(context, DATABASE_NAME)
                    .build()
                    .also { Instance = it }
            }
        }

        /**
         * Один список миграций на оба входа. Разъехавшись, они дали бы худший из
         * возможных исходов: файл, который проверка приняла, а рабочее открытие — нет.
         */
        private fun builder(context: Context, fileName: String) =
            Room.databaseBuilder(context, InventoryDatabase::class.java, fileName)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                )
    }
}
