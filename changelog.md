# Changelog

All notable changes documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/) and the project follows
[SemVer](https://semver.org/) from 1.0+.

## [Unreleased]

### Changed
- **Migrating the DI engine from Square Anvil + Google Dagger to [Metro](https://github.com/zacsweers/metro).**
  Public annotation API (`@ContributesViewModel`, `@ContributesFragment`,
  `@ContributesActivityInjector`, `@ContributesServiceInjector`, `@ContributesViewInjector`,
  `@ContributesAppInjector`, `@ContributesInjector`, `@ContributesWorker`, `@SingleIn`,
  `@ForScope`) and runtime API (`Whetstone.inject(...)`, `injectedViewModel()`) preserved.
  (in progress — see `todo.md` / `PLAN_METRO_MIGRATION.md`)

### Removed
- (pending) Dagger `@LazyClassKey` ProGuard machinery — Metro `@ClassKey` references classes
  directly, so the generated `*_LazyMapKey` helpers, generated `.pro` files, and the gradle
  plugin's proguard-copy task are no longer needed.
- (pending) `whetstone { generateDaggerFactories; syncGeneratedSources }` Gradle options —
  Metro handles all modules uniformly.
