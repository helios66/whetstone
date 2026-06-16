# Migrating a Dagger/Anvil codebase to Whetstone (Metro)

Whetstone's DI engine is **Metro**. Its public annotation + runtime API is unchanged, but a large
codebase moving off Dagger/Anvil hits a few Metro behaviours. Whetstone now absorbs the common ones;
two are intrinsic to Metro and stay on your side.

## Whetstone handles these for you

### `dagger.Lazy`, `dagger.MapKey`, `@Module`/`@Provides` → enable Dagger interop

Instead of configuring Metro directly in your root `build.gradle.kts`, flip one Whetstone flag in any
module that still has Dagger imports:

```kotlin
whetstone {
    addOns {
        daggerInterop.set(true)   // Metro now recognises Dagger annotations + runtime types
    }
}
```

This resolves `[Metro/MissingBinding]` errors for `dagger.Lazy` at constructor-injection sites and
lets `@Module`/`@Provides`/`dagger.MapKey` keep working — no need to rewrite `dagger.Lazy` to
`kotlin.Lazy` or swap `dagger.MapKey` for `dev.zacsweers.metro.MapKey` across hundreds of files. Turn
it off once the migration is finished.

### Custom map keys → `@MapKey`

Annotate custom map-key annotations with Whetstone's `@MapKey` (a typealias to Metro's):

```kotlin
import com.unpopulardev.whetstone.MapKey

@MapKey
annotation class DestinationKey(val value: KClass<out Destination>)
```

A module that only *defines* keys needs just `implementation("…:whetstone")` — not the Whetstone
Gradle plugin — because the runtime now exposes Metro transitively.

### Binding precedence → `replaces` (not `priority`)

Metro has **no `rank`/`priority`** on contributed bindings; it uses explicit replacement. Whetstone's
`@ContributesBinding` already supports it:

```kotlin
// before (Anvil/old):  @ContributesBinding(AppScope::class, priority = HIGH)
// after (Whetstone):    @ContributesBinding(AppScope::class, replaces = [RealService::class])
class FakeService @Inject constructor() : Service
```

## These stay on your side (intrinsic to Metro)

### Test modules / fakes must be interfaces

Metro binding containers are interfaces whose binding methods are **default interface functions**.
Convert object/class-based test modules accordingly:

```kotlin
// before
object TestNetworkModule { @Provides fun api(): Api = FakeApi() }

// after
interface TestNetworkModule {
    @Provides fun api(): Api = FakeApi()   // default interface function
}
```

Whetstone can't absorb this — it's how Metro aggregates bindings.

### `@AssistedFactory` parameter names must match the constructor

Metro matches assisted parameters by **name *and* type**. Align the factory method's parameter names
with the `@Assisted` constructor parameters:

```kotlin
class Presenter @Inject constructor(@Assisted private val id: String, …) {
    @AssistedFactory
    interface Factory { fun create(id: String): Presenter }   // param MUST be named `id`
}
```

A mismatch surfaces as a missing-binding/assisted error. This is consumer-code correctness; Whetstone
can't rename your parameters.
