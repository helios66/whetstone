package com.unpopulardev.whetstone

/**
 * Whetstone's stable alias for Metro's map-key meta-annotation.
 *
 * Annotate a custom map-key annotation with `@MapKey` so it can key `@IntoMap` bindings (and
 * Whetstone's `@ContributesMultibinding` map bindings) — without importing `dev.zacsweers.metro.*`
 * directly:
 *
 * ```
 * @MapKey
 * annotation class DestinationKey(val value: KClass<out Destination>)
 * ```
 *
 * Because it is a `typealias`, the compiler resolves `@MapKey` straight to Metro's annotation, so
 * Metro recognises the custom key natively. A module that only *defines* keys needs just the
 * `whetstone` runtime dependency — not the Whetstone Gradle plugin. (With `daggerInterop` enabled,
 * `dagger.MapKey` also works, easing migration.)
 */
public typealias MapKey = dev.zacsweers.metro.MapKey
