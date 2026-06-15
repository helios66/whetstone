package com.unpopulardev.whetstone.injector

import kotlin.reflect.KClass

/**
 * Contributes the annotated class as a binding for [boundType] into the given [scope].
 *
 * Anvil-compatible facade over Metro (registered via the Whetstone Gradle plugin's Metro annotation
 * interop). [boundType] defaults to [Unit] meaning "infer from the single supertype"; specify it
 * when the class has more than one supertype. [replaces] swaps another binding (e.g. a fake in a
 * test graph).
 *
 * ```
 * @ContributesBinding(ApplicationScope::class, boundType = Api::class)
 * class RealApi @Inject constructor() : Api, Closeable
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ContributesBinding(
    val scope: KClass<*>,
    val boundType: KClass<*> = Unit::class,
    val replaces: Array<KClass<*>> = [],
)
