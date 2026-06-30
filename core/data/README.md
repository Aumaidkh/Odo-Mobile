# `:core:data`

> The **offline-first source of truth**. A local **SQLDelight** database holds the
> user's data; repository implementations map rows ↔ domain at the boundary and
> expose the `:core:domain` ports. The server is a *later* sync target — nothing
> here talks to the network.

- **Package:** `com.hopcape.odo.core.data`
- **Gradle:** `:core:data` · accessor `projects.core.data`
- **Targets:** Android + iOS (KMP, `commonMain` + per-platform driver). MVP ships Android.
- **Stack:** SQLDelight `2.0.2` · kotlinx-datetime · Kotlin coroutines (`Flow`) · Koin DI · Arrow `Either`

---

## 1. TL;DR — the 30-second mental model

```
 Feature / UseCase            :core:data (this module)                 SQLite (odo.db)
 ─────────────────            ────────────────────────                 ───────────────
 carRepository.add(car) ─────► CarRepositoryImpl
   Either<DomainError,Car>     │  Car ──CarMappers──► insert params
                               │        transaction { demote primary?  ┌───────────┐
                               │                       insertCar }  ───►│  cars     │
                               │                                        │  (PENDING)│
 carRepository                 │  selectPrimaryCar().asFlow()           └───────────┘
   .observePrimaryCar() ◄──────┤  .mapToOneOrNull → row.toDomain()
   Flow<Car?>                  │
                               │
 vehicleCatalog.makes() ──────► VehicleCatalogImpl ──► selectAllMakes  ┌───────────────┐
   List<String>                                                        │ vehicle_make/ │
                                                                       │ vehicle_model │
                                                                       │ (seeded)      │
                                                                       └───────────────┘
```

The rest of the app sees **only** the `:core:domain` ports (`CarRepository`,
`VehicleCatalog`). The generated row types, the `OdoDatabase`, the driver and
`sync_status` never leave this module.

---

## 2. Why this design

- **Offline-first (CLAUDE.md golden rule).** The local DB is authoritative: an insert
  is durable and queryable with no network, with a **client-generated UUID** already on
  the `Car`. The server reconciles later — `sync_status` is the hook for that engine.
- **Domain never sees a DTO.** Generated rows (`Cars`, …) are mapped to `Car` *inside*
  this module. The dependency points inward: `:core:data → :core:domain` only.
- **Rehydration without re-validation.** Rows were written by an already-valid `Car`, so
  reads go through `Car.reconstitute(...)` (domain) rather than the validating
  `Car.create(...)` — no `EitherNel` gymnastics on a `Flow<Car?>`, and corruption fails
  fast instead of silently degrading.
- **Dropdown data lives in the DB, not the UI.** Makes/models are seeded reference tables
  behind the `VehicleCatalog` port, so Presentation hardcodes nothing and the catalog can
  become server-synced later without touching callers.

---

## 3. File map (one line each)

| File | Layer | Responsibility |
| --- | --- | --- |
| `sqldelight/.../Car.sq` | schema | `cars` table (DB_SCHEMA §9.3, mapped to SQLite) + indexes + `insertCar` / `clearPrimaryForOwner` / `selectPrimaryCar` / `selectById`. **`sync_status` groundwork.** |
| `sqldelight/.../VehicleMake.sq` | schema | Seeded make reference table + `insertMake` / `selectAllMakes` / `countMakes`. |
| `sqldelight/.../VehicleModel.sq` | schema | Seeded model reference table (FK to make) + `insertModel` / `selectModelsByMakeName`. |
| `db/DriverFactory.kt` | platform port | `expect class DriverFactory { create(): SqlDriver }` + `createOdoDatabase(driver)`. |
| `db/DriverFactory.android.kt` | platform | `AndroidSqliteDriver` (needs a `Context`, supplied by `:app`). |
| `db/DriverFactory.ios.kt` | platform | `NativeSqliteDriver` (covers both iOS targets via `iosMain`). |
| `car/CarMappers.kt` | mapping | `Cars` row → `Car` (the row→domain boundary) + `SyncStatus` constants. |
| `car/CarRepositoryImpl.kt` | repo impl | `add` (offline insert, one-primary transaction) + `observePrimaryCar`. |
| `car/VehicleCatalogImpl.kt` | repo impl | `VehicleCatalog` over the seeded tables; years/fuel from domain types. |
| `car/VehicleSeedData.kt` | seed | Top Indian brands + models; idempotent `seedVehicleReferenceData(db)`. |
| `CoreDataModule.kt` | wiring | Koin `coreDataModule`: `OdoDatabase` (seeded) + the two ports. |

Generated API (do **not** edit) lands under `build/generated/.../db/`: `OdoDatabase`,
the `Cars`/`VehicleMake`/`VehicleModel` row types, and the `*Queries`.

---

## 4. Public API surface

### The rest of the app uses **only** these (from `:core:domain`)
- `CarRepository` — `add(car)` / `observePrimaryCar()`
- `VehicleCatalog` — `makes()` / `models(make)` / `years()` / `fuelTypes()`

### Only `:app` (the DI bootstrap) uses these
- `coreDataModule` — registered at `startKoin`
- `DriverFactory` — **must be provided per-platform** (Android: `DriverFactory(context)`)

> 🔒 **Boundary rule:** a `:feature:*` module must never import `OdoDatabase`, a generated
> row/`*Queries`, `DriverFactory`, or `SyncStatus`. Inject the domain port instead. If you
> reach for a row type outside this module, add/extend a mapper here.

---

## 5. Wiring (`:app`) — done once

The platform `DriverFactory` is the only thing this module can't build itself (Android needs
a `Context`), so `:app` provides it, then loads `coreDataModule`:

```kotlin
// androidMain
val androidDataModule = module {
    single { DriverFactory(androidContext()) }   // Context-bearing factory
}

// startup
startKoin {
    androidContext(this@OdoApplication)
    modules(androidDataModule, coreDataModule /* , … */)
}
```

`coreDataModule` builds the `OdoDatabase` from that factory and **seeds the dropdown tables
on first creation** (idempotent), then exposes `CarRepository` and `VehicleCatalog` as
singletons.

---

## 6. Recipes

### 6.1 Add a column to `cars`
1. Edit `Car.sq` (column + any index). 2. Update `insertCar` and `CarMappers.toDomain`.
3. If it's user data, give it a sensible default for existing rows / a migration (see §8).

### 6.2 Add a new table + repository
1. New `Xyz.sq` (table + named queries). 2. `XyzMappers.kt` for row↔domain.
3. `XyzRepositoryImpl` implementing the `:core:domain` port. 4. Register it in `coreDataModule`.

### 6.3 Extend the dropdown catalog
Add brands/models to `VEHICLE_SEED` in `VehicleSeedData.kt`. Seeding keys on deterministic
slugs (`INSERT OR IGNORE`), so appends are safe; the first run after install populates them.

---

## 7. Data conventions (enforced here)

- **Offline UUIDs:** the `CarId` is client-generated upstream; `add` persists it as-is.
- **`owner_id` is never fabricated.** It's stored from the `Car`'s `OwnerId`; real
  provisioning (auth) and the server trigger come in a later slice.
- **One primary car per owner:** a partial unique index *and* `add` demotes the existing
  primary inside the same transaction before inserting a new primary.
- **Registration number** is stored normalized (uppercase, no spaces) — the domain
  `RegistrationNumber` value object guarantees that before it reaches the row.
- **Timestamps** (`created_at`/`updated_at`) are client-stamped ISO-8601 UTC via
  `kotlinx.datetime.Clock` (injectable for tests); the server reconciles on sync.
- **Soft delete:** `deleted_at`; all reads filter `deleted_at IS NULL`.
- **No money columns here** — odometer is plain `INTEGER` kilometres, not paise. (When money
  lands elsewhere it is integer paise per DB_SCHEMA.)

---

## 8. Tech notes & gotchas

- **Postgres → SQLite types:** `uuid`→`TEXT`, `smallint`/`integer`→`INTEGER` (comes back as
  `Long`; mappers `.toInt()`), `boolean`→`INTEGER` 0/1, `timestamptz`→`TEXT` (ISO-8601),
  enums→`TEXT` (`FuelType.name` ↔ `FuelType.valueOf`).
- **No GIN index:** the server's trigram make/model index has no SQLite equivalent; a plain
  index suffices locally.
- **`expect`/`actual` class warning:** `DriverFactory` is an expect/actual *class* (Android
  needs a `Context`), so the build opts in via `-Xexpect-actual-classes` in `build.gradle.kts`.
- **`sync_status` is client-only** — not in the authoritative server schema and never mapped
  to the domain. It exists purely as groundwork for the future sync engine.
- **Migrations:** none yet (greenfield). When the schema changes post-release, add
  `.sqm` migration files and enable schema verification; until then `Schema.create` is fine.
- **`years()`** currently returns the full `ModelYear.RANGE` (1980–2100) descending — it
  includes future years. Cap to "current year + 1" when Presentation needs it (inject a clock).

---

## 9. Testing

| Where | What |
| --- | --- |
| `commonTest` · `CarMappersTest` | `Cars` row → `Car` mapping: every field, fuel parse, 0/1 boolean, null optionals. |
| `androidHostTest` · `CarRepositoryImplTest` | In-memory `JdbcSqliteDriver`: insert → `observePrimaryCar` read-back, normalized reg, `sync_status = PENDING`, one-primary demotion. |
| `androidHostTest` · `VehicleCatalogImplTest` | Seeded makes/models ordering, unknown-make empty, idempotent seeding, years/fuel from domain. |

Driver-backed tests live in `androidHostTest` (JVM host) because the in-memory
`JdbcSqliteDriver` is JVM-only; pure mapper tests stay in `commonTest`.

```bash
./gradlew :core:data:testAndroidHostTest          # commonTest + host tests
./gradlew :core:data:compileAndroidMain           # Android compile
./gradlew :core:data:compileKotlinIosSimulatorArm64   # iOS native compile (driver actual)
```

---

## 10. Decisions log (FAQ)

- **Why `Car.reconstitute()` instead of reusing `Car.create()` on reads?** Local rows are
  trusted (written by a valid `Car`, mirrored by DB CHECKs). Re-validating would force
  `EitherNel` handling on `observePrimaryCar()`'s `Flow<Car?>`; `reconstitute` keeps reads
  total and fails fast only on genuine corruption.
- **Why seed dropdowns into SQLDelight rather than an in-memory list?** Offline-first and one
  source of truth — the catalog can later sync from the server without changing the
  `VehicleCatalog` contract or any caller.
- **Why is `DriverFactory` not in `coreDataModule`?** It's the one platform-specific
  dependency (Android `Context`); `:app` owns platform wiring, this module owns the DB graph.
- **Why no money types here?** This module only persists cars; odometer is kilometres. Money
  (integer paise) arrives with the service-log/bill features.
