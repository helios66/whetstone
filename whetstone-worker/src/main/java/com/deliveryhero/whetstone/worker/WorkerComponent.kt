package com.deliveryhero.whetstone.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.deliveryhero.whetstone.ForScope
import com.deliveryhero.whetstone.app.ApplicationScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * A Metro graph extension that has the lifetime of the [androidx.work.ListenableWorker].
 */
@GraphExtension(WorkerScope::class)
public interface WorkerComponent {

    @Multibinds(allowEmpty = true)
    public val workerMap: Map<KClass<*>, () -> ListenableWorker>

    /**
     * Interface for creating a [WorkerComponent]. Contributed to [ApplicationScope] so the
     * application graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ApplicationScope::class)
    public interface Factory {
        public fun create(
            @Provides @ForScope(WorkerScope::class) appContext: Context,
            @Provides parameters: WorkerParameters
        ): WorkerComponent
    }
}

/**
 * Exposes the [WorkerFactory] (bound by [MultibindingWorkerFactory]) on the application graph so
 * the WorkManager initializer can install it.
 */
@ContributesTo(ApplicationScope::class)
public interface WorkerFactoryProvider {
    public val workerFactory: WorkerFactory
}
