package com.deliveryhero.whetstone

/**
 * Indicates that this provided type (via `@Provides`, `@Binds`, `@Inject`, etc) will only have a
 * single instance within the target scope, e.g. `@SingleIn(ApplicationScope::class)`.
 *
 * Note that the scope does not actually need to be a scope-annotated annotation class. It is
 * _solely_ a key.
 *
 * Backed by [Metro's][dev.zacsweers.metro.SingleIn] scope annotation.
 */
public typealias SingleIn = dev.zacsweers.metro.SingleIn
