package com.deliveryhero.whetstone.activity

import android.app.Activity
import androidx.fragment.app.FragmentFactory
import com.deliveryhero.whetstone.app.ApplicationScope
import com.deliveryhero.whetstone.injector.MembersInjectorMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides

/**
 * A Metro graph extension that has the lifetime of the [android.app.Activity].
 */
@GraphExtension(ActivityScope::class)
public interface ActivityComponent {
    public val fragmentFactory: FragmentFactory

    @Multibinds(allowEmpty = true)
    public val membersInjectorMap: MembersInjectorMap

    /**
     * Interface for creating an [ActivityComponent]. Contributed to [ApplicationScope] so the
     * application graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ApplicationScope::class)
    public interface Factory {
        public fun create(@Provides activity: Activity): ActivityComponent
    }
}
