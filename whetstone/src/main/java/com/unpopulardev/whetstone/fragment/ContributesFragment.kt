package com.unpopulardev.whetstone.fragment

import androidx.fragment.app.Fragment
import com.unpopulardev.whetstone.InternalWhetstoneApi
import com.unpopulardev.whetstone.meta.AutoInstanceBinding

/**
 * Marker annotation signalling that the compiler should generate necessary instance
 * bindings for the annotated fragment.
 *
 * For example:
 * Given this annotated fragment
 * ```
 * @ContributesFragment
 * class MyFragment @Inject constructor() : Fragment()
 * ```
 * a complementary Metro contribution will be generated
 * ```
 * @ContributesTo(FragmentScope::class)
 * interface MyFragment_WhetstoneModule {
 *     @Binds
 *     @IntoMap
 *     @ClassKey(MyFragment::class)
 *     val MyFragment.bindMyFragment: Fragment
 * }
 * ```
 */
@OptIn(InternalWhetstoneApi::class)
@AutoInstanceBinding(base = Fragment::class, scope = FragmentScope::class)
public annotation class ContributesFragment
