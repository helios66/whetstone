package com.unpopulardev.whetstone.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.unpopulardev.whetstone.injector.MembersInjectorMap
import dev.zacsweers.metro.Multibinds

/**
 * The root Metro dependency graph that has the lifetime of the [android.app.Application].
 *
 * The generated `GeneratedApplicationComponent` (or a hand-written `@DependencyGraph`) implements
 * this interface, so everything contributed to [ApplicationScope] is aggregated here.
 */
public interface ApplicationComponent {
    public val viewModelFactory: ViewModelProvider.Factory

    @Multibinds(allowEmpty = true)
    public val membersInjectorMap: MembersInjectorMap

    /**
     * Interface for creating an [ApplicationComponent].
     */
    public interface Factory {
        public fun create(application: Application): ApplicationComponent
    }
}
