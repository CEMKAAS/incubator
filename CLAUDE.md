# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Инкубатор" — a Russian-language Android app (Jetpack Compose + Room, offline-only) for tracking poultry egg incubation.

Two entities, and keeping them apart matters: an **`Incubator`** is the physical device (name and capacity required, brand / model / price / note optional, auto-turn and auto-airing flags required), and a **`Batch`** (закладка) is one clutch of eggs inside it — species, start date, egg count. Creating a batch materializes a full per-day schedule of temperature / humidity / turning / airing rows (`Value`, one per incubation day), a set of daily reminder times (`Time`), and its species chips (`Species`).

**The naming is now literal, and it should stay that way.** Anything named `Incubator*` is the device and lives in `ui/incubator/`; anything named `Batch*` is a clutch and lives in `ui/batch/`. Before schema v2 the table called `Incubator` actually held batches and every batch screen was named after the incubator, so old commits and any stale branch will read the opposite way.

Two deliberate exceptions, both in `ui/batch/`: the AppMetrica event names (`"Инкубатор"`, `"Incubator Edit"`, `"Incubator Archive"`) keep their original wording so historical analytics stay comparable, and user-facing copy like "заложили в инкубатор" or "убрать из инкубатора" is correct as written — the eggs really do go into the device.

All UI copy is hardcoded Russian string literals in Kotlin — `strings.xml` holds only `app_name`.

## Build & test

Requires JDK 17+ and an Android SDK path in `local.properties` (`sdk.dir`). `java` is not on the shell PATH in this environment — use Android Studio's bundled JBR or set `JAVA_HOME` before invoking Gradle. Verified against both JDK 17 and the Android Studio JBR (25).

```powershell
.\gradlew.bat assembleDebug          # build
.\gradlew.bat build                  # debug + release + lint + unit tests
.\gradlew.bat installDebug           # build + install on connected device/emulator
.\gradlew.bat test                   # host unit tests (app/src/test)
.\gradlew.bat connectedAndroidTest   # instrumented tests (needs device)
.\gradlew.bat testDebugUnitTest --tests "ru.zaroslikov.incubator.ExampleUnitTest"   # single test
.\gradlew.bat clean
```

`:app` has effectively no tests — only the generated `ExampleUnitTest` / `ExampleInstrumentedTest`. The one real test is `:domain`'s `IncubationPeriodTest`; because `:domain` is a plain JVM module, `gradlew :domain:test` runs in seconds with no emulator, which makes it the natural home for further tests of the species logic. Real verification of a change is still `gradlew build` (which also runs lint on debug and release) plus running the app.

`lint` is part of `build` and **fails the build on errors**. `ReminderWorker` guards its `NotificationManagerCompat.notify` call with an explicit `POST_NOTIFICATIONS` check specifically to satisfy lint's `MissingPermission`; do not drop that guard.

Git in this repo may report "dubious ownership"; fix with `git config --global --add safe.directory D:/Work/_Project/Incubator`.

## Toolchain and dependency versions

`gradle/libs.versions.toml` is the **single source of truth** — the root and app build scripts declare no inline versions, and plugins are applied via `alias(libs.plugins.…)`. Add new dependencies to the catalog, not as coordinate strings.

Current stack: Gradle 9.7.1, AGP 9.3.1, Kotlin 2.3.21, KSP 2.3.11, Compose BOM 2026.08.00, Room 2.8.4.

Constraints worth knowing before bumping anything:

- **AGP 9 supplies Kotlin itself** for both `com.android.application` and `com.android.library`. Applying `org.jetbrains.kotlin.android` is now a hard error ("no longer required for Kotlin support since AGP 9.0"). There is no `composeOptions.kotlinCompilerExtensionVersion` any more — the Compose compiler *is* the Kotlin plugin.
- **`:domain` is the one module that needs an explicit Kotlin plugin** (`org.jetbrains.kotlin.jvm`), since AGP only supplies Kotlin to Android modules. It is also why `:domain` declares `kotlinx-coroutines-core` explicitly — it gets nothing transitively from AndroidX. That dependency is `api`, not `implementation`, because `Flow` appears in `ItemsRepository`'s signatures.
- **Every plugin must be declared in the root `build.gradle.kts` with `apply false`.** Applying `com.android.library` with a version directly in `data/build.gradle.kts` fails with "the plugin is already on the classpath with an unknown version" once `:app` has pulled AGP in.
- `kotlin-parcelize` was removed: `Parcelize`/`Parcelable` are used nowhere. `ksp` is applied only in `:data` — `:app` has no code generation left.
- **KSP pins the Kotlin version.** KSP publishes no releases on the Kotlin 2.4 line, so Kotlin cannot go past 2.3.x while Room is generated through KSP. Check `com.google.devtools.ksp:symbol-processing-gradle-plugin` on Maven Central before raising `kotlin`.
- **compileSdk 37 is forced by AndroidX** — `core-ktx` 1.19 and its siblings refuse to compile against 36.
- **`targetSdk` is 37, which means edge-to-edge is mandatory on Android 15+.** The app never calls `enableEdgeToEdge()` and does not manage insets explicitly, so it relies entirely on Material3 defaults. The likely trouble spot, which lint does not flag (it reports 0 errors): the root `Scaffold` in `InventoryApp` applies `systemBars` content insets *and* every screen nests its own `Scaffold` + `TopAppBar` that applies status-bar insets again, so top spacing can be counted twice. Verify on an Android 15+ device before shipping.
- `kotlinOptions` was replaced by the `kotlin { compilerOptions { jvmTarget … } }` block.
- Material icons are no longer transitive from material3; `material-icons-core` is declared explicitly (the BOM still pins it, at its final 1.7.8). Every icon the app uses — `ArrowBack`, `Close`, `DateRange`, `Info`, `Notifications`, `Add`, `Settings` — lives in *core*, so `material-icons-extended` is not needed.

## Modules

Three Gradle modules; the dependency graph is `:app → :data → :domain` and `:app → :domain`.

```
:domain   pure Kotlin/JVM — no Android, no Room. Only kotlinx-coroutines (for Flow).
  domain.model/        Incubator, Batch, Value, Time, Species, Measurement
                       (plain data classes)
  domain.repository/   ItemsRepository, WorkRepository
  domain.incubation/   species regimens, incubation length, candling days

:data     com.android.library, namespace ru.zaroslikov.incubator.data
  data.entity/         IncubatorEntity, BatchEntity, TimeEntity, MeasurementEntity,
                       ValueEntity, SpeciesEntity   (@Entity twins; tables
                       Incubator, Batch, Batch_time, Batch_value,
                       Batch_species, Batch_measurement)
  data.mapper/         Mappers.kt — toDomain() / toEntity()
  data.local/          ItemDao, InventoryDatabase
  data.repository/     OfflineItemsRepository
  DataProvider.kt      provideItemsRepository(context) — the module's only entry point

:app      com.android.application — Compose UI, ViewModels, composition root
  di/                  AppContainer, AppDataContainer
  work/                Constants, ReminderWorker, WorkManagerRepository
  ui/start/            IncubatorScreen — list of devices with aggregates
  ui/incubator/        one device: its batches (IncubatorScreen) and its form
                       (AddIncubatorSheet, create + edit in one)
  ui/batch/            one clutch: BatchDetailSheet (the tap target),
                       BatchAnalyticsSheet + BatchAnalytics (day chart and
                       figures), BatchScreen (days), BatchDayScreen,
                       CandlingScreen, BatchArchiveScreen, BatchUiState
                       (form state + mappers), AddBatchSheet (create and
                       edit in one), TimePickerDialog,
                       AddBatchScheduleScreen (no longer reachable)
  ui/components/       SheetForm — the pieces both bottom sheets share
  ui/navigation/, ui/theme/
```

**Room is confined to `:data` and must stay there.** `room-runtime` is an `implementation`
dependency, so `:app` cannot see `RoomDatabase` at all — that is why `:data` exposes
`provideItemsRepository(context)` instead of handing out `InventoryDatabase`. If you find
yourself wanting `api(libs.androidx.room.runtime)`, add a factory function to `:data`
instead. Verify with `gradlew :app:dependencies --configuration debugCompileClasspath | grep androidx.room` — it must return nothing.

WorkManager lives in `:app`, not `:data`, because `ReminderWorker` builds a notification
using `R.drawable.baseline_egg_24` and a `PendingIntent` into `MainActivity`. The
`WorkRepository` interface still lives in `:domain`, so ViewModels depend on the abstraction.

## Architecture

Manual DI, no Hilt/Koin:

- `InventoryApplication` (also `Configuration.Provider` for WorkManager) builds an `AppDataContainer` exposing `itemsRepository` and `workRepository`.
- `ui/AppViewModelProvider.Factory` is a single `viewModelFactory` holding one `initializer` per ViewModel, pulling dependencies off the container via the `CreationExtras.inventoryApplication()` extension. **Every new ViewModel must be registered there**, and screens obtain them with `viewModel(factory = AppViewModelProvider.Factory)`.
- Adding a repository query touches four places: `ItemDao` (entity types), the mapper if a new type appears, the `ItemsRepository` interface in `:domain`, and `OfflineItemsRepository`, which maps at the boundary (`Flow` via `kotlinx.coroutines.flow.map`).
- Domain models and Room entities are separate classes with identical field names. The domain copies keep the **same mutability** as the entities (`Value.id`, `idPT`, `airing`, `over`, `temp`, `damp`, `note` are `var`; `Time` is all `var`; `Batch` only `arhive`) because UI code mutates them in place — see `ScheduleTransforms.setIdPT` / `setAutoIncubator`. Turning them into `val` breaks the UI.
- `Batch.airing` / `Batch.over` do **not** duplicate `Incubator.autoAiring` / `autoTurn`. The device fields are its capability; the batch fields record what was actually used when its schedule was generated, and those values are already baked into its `Value` rows — do not rewrite them retroactively. A *new* batch copies them from its device once, at creation, because the form has no switches for them.
- **`Measurement` is fact, `Value` is plan** — do not merge them. `Value` says what day 15 *should* be (37.8 °, 55 %); `Measurement` says what the user read off the device at 22:18, and it hangs off that day's `Value` row via `idValue`. Both sides are `Double?` since v5, so the deviation is plain subtraction. The deviation shown in the batch sheet is the difference between the two, and the thresholds behind «в норме / небольшое / заметное отклонение» (±0.2 / ±0.5 ° and ±3 / ±7 %) are the app's own — the mockup only ever draws the «заметное» case. `Measurement.over` / `airing` are counts of turns and airings, not the automation flags of the same name on `Batch`. Today's date decides *which* day row is current, never which measurements belong to it.
- **`Batch.price` is the number the user typed, and `pricePerEgg` says which number it was** — per egg or for the whole clutch. Do not normalise one into the other on save: the egg count can still change, and «22 ₽ за яйцо» would then be lost. The two summary tiles under the field are derived, never stored, and the per-egg one uses integer division — the app has no kopecks anywhere else.
- Room DB `incubator_database`, **version 5**, `exportSchema = true` (schemas land in `data/schemas/`). `fallbackToDestructiveMigration()` was **removed on purpose** — with it, every version bump silently wiped users' projects. Migrations are hand-written in `InventoryDatabase`; add a new one for every schema change rather than bumping the version alone.
- **`MIGRATION_4_5` turns `temp` and `damp` into `REAL`** in both `Batch_value` (the plan) and `Batch_measurement` (the fact). They could never really be text: the app subtracts one from the other to show a deviation, and «60» minus «55» was being parsed out of a string on every recomposition. SQLite cannot change a column's type in place, so both tables are recreated and copied — **and the order is load-bearing**: `Batch_measurement` references `Batch_value`, so the parent cannot be dropped while a child points at it. Hence three steps: measurements move to a scratch table **without** the foreign key, the day rows are rebuilt (ids copied verbatim, which is what keeps the measurement links valid), then measurements come back with a key onto the new table. Conversion is `CASE WHEN REPLACE(TRIM(col), ',', '.') GLOB '*[0-9]*' THEN CAST(… AS REAL) ELSE NULL END` — commas become dots (they are easy to type on a numeric keypad and the quail regimen shipped one), text with no digit at all becomes `NULL` rather than `0.0`, and a range like «37-38» collapses to 37, which is a real loss that a `REAL` column cannot avoid.
- **`MIGRATION_3_4` adds `Batch_measurement`** — the actual thermometer/hygrometer readings the batch sheet records. A separate table rather than columns on `Batch_value`: a day of incubation can hold any number of readings, while `Batch_value` holds exactly one row per day and that row is the *plan*. **Its foreign key points at `Batch_value.id`, not at `Batch` plus a date** — the start date of a batch gets corrected and the phone's clock gets changed, and a reading keyed by calendar date would then sit on the wrong day of incubation. Editing a batch never recreates its `Batch_value` rows (`AddBatchViewModel.save()` only updates the batch and rewrites reminders), so those ids live as long as the batch does. The cascade reaches measurements in two steps: `Batch` → `Batch_value` → `Batch_measurement`. Nothing to back-fill — measurements did not exist before v4.
- **`MIGRATION_2_3` is an ordinary column add** — `Batch.Breed / Price / PricePerEgg` and `Batch_time.note`, the three things the batch form's mockup asks for. `PricePerEgg` back-fills to `1`, not `0`: existing batches have no price at all, and when one is finally typed in, «за яйцо» is the mode the mockup defaults to.
- **`MIGRATION_1_2` is a table restructure, not a column add**, and its ordering is load-bearing: the name `Incubator` is reused for the device, so the old table is first renamed to `Batch_tmp`, then the device table is created and one row synthesized, then `Batch` is created with its FK and filled, then the child tables, then the old ones dropped. The synthesized incubator gets `Capacity = 0` (v1 had no such data — the card asks the user to fill it, editable via the gear icon on the batches screen) while its auto-turn / auto-airing flags are derived with `MAX(CASE WHEN Overturn = 'true' …)` over the existing batches.
- **Child tables are recreated and copied, never renamed.** `minSdk = 26` means SQLite 3.18, where `ALTER TABLE … RENAME TO` does *not* rewrite `REFERENCES` in other tables — that only arrived in 3.25 (API 29). Any future table rename must use the same create-copy-drop dance.
- Entities sit apart from the domain models, so editing a `*Entity` class *is* editing the schema. Two checks, in order of strength: Room stamps an identity hash into the generated `data/build/generated/ksp/debug/kotlin/.../InventoryDatabase_Impl.kt` (`RoomOpenDelegate(2, "<hash>", ...)`) and writes it into `room_master_table` on the device, so pulling the DB and comparing the two proves the migration produced exactly the expected schema; if a refactor is meant to be schema-neutral, that hash must not move; and the generated `CREATE TABLE` there is the authority your migration DDL must match character for character.
- `Migration` overrides `migrate(db: SupportSQLiteDatabase)`. That is the right overload here: `getDatabase` builds Room without a custom `SQLiteDriver`, and the base `migrate(SQLiteConnection)` delegates to the SupportSQLite one in exactly that case.
- Adding a `NOT NULL` column via `ALTER TABLE … DEFAULT …` is safe even though the entity declares no `defaultValue`: Room only compares defaults when the entity itself defines one (`TableInfo.Column.equalsCommon`).

Navigation: each screen file declares its own `object …Destination : NavigationDestination` with `route`, `titleRes`, `itemIdArg`, and `routeWithArgs`; all of them are wired in `ui/navigation/InventoryNavGraph.kt`, which also fires the AppMetrica screen events. Route args are declared `NavType.IntType` while the ViewModels read them as `Long` — keep that pairing when adding routes.

State: list screens expose a `StateFlow` UI state via `repository.…().map { … }.stateIn(WhileSubscribed(5_000))`; single-entity editors hold a `mutableStateOf(…UiState)` with `private set` plus an `updateUiState()` setter, seeded in `init { }` from `getX().filterNotNull().first()`. Mapping extensions (`toIncubatorUiState()` / `toIncubatorTable()`, `toValueUiState()` / `toValue()`) live next to the ViewModel that owns the state.

## Domain conventions that bite

- **Booleans and dates are stored as strings, but temperature and humidity are not.** `airing`, `over`, `arhive` are `String` columns holding `"true"/"false"` or `"0"/"1"`, converted at the UiState boundary. Dates are `dd.MM.yyyy` text; `getAllIncubator()` sorts by re-slicing that text into ISO order inside SQL, and the current incubation day is computed in the UI as a `TimeUnit.DAYS` diff between parsed `Date`s. `temp` and `damp`, by contrast, are `Double?` since schema v5 — `null` means «не задано». `Value.over` / `Value.airing` stay text on purpose: they hold «2-3», «Авто», «2 раза по 5 минут», which are not numbers.
- **Input rules for the numeric fields live in `ValueFormat.kt` too, and the caller picks which one applies.** `filterMeasureInput()` — temperature and humidity — allows at most **two digits before the separator and two after**, normalising a comma to a dot and refusing a separator as the first character: «37.55» goes in whole, «333.44» becomes «33.44», «33.333» becomes «33.33». Two integer digits is what these quantities actually use — incubation temperature lives between 36 and 39, working humidity between 30 and 90 — and **the cost is that «100» cannot be typed into the humidity field**; raising `MAX_INTEGER_DIGITS` to 3 is the one-line change if that ever matters. `filterCountInput()` — turns and airings — deliberately does *not* follow that rule: those are whole counts, so it takes digits only and stops at a separator, turning a pasted «2.5» into «2» rather than «25». `NumberField` does not filter at all: it serves both kinds. `ValueFormatTest` in `app/src/test` pins every case, including the two the rule was written for.
- **Display follows the same two-decimal budget.** `formatTemp()` keeps one decimal and shows a second only when it carries information («37.0», «37.5», «37.55») — the regimen is written with one, and a bare «37» in a column of «37.5» reads as a different quantity. `formatDamp()` drops the fraction entirely when it is zero («60», not «60.0»). Both go through `Locale.US` so the separator is a dot, matching the mockup and the `:domain` schedule. The deviation line under each tile still rounds to one decimal for temperature and none for humidity — it summarises, it is not a reading.
- **Species knowledge lives in `:domain`, package `domain.incubation`** — that is the first place to look when adding a bird:
  - `IncubationSchedule.kt` — `setIncubator(type)`, a ~1600-line `when` emitting one `Value` per incubation day. This is the default regimen (temperature, humidity, turns, airing) and the bulk of the module.
  - `IncubationPeriod.kt` — `isIncubationFinished(type, day)`, the per-species incubation length that triggers the "project finished" dialog.
  - `Candling.kt` — `setOvoskop(type, day)`, which days offer candling (овоскопирование).
  - `ScheduleTransforms.kt` — `setAutoIncubator()` (overwrites every day's `airing`/`over` with `"Авто"` when the project flag is set) and `setIdPT` / `setIdPTTime` (stamp the parent project id onto generated rows before saving).
  - **Quail days 15–17 were corrected from 37.5 % to 65 % humidity.** The original file had them at `"37,5"` — the only humidity in the whole regimen written with a comma, and the shape of a temperature pasted into the wrong column: days 11–14 sit at 55 %, and the hatching phase needs *more* humidity, not a third less. Old builds and any archived batch created before this change still carry 37.5, since a batch keeps the `Value` rows it was generated with.
- Two per-species spots still sit in `:app` because they resolve drawables through `R`: `setOvoskopImage()` and the candling reference text in `IncubatorOvoscopScreen.kt`, plus `typeBirdsList` in `AddIncubator.kt` (the picker). Adding a bird means touching those three as well as the four domain files — the `when` blocks fall through to `else` silently, so a missed spot is a runtime behaviour bug, not a compile error.
- `isIncubationFinished` is the one function that was rewritten rather than moved: the old `endInc` took a Compose `MutableState` and opened the dialog itself. `IncubationPeriodTest` in `:domain` pins the new version against a verbatim copy of the old logic across every species and day, so changing a threshold there fails the test.
- Archive: setting `arhive = "1"` hides a project from the main list; a *new* project of the same species can then be seeded from an archived project's saved `Value` rows (`ArhivIncubatorChoice` → `getValueArchive`), which is why `AddIncubator` swaps between the generated `list` and the archived `list2`.

## Notifications

`WorkManagerRepository.scheduleReminder(times, projectName)` enqueues one `PeriodicWorkRequest` per reminder time, keyed uniquely by the time string (`"08:00"`) and tagged with the **project title**; `cancelAllNotifications(title)` cancels by that tag. Each `Time` also carries a `note` — the reminder's own text, typed in the batch form; blank means the notification falls back to `NOTIFICATION_TITLE` from `work/Constants.kt`. Consequences: renaming a batch orphans its old reminders, and two batches sharing a time string share the unique work name. Edits always cancel-then-reschedule — see `AddBatchViewModel.rewriteReminders`, which cancels by the **old** title (the one WorkManager tagged them with) before writing the new rows, and cancels by the new one too when the title changed. WorkManager's default initializer is stripped in the manifest via the `InitializationProvider` `tools:node="remove"` block, so on-demand init through `Configuration.Provider` is required — note that `Configuration.Provider` exposes a `val workManagerConfiguration` property, not the old `getWorkManagerConfiguration()` method.

## Third-party SDKs

AppMetrica is initialized in `InventoryApplication.onCreate`, with its API key as an inline literal.

**Ads were removed and are meant to come back.** Yandex Mobile Ads 8.x used to supply a sticky banner in the root `Scaffold`'s `bottomBar` (`ui/Banner.kt`) and an app-open ad in `MainActivity` re-shown on every process foreground (`DefaultProcessLifecycleObserver`); both files are gone, along with `YandexAds.initialize`, the `libs.mobileads` and `libs.androidx.lifecycle.process` dependencies, and the ad unit ids `R-M-12856457-1` / `-2`. The `mobileads` entry stays in `gradle/libs.versions.toml` so restoring it is one line in `app/build.gradle.kts`. Every screen composable still takes a `contentPadding: PaddingValues` and applies it — it now carries only system-bar insets, and it is what a returning banner would need again, so keep it. `AppMetrica.reportEvent(...)` calls with Russian event names are sprinkled through navigation and save paths — keep the existing names when moving code so analytics stay comparable.

First launch is detected by the `app_prefs` / `is_first_launch` SharedPreference in `MainActivity`; it both requests notification permissions and makes the nav graph start at the add-project screen with onboarding dialogs.

## Theme and the start screen

`ui/theme/` holds a large hand-written Material 3 color scheme (`Color.kt`) with light/dark/`dynamicColor` variants in `Theme.kt`, and downloadable Google Fonts via `ui-text-google-fonts` + `res/values/font_certs.xml`.

The **light** scheme and the typography were rebuilt from the Figma mockup
([node 5:682](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=5-682), file `B48q96fOq7Nsy569AXrbWY`):

- Fonts are Fraunces (display/titles), Inter (body), JetBrains Mono (`monoFontFamily` — eyebrow and figures). The mockup sets Fraunces' `SOFT`/`WONK` variation axes; downloadable fonts cannot carry those, so the rendered face is plain SemiBold.
- `DesignType` (in `Type.kt`) and `DesignPalette` (in `Color.kt`) hold the values that have no Material slot — chip fills per species, the progress track, the emphasised hatch date. Use those instead of inlining hex in composables. The Figma file defines no variables, so every value was read off the raw hex in the mockup.
- **The dark scheme is untouched and now disagrees with the light one.** There is no dark mockup yet; dark mode still renders with the old green palette.
- `isAppearanceLightStatusBars` is `!darkTheme`. It used to be `darkTheme`, which painted white status-bar icons over the (now cream) light background — invisible. `statusBarColor` is set for pre-35 devices only; API 35+ ignores it under forced edge-to-edge.

**Three screens are `ModalBottomSheet`s, not navigation destinations** — their mockups
([node 9:2738](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=9-2738) for the incubator form, [node 12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555) for the batch form, [node 14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893) for the batch itself) show a rounded top, a drag handle and a close cross, so they must appear *over* whatever screen is behind them. Each is therefore hosted by the screen that opens it: `AddIncubatorSheet` by `StartScreen` for creating (`incubatorId = 0`, also auto-opened on first launch) and by `IncubatorScreen` for editing; `AddBatchSheet` and `BatchDetailSheet` by `IncubatorScreen`. Because they have no nav entry, the id arrives through `viewModel.load(id)` from a `LaunchedEffect`, not `SavedStateHandle`, and the state resets on every open. The parts they share — drag handle, header row, `FieldLabel`, `SheetTextField`, `SheetPickerField`, the 20/16/48 dp constants — live in `ui/components/SheetForm.kt`; put anything a second sheet would need there rather than copying it.

**`SheetTextField` is built from `BasicTextField` + `OutlinedTextFieldDefaults.DecorationBox`, and must stay that way.** The ready-made `OutlinedTextField` pins itself to `defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight)` — 56 dp — which `heightIn` cannot reduce, so every field stood 8 dp taller than the 48 dp `SheetPickerField` and the «за яйцо / за всё» switch beside it. The decoration box takes `contentPadding` with zero vertical padding, so `heightIn(min = FieldHeight)` alone sets the height and the field still grows instead of clipping when the system font is enlarged. Anything placed next to a field in a `Row` should use `FieldHeight`, not a literal.

**Brand and model suggest previously used values.** `SuggestingSheetTextField` wraps the ordinary sheet field in an `ExposedDropdownMenuBox` fed by `getUsedBrands()` / `getUsedModels()` (`SELECT DISTINCT … WHERE … != ''`). Typing stays free — a new brand is always allowed — and the dropdown chevron appears **only when the list is non-empty**, so it never promises a menu that has nothing in it. Placeholders in these half-width fields are capped at one line with an ellipsis: with the chevron taking space, "Rcom, Блиц…" otherwise wraps and makes the field taller than its neighbour.

**Only the name is required.** The mockup marks `Название *` alone and states outright that the rest is optional, so `isValid` checks the name only — this deliberately overrides the earlier spec where capacity was mandatory. A device saved without capacity shows "укажите вместимость" on its card until someone fills it in via the gear icon.

`ui/start/StartScreen.kt` follows the mockup: eyebrow + large title (archive and info icons sit in that row — the mockup has neither, so their placement is an invention), two stat tiles, then cards. Card geometry worth preserving: the progress bar clamps at 100 % while the percentage label shows the true ratio — the mockup's first card is 108 of 72 places, i.e. 150 % with a fully-filled bar.

Cards degrade for pre-migration rows, and must keep doing so: `model` falls back to the species when blank, and `capacity == 0` hides the percentage and the bar. Every project that existed before v2 is in exactly that state until its owner edits it.

## The incubator screen

`ui/incubator/IncubatorScreen.kt` follows mockup [node 5:547](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=5-547): a green (`DesignPalette.HeaderSurface`) header with three figures, then a cream sheet carrying three tabs — **Закладки** (`IncubatorScreen.kt`), **Статистика** (`IncubatorStatsTab.kt`), **Финансы** (`IncubatorFinanceTab.kt`). The tabs are a `HorizontalPager`, so swiping between them is part of the design, not a bonus; the pill switcher only calls `animateScrollToPage`.

- **The sheet's `offset(y = -17.dp)` is load-bearing.** The mockup tucks the tab block 17 dp into the green header, and the 24 dp top corner radius is what lets the green show through at the edges. Drop the offset and the notches fill with cream, killing the effect. The 24 dp of background the offset leaves at the very bottom is the same cream, so the seam is invisible.
- The header is **fixed**; each pager page scrolls on its own. Making the header scroll away would need nested-scroll plumbing across three pages and was not worth it.
- **The screen no longer nests its own `Scaffold`.** The root `Scaffold` in `InventoryApp` already supplies system-bar insets through `contentPadding`; adding another one double-counted the status bar. The status bar staying cream above the green matches the mockup, which also draws it on the cream body.
- **The archive toggle is gone.** Finished batches now sit in the same list with a «Завершено» chip, exactly as the mockup's duck card does, so there is nothing to toggle. Editing the device moved to the pencil in the header row — the mockup has no such control, so its placement is an invention, same as the icons on `StartScreen`.
- **The third header figure is «Выведено», not «Прибыль».** The mockup asks for profit, but the app stores no money at all, and a header permanently reading `0 ₽` reads as breakage. Swap it back when finances land.
- `IncubatorViewModel` derives every figure from existing rows — no new columns. Note that **средний вывод counts only archived batches** (`sum(eggAllEND) / sum(eggAll)`): active ones still have `eggAllEND = 0` and would drag the percentage down. «Всего яиц» and the species chart, by contrast, span *all* batches, which is why the mockup's 132 exceeds its 108 «в работе».
- Chart bar colours (`DesignPalette.ChartBars`) are picked **by position** in the descending-sorted list, not by species — that is how the mockup assigns them.
- **`FinanceTab` is deliberately a stub.** The layout is the mockup's, but the sums are zero, the operation list is an empty state and the add button is inert, because there is no operation entity and no table for one. Making it real means a new `@Entity`, a fresh migration, DAO and repository methods, and a sheet to add an operation.
- Icons exported from the mockup live in `res/drawable/ic_*_design*.xml` as stroke-only vector drawables; `Icon(painter, tint = …)` recolours them, so the hex baked into `android:strokeColor` is only a fallback.

## The batch form

`ui/batch/AddBatchSheet.kt` follows mockup [node 12:3555](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=12-3555): name, a 3-across grid of species tiles, breed, count + date side by side, price with a за яйцо / за всё switch and two derived tiles, reminder cards, note, «Заложить яйца».

- **One form does both create and edit** — `batchId == 0` creates, anything else edits — because the file has no separate mockup for editing and the fields are identical. `StartScreen`-style hosting applies: `IncubatorScreen` opens it to create, `BatchScreen`'s gear opens it to edit. The old `BatchEditScreen` / `BatchEditViewModel` and the `BatchEdit` route are gone; `BatchUiState` and its mappers moved to `BatchUiState.kt`. `BatchScreen` must call `viewModel.reload()` from `onSaved`: its `itemUiState` is a plain `mutableStateOf` the screen itself mutates when incubation finishes, so nothing re-reads the batch on its own.
- **Editing rewrites the reminders wholesale** rather than diffing them, and archived batches never reach this sheet — `IncubatorScreen` routes a finished batch to `BatchArchiveScreen`, which keeps its own unarchive and delete.
- **«Завершить закладку» and «Удалить закладку» sit under the save button and exist only in edit mode.** The mockup draws neither, but the screen that used to hold them is gone. Delete asks first: `ON DELETE CASCADE` takes the day rows, reminders and species chips with it.
- **Creating a batch is one step.** The button saves immediately: `AddBatchViewModel.save()` inserts the batch, generates its `Value` rows from `setIncubator(type)`, writes the reminders and the species chip, then the nav graph opens the new batch. `AddBatchScheduleScreen.kt` — the old second step, a per-day table — is still in the tree but nothing reaches it; the same table is editable inside the batch anyway.
- **The species grid shows five tiles, not the mockup's six.** «Цесарки» would need a full regimen in `IncubationSchedule` plus entries in `IncubationPeriod`, `Candling` and `setOvoskopImage`, and none of that is written. The day counts under each tile come from `incubationDays()`, so a new bird appears there for free once the domain knows it. Emoji come from `speciesEmoji()` in `StartScreen.kt` — one list, shared with the cards.
- **The form has no auto-turn / auto-airing switches, exactly as in the mockup.** `load()` copies `autoAiring` / `autoTurn` off the parent `Incubator` instead; those are the device's capability, and the batch just records what it ran with. The old «другие виды в закладке» chips are gone too — `Batch_species` still exists and still gets the leading species.
- **`ArchiveScheduleDialog` is off-mockup and deliberate.** It only appears when finished batches of the same species exist, and it preserves the one thing the two-step form could do that the one-step one otherwise could not: seeding the day-by-day regimen from an archived batch (`getValueArchive`). Dismissing it saves with the default schedule.
- The dashed «Добавить напоминание» button is a local copy, not `IncubatorScreen`'s `DashedButton`: this mockup draws it at 41 dp with a 16 dp radius and 13 sp text, the other at 58 / 22 / 16.

## The batch sheet

`ui/batch/BatchDetailSheet.kt` follows mockup [node 14:4893](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=14-4893): a summary card, a 2×2 grid of info tiles, «Замеры за сегодня» (two stat tiles, deviation bars, a four-field entry form and the day's log) and «Завтра — День N» with four mode pills. **Tapping an active batch on `IncubatorScreen` opens this sheet** — it replaced `BatchScreen` as the tap target; finished batches still route to `BatchArchiveScreen`, and creating a batch now opens the same sheet instead of navigating.

- **`BatchScreen` is still reachable, through the «Расписание по дням» row.** The mockup draws no such control, but the day list is the only place with candling and «Завершить закладку», and losing it would be a regression. The AppMetrica events moved with the screens: opening the sheet reports `"Переход в закладку"` (it is the same user action as before), the schedule link reports the new `"Переход в расписание"`.
- **The sheet writes `Measurement` rows, never `Value` rows**, attached to today's `Value` row — so the day survives a corrected start date or a changed phone clock, and the record button is disabled if the batch somehow has no row for the current day. The four inputs (t°C, влаж.%, перев., провет.) plus a note save immediately on «Записать замер»; «Изм.» loads a row back into the form and keeps its original time, «×» deletes it. The button is disabled until at least one reading is filled — an empty measurement is not worth a row.
- **The big tile shows the latest reading of the day**, and the deviation bars only appear once there is one, exactly as in the mockup's two variants. Bar scales are the mockup's: ±1.5 ° and ±10 %; the marker clamps at the ends.
- **The bottom button says «Готово» and closes the sheet.** The mockup draws a permanently disabled «Сохранено», which reads as breakage — everything here is already saved by the time the button is visible.
- **«Аналитика» opens `BatchAnalyticsSheet`** — a sheet over this one, hosted the same way the delete dialog is (a sibling of the `ModalBottomSheet` call, so it lands in its own window above). With no measurements it stays at 40 % opacity and is not clickable: there would be nothing to count. It reports the AppMetrica event `"Переход в аналитику"`.
- **«Осталось сейчас» equals «Заложено яиц».** Nothing in the app records eggs culled at candling, so there is no second number to show; the tile flips to «Выведено» for a finished batch.
- **Mode pills compact their value.** `Value.airing` reads «2 раза по 5 минут», which does not fit a quarter-width pill, so `compactMode()` keeps short values («Авто», «2-3») verbatim and otherwise takes the leading number — the figure the mockup asks for.

## The day analytics sheet

`ui/batch/BatchAnalyticsSheet.kt` follows mockup [node 16:6632](https://www.figma.com/design/B48q96fOq7Nsy569AXrbWY/Untitled?node-id=16-6632): a chart of the day's readings, a 3×3 grid of figures and a verdict card. It takes the measurements and today's `Value` as parameters rather than loading them — `BatchDetailViewModel` already has both, and a second ViewModel would read the same rows twice.

- **All the arithmetic lives in `BatchAnalytics.kt`, which imports no Compose**, so `BatchAnalyticsTest` in `app/src/test` runs it on the JVM. Adding a figure to the grid means adding a field to `DayAnalytics`, not a calculation inside a composable.
- **The temperature line is red and humidity is a second, blue line** — the mockup draws one green line. Green already means accent and «в норме» everywhere else in the app, and the two series need to be told apart at a glance. They carry **separate axes**, temperature on the left and humidity on the right, because 37.5 ° and 58 % share no scale; when only humidity was recorded it takes the left gutter instead.
- **Events are drawn by shape, not by colour alone**: a turn is a solid vertical line across the plot, a note is the same line dashed, and an airing shades the time span it covered — it is the only one of the three with a duration (`Measurement.airing` is minutes, per the form's «ПРОВ., МИН» label). One measurement can emit all three at the same minute. The legend lists only the kinds that actually occurred.
- **`niceAxis` checks the step against the *rounded* bottom, not against the raw range.** Flooring the low end eats into the window: readings 37.1…37.9 span 0.8, which a 0.2 step would seem to cover, but the axis then runs 37.0…37.8 and the maximum gets drawn above the top gridline. `BatchAnalyticsTest` sweeps a grid of ranges against both step sets to pin this.
- **Lines between points are straight, though the mockup curves them.** A smoothed curve bulges past the highest reading, and «Максимум» is a tile directly under the chart.
- `turns` sums the `over` field (`"1"` from the toggle, a number from pre-toggle measurements), while `airings` counts measurements — the field there holds minutes, not a count.
- The verdict text comes back from `insightParts()` as plain/bold/mono segments rather than a finished `AnnotatedString`, so the wording stays testable and the composable picks the type.
- Chart colours are `DesignPalette.Chart*`; the tile figure is `DesignType.AnalyticsValue` (Fraunces 20/20). The «В цель ±0.3°» tile keeps the mockup's brown for any value — the mockup sets no «good / bad» thresholds for a hit rate, and inventing them here would be guesswork.
