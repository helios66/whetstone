package com.unpopulardev.whetstone.activity

import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInjectorBinding

/**
 * Marker annotation signalling that the compiler should generate necessary members injector
 * bindings for the annotated activity.
 *
 * For example:
 * Given this annotated activity
 * ```
 * @ContributesActivityInjector
 * class MyActivity : Activity()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(ActivityScope::class)
 * interface MyActivity_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyActivity::class)
 *     val MembersInjector<MyActivity>.bindMyActivityInjector: MembersInjector<*>
 * }
 * ```
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInjectorBinding(scope = ActivityScope::class)
public annotation class ContributesActivityInjector
