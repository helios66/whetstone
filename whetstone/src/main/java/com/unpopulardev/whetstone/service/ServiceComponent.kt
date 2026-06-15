package com.unpopulardev.whetstone.service

import android.app.Service
import com.unpopulardev.whetstone.app.ApplicationScope
import com.unpopulardev.whetstone.injector.MembersInjectorMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides

/**
 * A Metro graph extension that has the lifetime of the [android.app.Service].
 */
@GraphExtension(ServiceScope::class)
public interface ServiceComponent {

    @Multibinds(allowEmpty = true)
    public val membersInjectorMap: MembersInjectorMap

    /**
     * Interface for creating a [ServiceComponent]. Contributed to [ApplicationScope] so the
     * application graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ApplicationScope::class)
    public interface Factory {
        public fun create(@Provides service: Service): ServiceComponent
    }
}
