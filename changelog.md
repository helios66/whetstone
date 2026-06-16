# Changelog

All notable changes documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/) and the project follows
[SemVer](https://semver.org/) from 1.0+.

## [Unreleased]

This is a **major** release (2.0.0) — the DI engine changed and some internal/previously-public
types were removed (e.g. each component's `ParentComponent`, the `*Module` interfaces). The
consumer-facing annotation and `Whetstone.*` runtime APIs are source-compatible.

**Minimum SDK raised: `minSdk` 21 → 23.** The modernized androidx dependencies dropped support
for API 21/22, so consumers must target API 23 (Android 6.0) or higher.

### Added
- **Opt-in Dagger interop** — `whetstone { addOns { daggerInterop.set(true) } }` flips Metro's
  Dagger annotation + runtime interop, so a codebase migrating off Dagger keeps `dagger.Lazy`,
  `dagger.MapKey`, `@Module`/`@Provides`, etc. working without rewriting injection sites. Off by
  default; turn it off once the migration is done. (Driven from `consumer` feedback — replaces a
  manual `configure<MetroPluginExtension> { interop { includeDagger() } }` in the root build.)
- **`@MapKey`** (`com.unpopulardev.whetstone.MapKey`) — a typealias to Metro's map-key meta-annotation
  so custom map keys (e.g. `@DestinationKey`) annotate with a Whetstone import instead of
  `dev.zacsweers.metro.*`. A module that only *defines* keys needs just the `whetstone` runtime
  dependency (now exposing Metro's runtime as `api`), not the Gradle plugin. The KSP processor
  recognises keys meta-annotated via this alias in `@ContributesMultibinding` map bindings.
- **`@BindsInstance`** (`com.unpopulardev.whetstone.injector.BindsInstance`) — a drop-in for
  `dagger.BindsInstance`. A typealias to Metro's `@Provides` (what Metro uses to bind graph-factory
  parameters), so `fun create(@BindsInstance app: Application)` keeps working. Pure import swap.
- **`@Reusable`** (`com.unpopulardev.whetstone.Reusable`) — a drop-in for `dagger.Reusable` so consumers
  can delete that import. Metro has no opportunistic-cache scope, so this is a no-op marker (Metro
  ignores it → unscoped), which is within Dagger's `@Reusable` contract. Use `@SingleIn(scope)` for
  guaranteed caching. Part of the Dagger-eviction import-mapping (see the migration spec).
- **Anvil-compatible contribution annotations** under `com.unpopulardev.whetstone.injector`:
  `@ContributesTo(scope, replaces)`, `@ContributesBinding(scope, boundType, replaces)`, and
  `@ContributesMultibinding(scope, boundType, replaces)`. The `whetstone-compiler` KSP processor
  translates each into a native Metro `@ContributesTo` module (`<Class>_WhetstoneContribution`), so
  they merge across modules. `@ContributesMultibinding` becomes a set binding, or a map binding when
  the class carries a map-key annotation (any annotation meta-annotated with Metro/Dagger `@MapKey`).
  Eases migration off Anvil — keep the call sites, swap the import. (Metro's annotation interop was
  evaluated and rejected: it emits no cross-module contribution hints.)
- **Dependency-graph visualization.** The Gradle plugin contributes a `whetstoneDepGraph` task
  that renders the merged DI graph (scopes + contributions) as a Mermaid diagram (`.md`/`.mmd`/
  `.html`). Per-module fragments are emitted by the KSP processor and aggregated whole-app across
  modules. See "Visualizing the dependency graph" in the README.
- Fast, isolated KSP processor unit tests (kctfork + KSP2) covering every generated contribution
  shape, the generated `@DependencyGraph`, and a regression test for the single-file-per-class
  aggregation.
- Robolectric runtime DI assertions in the sample (CI-runnable, no emulator).
- The `sample`/`sample-library` modules double as an auto-tracing testbed (integrated with the
  Mundus compiler plugin) with an emulator-driven Perfetto-trace acceptance suite at
  `scripts/mundus-trace-scenarios.sh`. Dev-only — not part of the published artifacts. The Mundus
  integration is gated behind `-Pmundus.present` (default `true`): a clone without access to the
  private `helios66/mundus` GitHub Packages registry builds the sample with `-Pmundus.present=false`,
  which swaps in a no-op convention plugin (`com.unpopulardev.whetstone.mundus`, in build-logic) and
  local no-op runtime stubs — so the testbed never makes the sample un-buildable.

### Dependencies
- AGP 8.13.0 → 8.13.2; `compileSdk` 35 → 36; `minSdk` 21 → 23.
- androidx pulled to the latest versions compatible with AGP 8.13.x (the next releases require
  AGP 9.1+): core 1.18.0, activity 1.13.0, lifecycle 2.10.0, work 2.11.2, compose 1.11.2,
  fragment 1.8.9, appcompat 1.7.1, constraintlayout 2.2.1, material 1.14.0, and the latest
  androidx.test. Metro 1.2.0 → 1.2.1. Kotlin stays 2.3.20; AGP stays on the 8.x line.

### Changed
- **Namespace migrated to `com.unpopulardev.whetstone`** (group + every package + the
  `com.unpopulardev.whetstone` Gradle plugin id), from the previous `io.github.helios66` group /
  `com.deliveryhero.whetstone` packages. Clean break — no relocation POM at the old coordinates.
- **Distribution moved to Maven Central.** This fork now publishes to the Sonatype Central Portal
  under `com.unpopulardev.whetstone` (GPG-signed, `USER_MANAGED`) via the vanniktech maven-publish
  plugin. Consumers just need `mavenCentral()` — no credentials or custom repository. The previous
  private GitHub Packages distribution is dropped. See README + RELEASING.md.
- **Migrated the DI engine from Square Anvil + Google Dagger to [Metro](https://github.com/zacsweers/metro) 1.2.0.**
  The public annotation API (`@ContributesViewModel`, `@ContributesFragment`,
  `@ContributesActivityInjector`, `@ContributesServiceInjector`, `@ContributesViewInjector`,
  `@ContributesAppInjector`, `@ContributesInjector`, `@ContributesWorker`, `@SingleIn`,
  `@ForScope`) and runtime API (`Whetstone.inject(...)`, `injectedViewModel()`,
  `ApplicationComponentOwner`, `GeneratedApplicationComponent.create(...)`) are unchanged.
  Consumer code keeps using the JSR-330 `javax.inject.*` annotations — the Gradle plugin enables
  Metro's javax interop automatically. Multi-module usage is preserved.
  - `@SingleIn` / `@ForScope` are now `typealias`es to their Metro equivalents.
  - The runtime sub-components (Activity/Fragment/Service/View/ViewModel/Worker) are now Metro
    `@GraphExtension`s; the root component is a Metro `@DependencyGraph`.
  - The `whetstone-compiler` is now a KSP `SymbolProcessor` (was an Anvil `CodeGenerator`) that
    emits Metro `@ContributesTo` contributions.
- The Gradle plugin now applies `dev.zacsweers.metro` + `com.google.devtools.ksp` instead of Anvil.
- Bumped Kotlin 2.2.0 → 2.3.20 (required by Metro 1.2.0); added KSP 2.3.9. The
  `whetstone-gradle-plugin` module now targets Java 21 (Metro's Gradle plugin ships Java 21 bytecode).

### Fixed
- **Kotlin compiler toolchain raised 17 → 21.** Metro 1.2.1's compiler plugin ships Java 21 bytecode,
  so a Kotlin daemon on JDK 17 failed a clean compile with `UnsupportedClassVersionError`. The
  emitted bytecode target stays JVM 11. Enabled **core library desugaring** so Java 8+ APIs (e.g.
  `Iterable#forEach`) stay safe on `minSdk 23` when compiling on JDK 21.

### Removed
- Dagger `@LazyClassKey` ProGuard machinery — Metro `@ClassKey` references classes directly, so
  the generated `*_LazyMapKey` helpers, generated `.pro` files, and the Gradle plugin's
  proguard-copy task are gone.
- `whetstone { generateDaggerFactories; syncGeneratedSources }` Gradle options are deprecated
  no-ops (Metro handles all modules uniformly); they will be removed in a future release.
- Removed the Anvil, Dagger, KAPT and AutoService dependencies.
