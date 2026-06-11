package com.deliveryhero.whetstone.fragment

import androidx.fragment.app.Fragment
import com.deliveryhero.whetstone.activity.ActivityScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import kotlin.reflect.KClass

/**
 * A Metro graph extension that has the lifetime of the [androidx.fragment.app.Fragment].
 */
@GraphExtension(FragmentScope::class)
public interface FragmentComponent {

    @Multibinds(allowEmpty = true)
    public val fragmentMap: Map<KClass<*>, () -> Fragment>

    /**
     * Interface for creating a [FragmentComponent]. Contributed to [ActivityScope] so the
     * activity graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ActivityScope::class)
    public interface Factory {
        public fun create(): FragmentComponent
    }
}
