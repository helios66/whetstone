package com.unpopulardev.whetstone.service

import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInjectorBinding

/**
 * Marker annotation signalling that the compiler should generate necessary members injector
 * bindings for the annotated service.
 *
 * For example:
 * Given this annotated service
 * ```
 * @ContributesServiceInjector
 * class MyService : Service()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(ServiceScope::class)
 * interface MyService_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyService::class)
 *     val MembersInjector<MyService>.bindMyServiceInjector: MembersInjector<*>
 * }
 * ```
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInjectorBinding(scope = ServiceScope::class)
public annotation class ContributesServiceInjector
