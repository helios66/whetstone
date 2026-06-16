package com.unpopulardev.whetstone.sample

import androidx.test.core.app.ApplicationProvider
import com.unpopulardev.whetstone.Whetstone
import com.unpopulardev.whetstone.app.ApplicationComponent
import com.unpopulardev.whetstone.sample.library.ConfigGraph
import com.unpopulardev.whetstone.sample.library.InjectorAnnotationsProbe
import com.unpopulardev.whetstone.sample.library.KotlinLazyGraph
import dev.zacsweers.metro.createGraphFactory
import com.unpopulardev.whetstone.worker.MultibindingWorkerFactory
import com.unpopulardev.whetstone.worker.WorkerFactoryProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runtime DI assertions, executed on the JVM via Robolectric so they run in CI without an emulator.
 * Robolectric instantiates [MainApplication] (driving `onCreate` -> `Whetstone.inject(this)`), which
 * builds the generated Metro graph.
 */
@RunWith(RobolectricTestRunner::class)
class WhetstoneRuntimeTest {

    private val app: MainApplication
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `application member injection populates the injected dependency`() {
        // MainApplication.onCreate ran Whetstone.inject(this); the @Inject field must be set.
        assertEquals("Application message!", app.dependency.getMessage("Application"))
    }

    @Test
    fun `root graph exposes the view-model factory and the members-injector multibinding`() {
        val component = Whetstone.fromApplication<ApplicationComponent>(app)
        assertNotNull(component.viewModelFactory)
        assertTrue(
            component.membersInjectorMap.containsKey(MainApplication::class),
            "membersInjectorMap should contain the @ContributesAppInjector application",
        )
    }

    @Test
    fun `worker graph-extension factory resolves the multibinding worker factory`() {
        val provider = Whetstone.fromApplication<WorkerFactoryProvider>(app)
        assertTrue(provider.workerFactory is MultibindingWorkerFactory)
    }

    @Test
    fun `injector contribution annotations resolve via metro interop`() {
        val probe = Whetstone.fromApplication<InjectorAnnotationsProbe>(app)
        // @ContributesBinding(boundType = Greeter::class)
        assertEquals("real-greeter", probe.greeter.greet())
        // @ContributesBinding(replaces = [DefaultApi::class]) — the fake must win
        assertEquals("fake", probe.api.name())
        // @ContributesMultibinding -> Set<Interceptor>
        assertEquals(setOf("auth", "logging"), probe.interceptors.map { it.id() }.toSet())
        // @ContributesMultibinding + custom @DestinationKey -> Map<String, Destination>
        assertEquals(setOf("home", "cart"), probe.destinations.keys)
        assertEquals("/home", probe.destinations["home"]?.route())
    }

    @Test
    fun `BindsInstance binds a factory-provided instance into a graph`() {
        // whetstone @BindsInstance (typealias to Metro @Provides) on a factory param must bind.
        val graph = createGraphFactory<ConfigGraph.Factory>().create("hello-config")
        assertEquals("hello-config", graph.configName)
    }

    @Test
    fun `kotlin Lazy resolves natively`() {
        // Metro provides kotlin.Lazy<T> directly — no dagger, no daggerInterop.
        val graph = createGraphFactory<KotlinLazyGraph.Factory>().create()
        assertEquals("real-greeter", graph.greeting())
    }

    @Test
    fun `custom map key via Whetstone MapKey typealias resolves into the map multibinding`() {
        // InjectorAnnotationsProbe.destinations is keyed by @DestinationKey, which is now annotated
        // with com.unpopulardev.whetstone.MapKey (typealias to Metro's) rather than Metro's directly.
        val probe = Whetstone.fromApplication<InjectorAnnotationsProbe>(app)
        assertEquals(setOf("home", "cart"), probe.destinations.keys)
    }
}
