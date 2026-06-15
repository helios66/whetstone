package com.unpopulardev.whetstone.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.unpopulardev.whetstone.SingleIn
import com.unpopulardev.whetstone.activity.ActivityScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * A [FragmentFactory] that delegates to the per-fragment provider lambdas contributed via
 * [ContributesFragment].
 */
@ContributesBinding(ActivityScope::class)
@SingleIn(ActivityScope::class)
@Inject
public class MultibindingFragmentFactory(
    private val fragmentComponentFactory: FragmentComponent.Factory
) : FragmentFactory() {

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        val fragmentComponent = fragmentComponentFactory.create()
        val fragmentClass = loadFragmentClass(classLoader, className)
        val fragmentProvider = fragmentComponent.fragmentMap[fragmentClass.kotlin]
        return try {
            fragmentProvider?.invoke() ?: super.instantiate(classLoader, className)
        } catch (throwable: Throwable) {
            throw if (fragmentProvider == null)
                IllegalStateException(
                    "${fragmentClass.name} could not be instantiated. Did you forget to contribute it? Ensure the " +
                            "fragment class is annotated with '${ContributesFragment::class.java.name}' " +
                            "and has an '@Inject constructor'",
                    throwable
                )
            else throwable
        }
    }
}
