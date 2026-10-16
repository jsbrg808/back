# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Spring Boot 3.3.4 / Java 21 backend (`ordinal:back`) serving two unrelated feature areas under one app:

- **coin** (`ordinal.third.coin`) — a read-only coin-collection summary API backed by MariaDB.
- **pogo** (`ordinal.third.pogo`) — Pokémon GO data tooling: MyBatis-backed CRUD tables, Excel report generation (Apache POI), and a multi-step pipeline that scrapes/downloads external Pokédex and CalcyDex data into the DB.

No frontend lives in this repo; `CorsConfig` opens `/api/**` to any `localhost:*` origin, implying a separate client app during development.

## Commands

- Run the app: `mvn spring-boot:run` (or `back.bat`, which just sets UTF-8 codepage and runs the same).
- Build / package: `mvn clean package` (produces `target/back.jar`, lombok excluded from the fat jar per `pom.xml`).
- Run tests: `mvn test`.
- Run a single test: `mvn test -Dtest=ClassName#methodName`.

## Testing convention

- **Tests live under `src/test/java` mirroring the main package** and run with `mvn test`. Any time a source change adds or alters behavior, add or update a test covering that behavior in the same change — do not leave source edits untested.
- Keep unit tests **pure**: no live DB, no real HTTP. Mock mapper/service collaborators with Mockito (`@ExtendWith(MockitoExtension.class)`), and use `@TempDir` for file-based tests (parsers, `PogoDataFileStore`). Avoid full Spring context (`@SpringBootTest`) tests — don't add them unless a wiring bug demands it.
- Test targets so far: `PogoService` (als parsing + CRUD branching), `PokedexDataProcessor` (maxCp/evolution chains/code maps), `PokedexJsonParser`, `PokemonSpeciesCsvParser`, `CalcyDexMoveSetCalculator` (DPS/move-set selection), `PogoDataFileStore` (path validation/legacy copy). When touched, keep these covered.

The app listens on port 8090 (`application.yml`) and expects a MariaDB instance at `jdbc:mariadb://localhost:3306/third` (credentials `scott`/`tiger` — local dev defaults, not secrets to protect).

## Architecture

### Layering convention

Each feature package follows `controller` → `service` → `mapper` (MyBatis) → `vo`, with XML mapper files under `src/main/resources/mapper/<area>/<Sub>.xml` mirroring the Java mapper interfaces in `src/main/resources/mapper/**/*.xml` (configured via `mybatis.mapper-locations`). VOs are shared under `ordinal.third.pogo.vo` / `ordinal.third.coin.vo` rather than nested per sub-package, and are the MyBatis type-alias package root.

MyBatis queries are globally logged via `SqlLogInterceptor` (`ordinal.third.config`), which renders bound SQL with inlined parameter values to the `QUERY_LOG` logger — useful when debugging a mapper, no separate query-logging setup needed.

### `pogo` sub-areas

- **`pogo.table`** (`PogoService`, `Pogo5TableMapper`, `Pogo5deMapper`) — generic CRUD over three near-identical tables (`POGO_5F`/`POGO_5S`/`POGO_5T`, selected by the `{tbl:5[fst]}` path variable in `PogoTableController`). Encodes a legacy packed string format (`als`: `X<gender><lvl><name>[ <iv>]`) that gets parsed/unparsed in `PogoService.parsePogo5TableVO`.
- **`pogo.excel`** — generates two downloadable/previewable XLSX reports (`Pogo5pcExcelService`, `Pogo5yExcelService`) with heavy POI styling (`Pogo5pcStyles`, `Pogo5yStyles`, shared constants in `PogoExcelConstants`). `PogoExcelPreviewBuilder` renders the same data as a JSON preview (for an in-browser grid) before the user commits to a download. The 5y report supports single-cell edits via `POST /api/pogo/5y/cell` before download.
- **`pogo.pokedex`** and **`pogo.calcydex`** — a multi-step external-data pipeline orchestrated by `PokedexController` / `PokedexService`, driven by the `PokedexUpdateStep` enum (`STEP1`, `STEP1_5`, `STEP1_6`, `STEP2`, `STEP2_5`, `STEP3`, `STEP3_1`, `MOVE_SETS`). Steps are selectable per-request (`PokedexUpdateRequest`) and run sequentially, short-circuiting on first failure; most steps are `@Transactional`, and the whole service guards concurrent runs with a single `ReentrantLock` instance field (not safe to call multiple instances concurrently — this is deliberate serialization, not a bug). Steps download/parse source files (JSON, CSV), bulk-replace staging tables (`PokedexImportStore`), then transform/upsert into final tables (`pokedex`, `pokemon_species_csv`, `calcydex`). `STEP1_6` runs a raw SQL script from the classpath (`src/main/resources/sql/runtime/스텝1.6.sql`) via `ResourceDatabasePopulator` rather than a mapper — see `src/main/resources/sql/README.md` for the `runtime` vs `manual` SQL convention (runtime SQL's path is referenced directly in Java and must be kept in sync if moved/renamed; manual SQL is never auto-executed).
- **`pogo.data`** (`PogoDataProperties`, `PogoDataFileStore`) — resolves a configurable external data directory (`pogo.data.directory`, default `~/Documents/정진우/data`, overridable via `THIRD_DATA_DIR` env var) where downloaded source files (pokedex.json, species CSV, CalcyDex TSVs) are cached between runs. `PogoDataFileStore.prepare` enforces relative-path-only inputs and falls back to copying a legacy flat-named file if the namespaced path doesn't exist yet — a migration shim for files from before subdirectories were introduced.

### External HTTP clients

`PokedexSourceClient`, `CalcyDexSourceClient`, `CalcyDexPokemonClient` fetch live data from external sources (with dedicated `*HttpConnectionFactory` helpers) and compare against cached local copies to decide whether a re-download is needed, returning an `updated`/`LATEST` flag that steps surface back to the caller. When modifying these, preserve the "only re-download if changed" behavior rather than always fetching.

### Korean-language domain

VO fields, SQL comments, log/detail messages, and some file/directory names are Korean (this is a Korean-language PoGo/coin hobby tool). Keep detail/progress messages and comments in Korean to match the existing style when editing these areas.
