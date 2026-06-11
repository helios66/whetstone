package com.deliveryhero.whetstone.sample

import com.deliveryhero.whetstone.activity.ActivityScope
import com.deliveryhero.whetstone.injector.ContributesInjector
import com.deliveryhero.whetstone.sample.library.MainDependency
import javax.inject.Inject

/**
 * Exercises the generic `@ContributesInjector(scope)` path: Whetstone generates a
 * `MembersInjector<CustomInjectable>` binding into the [ActivityScope] injector map, which advanced
 * consumers can pull from `membersInjectorMap` to member-inject their own (non-Android) types.
 */
@ContributesInjector(ActivityScope::class)
class CustomInjectable {
    @Inject
    lateinit var dependency: MainDependency
}
