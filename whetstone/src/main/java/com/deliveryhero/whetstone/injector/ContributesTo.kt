package com.deliveryhero.whetstone.injector

import kotlin.reflect.KClass

/**
 * Contributes the annotated interface (typically a module exposing `@Binds`/`@Provides`, or an
 * aggregating component interface) to the given [scope].
 *
 * Anvil-compatible facade over Metro: the Whetstone Gradle plugin registers this annotation with
 * Metro's annotation interop, so Metro merges the annotated interface into the [scope]'s graph
 * natively — including [replaces] for swapping production contributions with test/fake ones.
 *
 * ```
 * @ContributesTo(ApplicationScope::class)
 * interface NetworkModule {
 *     @Binds fun bindApi(impl: RealApi): Api
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ContributesTo(
    val scope: KClass<*>,
    val replaces: Array<KClass<*>> = [],
)
