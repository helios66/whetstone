package com.unpopulardev.whetstone

/**
 * Drop-in replacement for Dagger's `@Reusable`, so consumers can delete `dagger` imports.
 *
 * Metro has no opportunistic-caching scope: a binding is either scoped (`@SingleIn(scope)`, cached
 * for that scope's lifetime) or unscoped (a new instance per request). This annotation is a **no-op
 * marker** — Metro ignores it, so an `@Reusable`-annotated `@Inject` class is treated as unscoped.
 *
 * That is within Dagger's `@Reusable` contract, which guarantees nothing: it *may* cache, so "always
 * recreate" is a legal implementation. It does, however, give up the caching *optimization*. If a
 * type genuinely must be cached, scope it explicitly with `@SingleIn(SomeScope::class)` instead.
 *
 * Provided so the 1,000+ `@Reusable` call sites migrate by a pure import swap
 * (`dagger.Reusable` -> `com.unpopulardev.whetstone.Reusable`) with no behavioural surprise beyond
 * the lost optimization.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
public annotation class Reusable
