package com.unpopulardev.whetstone.sample.library

import com.unpopulardev.whetstone.Reusable
import com.unpopulardev.whetstone.app.ApplicationScope
import com.unpopulardev.whetstone.injector.BindsInstance
import com.unpopulardev.whetstone.injector.ContributesBinding
import com.unpopulardev.whetstone.injector.ContributesMultibinding
import com.unpopulardev.whetstone.injector.ContributesTo
import com.unpopulardev.whetstone.MapKey
import dev.zacsweers.metro.DependencyGraph
import javax.inject.Inject

/**
 * Verification fixtures for the Anvil-compatible `injector.*` contribution annotations (added so a
 * consumer migrating off Anvil can keep its `com.unpopulardev.whetstone.injector.*` imports). They
 * exercise every shape the consumer relies on: [ContributesBinding.boundType], [ContributesBinding.replaces],
 * set multibindings, and custom-map-key map multibindings — all contributed to [ApplicationScope]
 * and probed at runtime via [InjectorAnnotationsProbe] (see WhetstoneRuntimeTest).
 */

// --- @ContributesBinding with an explicit boundType ---
public interface Greeter {
    public fun greet(): String
}

// @Reusable here verifies Metro tolerates the no-op marker alongside a real contribution.
@Reusable
@ContributesBinding(ApplicationScope::class, boundType = Greeter::class)
public class RealGreeter @Inject constructor() : Greeter {
    override fun greet(): String = "real-greeter"
}

// --- @ContributesBinding with replaces (fake replaces the default, as in test graphs) ---
public interface Api {
    public fun name(): String
}

@ContributesBinding(ApplicationScope::class, boundType = Api::class)
public class DefaultApi @Inject constructor() : Api {
    override fun name(): String = "default"
}

@ContributesBinding(ApplicationScope::class, boundType = Api::class, replaces = [DefaultApi::class])
public class FakeApi @Inject constructor() : Api {
    override fun name(): String = "fake"
}

// --- @ContributesMultibinding into a Set ---
public interface Interceptor {
    public fun id(): String
}

@ContributesMultibinding(ApplicationScope::class)
public class LoggingInterceptor @Inject constructor() : Interceptor {
    override fun id(): String = "logging"
}

@ContributesMultibinding(ApplicationScope::class)
public class AuthInterceptor @Inject constructor() : Interceptor {
    override fun id(): String = "auth"
}

// --- @ContributesMultibinding into a Map, via a CUSTOM map-key (mirrors the consumer's DestinationKey etc.) ---
@MapKey
@Retention(AnnotationRetention.RUNTIME)
public annotation class DestinationKey(val value: String)

public interface Destination {
    public fun route(): String
}

@ContributesMultibinding(ApplicationScope::class)
@DestinationKey("home")
public class HomeDestination @Inject constructor() : Destination {
    override fun route(): String = "/home"
}

@ContributesMultibinding(ApplicationScope::class)
@DestinationKey("cart")
public class CartDestination @Inject constructor() : Destination {
    override fun route(): String = "/cart"
}

// --- @ContributesTo accessor: merged into the ApplicationScope graph so the test can read everything back ---
@ContributesTo(ApplicationScope::class)
public interface InjectorAnnotationsProbe {
    public val greeter: Greeter
    public val api: Api
    public val interceptors: Set<Interceptor>
    public val destinations: Map<String, Destination>
}

// --- @BindsInstance verification: a standalone graph whose factory binds an instance ---
@DependencyGraph
public interface ConfigGraph {
    public val configName: String

    @DependencyGraph.Factory
    public interface Factory {
        // whetstone @BindsInstance == Metro @Provides — binds the param into the graph
        public fun create(@BindsInstance configName: String): ConfigGraph
    }
}

// --- Lazy injection, Metro-native (no dagger) ---
// Metro provides `kotlin.Lazy<T>` directly. A codebase migrating off Dagger turns a
// `dagger.Lazy<T>` site into a plain `kotlin.Lazy<T>` (access via `.value` instead of `.get()`).
// The Whetstone `daggerInterop` add-on lets `dagger.Lazy` keep working during that migration;
// this sample shows the finished, dagger-free form.
@DependencyGraph
public interface KotlinLazyGraph {
    public val lazyGreeter: Lazy<RealGreeter>

    public fun greeting(): String = lazyGreeter.value.greet()

    @DependencyGraph.Factory
    public interface Factory {
        public fun create(): KotlinLazyGraph
    }
}
