package com.deliveryhero.whetstone.injector

import kotlin.reflect.KClass

/**
 * Contributes the annotated class as a multibinding element for [boundType] into the given [scope].
 *
 * Following the Anvil convention, this becomes a **map** multibinding when the class also carries a
 * map-key annotation (any annotation itself meta-annotated with a `MapKey`, e.g. `@StringKey`,
 * `@ClassKey`, or a custom key); otherwise it is a **set** multibinding. [boundType] defaults to
 * [Unit] meaning "infer from the single supertype".
 *
 * ```
 * // set element
 * @ContributesMultibinding(ApplicationScope::class)
 * class LoggingInterceptor @Inject constructor() : Interceptor
 *
 * // map element (custom map-key)
 * @ContributesMultibinding(ApplicationScope::class)
 * @DestinationKey(Home::class)
 * class HomeDestination @Inject constructor() : Destination
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ContributesMultibinding(
    val scope: KClass<*>,
    val boundType: KClass<*> = Unit::class,
    val replaces: Array<KClass<*>> = [],
)
