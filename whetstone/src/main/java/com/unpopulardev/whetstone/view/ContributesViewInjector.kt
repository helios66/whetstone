package com.unpopulardev.whetstone.view

import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInjectorBinding

/**
 * Marker annotation signalling that the compiler should generate necessary members injector
 * bindings for the annotated view.
 *
 * For example:
 * Given this annotated view
 * ```
 * @ContributesViewInjector
 * class MyView : View()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(ViewScope::class)
 * interface MyView_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyView::class)
 *     val MembersInjector<MyView>.bindMyViewInjector: MembersInjector<*>
 * }
 * ```
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInjectorBinding(scope = ViewScope::class)
public annotation class ContributesViewInjector
