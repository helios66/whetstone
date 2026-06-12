package com.deliveryhero.whetstone.sample

import androidx.test.core.app.ApplicationProvider
import com.deliveryhero.whetstone.Whetstone
import com.deliveryhero.whetstone.app.ApplicationComponent
import com.deliveryhero.whetstone.worker.MultibindingWorkerFactory
import com.deliveryhero.whetstone.worker.WorkerFactoryProvider
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
}
