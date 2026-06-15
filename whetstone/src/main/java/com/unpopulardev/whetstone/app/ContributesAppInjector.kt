package com.unpopulardev.whetstone.app

import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInjectorBinding

/**
 * Marker annotation signalling that the compiler should generate necessary members injector
 * bindings for the annotated application.
 *
 * For example:
 * Given this annotated application
 * ```
 * @ContributesAppInjector
 * class MyApplication : Application()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(ApplicationScope::class)
 * interface MyApplication_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyApplication::class)
 *     val MembersInjector<MyApplication>.bindMyApplicationInjector: MembersInjector<*>
 * }
 * ```
 *
 * This annotation can also be used to generate the root app graph. To turn on this feature,
 * simply set [generateAppComponent] to `true`. Doing so will generate the following:
 *
 * ```
 * @DependencyGraph(scope = ApplicationScope::class)
 * interface GeneratedApplicationComponent : ApplicationComponent {
 *    @DependencyGraph.Factory
 *    fun interface Factory {
 *      fun create(@Provides application: Application): GeneratedApplicationComponent
 *    }
 *    // Metro generates a companion object implementing Factory, so callers can use
 *    // GeneratedApplicationComponent.create(application).
 * }
 * ```
 *
 * This can be very handy for quickly bootstrapping the DI setup. It is ideal for cases where the
 * application graph can be built without any external instance dependency (other than the
 * `Application` itself, provided via `@Provides`).
 *
 * **Note**: Generating app component is disabled by default.
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInjectorBinding(ApplicationScope::class)
public annotation class ContributesAppInjector(val generateAppComponent: Boolean = false)
