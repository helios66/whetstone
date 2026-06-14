package com.unpopulardev.mundus.runtime

/**
 * Local no-op stand-ins for the Mundus runtime API, compiled into `:sample-library` **only** when
 * `-Pmundus.present=false` (the Mundus compiler plugin + real `mundus-runtime` artifact are absent,
 * e.g. a public clone with no GitHub Packages access to `helios66/mundus`).
 *
 * They let the tracing-testbed sources compile and run unchanged — every call is a no-op. When
 * `mundus.present` is `true` (the default), the real `com.unpopulardev.mundus:mundus-runtime`
 * artifact provides these symbols and this source directory is **not** on the compile path.
 *
 * Keep the signatures in lockstep with the upstream runtime (verified against mundus-runtime: the
 * `Mundus` facade's `beginTokenWith`/`endToken` and `MundusMetadataScope`'s typed `put` overloads).
 */

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class AutoTrace

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class NoTrace

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
public annotation class TraceArg

/** Mirrors `com.unpopulardev.mundus.runtime.MundusMetadataScope` — typed `put` overloads, all no-ops. */
public class MundusMetadataScope {
    public fun put(key: String, value: String): Unit = Unit
    public fun put(key: String, value: Long): Unit = Unit
    public fun put(key: String, value: Double): Unit = Unit
    public fun put(key: String, value: Boolean): Unit = Unit
}

/** Mirrors the `com.unpopulardev.mundus.runtime.Mundus` facade surface the testbed uses. */
public object Mundus {
    /** No-op: runs the metadata builder against a throwaway scope (nothing is recorded), returns no token. */
    public fun beginTokenWith(label: String, block: MundusMetadataScope.() -> Unit): Any? {
        MundusMetadataScope().block()
        return null
    }

    public fun endToken(token: Any?): Unit = Unit
}
