# Whetstone → Metro migration plan

Migrate Whetstone's DI engine from **Square Anvil + Google Dagger** to
**[Metro](https://github.com/zacsweers/metro)** (`dev.zacsweers.metro`, latest stable **1.2.0**),
a Kotlin-compiler-plugin DI framework that unifies Dagger/Anvil/kotlin-inject.

**Hard requirement: feature parity + unchanged public API.** Consumers keep writing
`@ContributesViewModel`, `@ContributesFragment`, `@ContributesActivityInjector`,
`@ContributesServiceInjector`, `@ContributesViewInjector`, `@ContributesAppInjector`,
`@ContributesInjector`, `@ContributesWorker`, `@SingleIn`, `@ForScope` and calling
`Whetstone.inject(this)` / `injectedViewModel()`. We swap only the engine underneath.

## Why Metro fits

| Whetstone concept (Anvil+Dagger) | Metro equivalent |
|---|---|
| `@SingleIn(scope)` (custom) | `dev.zacsweers.metro.SingleIn(scope)` — identical shape |
| `@ForScope(scope)` (custom) | `dev.zacsweers.metro.ForScope(scope)` — identical shape |
| `@MergeComponent` / `@Component` | `@DependencyGraph(scope)` + `@DependencyGraph.Factory` |
| `@ContributesSubcomponent` + `ParentComponent` | `@GraphExtension(scope)` + `@GraphExtension.Factory` `@ContributesTo(parentScope)` |
| `@ContributesTo` (Anvil) | `@ContributesTo` (Metro) |
| `@ContributesBinding` (Anvil) | `@ContributesBinding` (Metro) |
| `@Module`/`@Binds`/`@Provides`/`@IntoMap`/`@Multibinds` (Dagger) | drop `@Module`; Metro `@Binds`/`@Provides`/`@IntoMap`/`@Multibinds` |
| `@LazyClassKey` + ProGuard keep machinery | Metro `@ClassKey` (no ProGuard machinery needed) |
| `MembersInjector<T>` (Dagger) | `dev.zacsweers.metro.MembersInjector<T>` |
| `Provider<T>` map values | `() -> T` lambda map values (Metro idiom) |
| Anvil `CodeGenerator` SPI (whetstone-compiler) | **KSP `SymbolProcessor`** — Metro merges KSP-generated `@ContributesTo` interfaces (they run before Metro's FIR plugin; `metro.hints` mechanism) |
| consumer `javax.inject.Inject` | works as-is (Metro JSR-330 interop) |

Metro has **no Anvil-style compiler SPI**, but **KSP processors that emit Metro annotations
work out of the box**. So whetstone-compiler is rewritten as a KSP processor that, per annotated
class, generates a Metro contributing interface.

## Generated-code shapes (the heart)

For `@AutoInjectorBinding(scope = S)` annotations (App/Activity/Service/View/Injector):
```kotlin
@ContributesTo(S::class)
public interface Foo_WhetstoneModule {
  @Binds @IntoMap @ClassKey(Foo::class)
  public val MembersInjector<Foo>.bindFooMembersInjector: MembersInjector<*>
}
```

For `@AutoInstanceBinding(base = B, scope = S)` annotations (ViewModel/Fragment/Worker):
```kotlin
@ContributesTo(S::class)
public interface Foo_WhetstoneModule {
  @Binds @IntoMap @ClassKey(Foo::class)
  public val Foo.bindFoo: B
}
```

For `@ContributesAppInjector(generateAppComponent = true)`:
```kotlin
@DependencyGraph(scope = ApplicationScope::class)
public interface GeneratedApplicationComponent : ApplicationComponent {
  @DependencyGraph.Factory
  public fun interface Factory : ApplicationComponent.Factory
  public companion object {
    public fun create(application: Application): ApplicationComponent =
      createGraphFactory<Factory>().create(application)
  }
}
```

Dropped vs. today: no `@LazyClassKey`, no `*_LazyMapKey` helper objects, no generated
`.pro` files, no gradle proguard-copy task. Metro `@ClassKey` references classes directly.

## Runtime structure

- Scope markers (`ApplicationScope` … `WorkerScope`): unchanged.
- `SingleIn`/`ForScope`: become `typealias` to Metro's (keeps `com.deliveryhero.whetstone.*` imports working).
- `ApplicationComponent`: stays an interface (impl'd by generated graph) exposing
  `viewModelFactory`, `membersInjectorMap`, and the extension factory accessors (auto-added by `@ContributesTo` factories).
- `ActivityComponent`/`FragmentComponent`/`ServiceComponent`/`ViewComponent`/`ViewModelComponent`/`WorkerComponent`:
  `@GraphExtension(scope)` with `@GraphExtension.Factory @ContributesTo(parentScope)`.
- Multibinding maps become `Map<KClass<*>, () -> T>` / `Map<KClass<*>, MembersInjector<*>>`;
  factories look up by `clazz.kotlin` and invoke the lambda.
- `MultibindingViewModelFactory`/`FragmentFactory`/`WorkerFactory`: `@ContributesBinding(scope) @Inject`, ported.
- `Whetstone` object + `ApplicationComponentOwner`: unchanged API; internal calls use Metro graph creation.

## Gradle plugin

- Apply `dev.zacsweers.metro` + `com.google.devtools.ksp`; add `ksp("whetstone-compiler")`.
- Remove anvil/kapt/dagger-compiler, proguard-copy task, `generateDaggerFactories`/`syncGeneratedSources` options.
- Keep `addOns { compose; workManager }`.

## Version bumps

- Add Metro 1.2.0; add KSP (matched to Kotlin). Possibly bump Kotlin to the version Metro 1.2.0 supports
  (verify empirically — current 2.2.0). Remove dagger/anvil/kapt/autoService(for Anvil) catalog entries.

## Stages (commit after each green+tested stage)

0. Baseline build green; Metro pattern spike (MembersInjector multibinding, graph extension, instance multibinding).
1. Version catalog + Kotlin/KSP/Metro wiring.
2. Runtime annotations + scopes (`SingleIn`/`ForScope` typealias, meta annotations kept).
3. whetstone-compiler → KSP processor (generated shapes above) + processor unit tests.
4. whetstone runtime: graphs/extensions/modules/factories on Metro.
5. whetstone-compose on Metro.
6. whetstone-worker on Metro.
7. Gradle plugin rewrite.
8. Samples build green; fix wiring.
9. Emulator end-to-end parity run (App/Activity/Service/View/ViewModel/Fragment/Worker).
10. Restore/port test suites (codegen, proguard→n/a, configuration-cache, worker).
11. Docs (README, RELEASING) + binary-compat baseline.

## Risk register

- R1 `MembersInjector<*>` map multibinding in Metro — **spike first**.
- R2 nested graph extensions (Fragment⊂Activity⊂App, View⊂Activity) — spike.
- R3 instance multibinding via `() -> T` values + `@ClassKey` on `@Binds` property — spike.
- R4 Kotlin↔Metro↔KSP version alignment — resolve at stage 1.
- R5 member injection on custom base classes / minSdk 21 — Whetstone uses `MembersInjector` map (not MetroX `AppComponentFactory`), so no API-28 floor. Keep Whetstone's model.
