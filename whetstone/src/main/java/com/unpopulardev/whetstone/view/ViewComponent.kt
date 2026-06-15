package com.unpopulardev.whetstone.view

import android.view.View
import com.unpopulardev.whetstone.activity.ActivityScope
import com.unpopulardev.whetstone.injector.MembersInjectorMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides

/**
 * A Metro graph extension that has the lifetime of the [android.view.View].
 */
@GraphExtension(ViewScope::class)
public interface ViewComponent {

    @Multibinds(allowEmpty = true)
    public val membersInjectorMap: MembersInjectorMap

    /**
     * Interface for creating a [ViewComponent]. Contributed to [ActivityScope] so the activity
     * graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ActivityScope::class)
    public interface Factory {
        public fun create(@Provides view: View): ViewComponent
    }
}
