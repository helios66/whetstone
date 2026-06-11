package com.deliveryhero.whetstone.injector

import kotlin.reflect.KClass

/**
 * Marker annotation signalling that the compiler should generate the necessary `MembersInjector`
 * binding for the annotated class.
 *
 * For example:
 * Given this annotated class
 * ```
 * @ContributesInjector(ActivityScope::class)
 * class MyActivity : FragmentActivity() {
 *     @Inject lateinit var someEntity: SomeEntity
 * }
 * ```
 * a complementary contributing interface will be generated
 * ```
 * @ContributesTo(ActivityScope::class)
 * interface MyActivityInjectorModule {
 *     @Binds @IntoMap @ClassKey(MyActivity::class)
 *     val MembersInjector<MyActivity>.bindMyActivityInjector: MembersInjector<*>
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ContributesInjector(val scope: KClass<*>)
