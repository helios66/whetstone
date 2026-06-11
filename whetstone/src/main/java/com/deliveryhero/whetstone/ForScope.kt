package com.deliveryhero.whetstone

/**
 * Qualifies this provided type (via `@Provides`, `@Binds`, `@Inject`, etc) for a given scope to
 * distinguish it from instances of the same type in other scopes, e.g.
 * `@ForScope(ApplicationScope::class) context: Context`.
 *
 * Note that the scope does not actually need to be a scope-annotated annotation class. It is
 * _solely_ a key.
 *
 * Backed by [Metro's][dev.zacsweers.metro.ForScope] qualifier annotation.
 */
public typealias ForScope = dev.zacsweers.metro.ForScope
