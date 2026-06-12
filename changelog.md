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
- Fast, isolated KSP processor unit tests (kctfork + KSP2) covering every generated contribution
  shape, the generated `@DependencyGraph`, and a regression test for the single-file-per-class
  aggregation.
- Robolectric runtime DI assertions in the sample (CI-runnable, no emulator).

### Dependencies
- AGP 8.13.0 → 8.13.2; `compileSdk` 35 → 36; `minSdk` 21 → 23.
- androidx pulled to the latest versions compatible with AGP 8.13.x (the next releases require
  AGP 9.1+): core 1.18.0, activity 1.13.0, lifecycle 2.10.0, work 2.11.2, compose 1.11.2,
  fragment 1.8.9, appcompat 1.7.1, constraintlayout 2.2.1, material 1.14.0, and the latest
  androidx.test. Metro 1.2.0 → 1.2.1. Kotlin stays 2.3.20; AGP stays on the 8.x line.

### Changed
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

### Removed
- Dagger `@LazyClassKey` ProGuard machinery — Metro `@ClassKey` references classes directly, so
  the generated `*_LazyMapKey` helpers, generated `.pro` files, and the Gradle plugin's
  proguard-copy task are gone.
- `whetstone { generateDaggerFactories; syncGeneratedSources }` Gradle options are deprecated
  no-ops (Metro handles all modules uniformly); they will be removed in a future release.
- Removed the Anvil, Dagger, KAPT and AutoService dependencies.
