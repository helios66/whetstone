package com.deliveryhero.whetstone.injector

/**
 * Drop-in for Dagger's `@BindsInstance`, so consumers can delete that import.
 *
 * Metro has no `@BindsInstance`: an instance handed to a graph factory is bound with `@Provides` on
 * the factory parameter. This is a typealias to Metro's `@Provides`, so
 * `fun create(@BindsInstance app: Application)` keeps compiling and binds `app` into the graph
 * exactly as Metro expects — a pure import swap with identical behaviour.
 */
public typealias BindsInstance = dev.zacsweers.metro.Provides
