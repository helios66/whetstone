package com.example.mundusdemo

import com.unpopulardev.mundus.runtime.AutoTrace
import com.unpopulardev.mundus.runtime.NoTrace
import kotlinx.coroutines.delay
// NOTE: precedence observed in Mundus 0.9.0 — class-level @NoTrace overrides a function-level
// @AutoTrace (the annotated method is NOT traced), and Mundus emits no warning about the override.

/**
 * Mundus annotation-coverage fixture.
 *
 * This class deliberately lives **outside** the sample's `includePackages`
 * (`com.deliveryhero.whetstone`). So the only thing that can cause its methods to be traced is
 * the [AutoTrace] annotation — which makes it a precise test of the annotation-driven opt-in path,
 * distinct from package-based inclusion. It exercises @AutoTrace on both a plain and a suspend
 * function, plus a [NoTrace] method that must stay untraced even inside an @AutoTrace class.
 */
@AutoTrace
public class AutoTracedDemo {

    /** @AutoTrace on a NON-coroutine function. */
    public fun weigh(values: List<Int>): Int {
        var acc = 0
        for (v in values) {
            acc += v * 2 + 1
        }
        return acc
    }

    /** @AutoTrace on a COROUTINE (suspend) function — suspends across a real await. */
    public suspend fun weighAsync(values: List<Int>): Int {
        delay(2)
        return weigh(values) + values.size
    }

    /** Opt-out control: @NoTrace must suppress tracing even though the class is @AutoTrace. */
    @NoTrace
    public fun untraced(values: List<Int>): Int = values.sum()
}

/**
 * Function-level @AutoTrace coverage. The CLASS is **not** annotated and lives outside
 * `includePackages`, so only the explicitly-annotated method must be traced — the un-annotated
 * sibling must stay untraced. This isolates per-function opt-in from class-level opt-in.
 */
public class PartlyTracedDemo {

    /** @AutoTrace on a single function of an otherwise-untraced class. */
    @AutoTrace
    public fun tracedOne(values: List<Int>): Int {
        var acc = 0
        for (v in values) {
            acc += v + 3
        }
        return acc
    }

    /** No annotation, class not annotated, package not included → must NOT be traced. */
    public fun plainTwo(values: List<Int>): Int = values.size * 2
}

/**
 * Precedence probe: the CLASS is @NoTrace but [contested] is @AutoTrace. This documents which
 * annotation wins when they conflict (function-level opt-in vs class-level opt-out).
 */
@NoTrace
public class ConflictDemo {

    /** @AutoTrace on a method of a @NoTrace class — who wins? */
    @AutoTrace
    public fun contested(values: List<Int>): Int {
        var acc = 0
        for (v in values) {
            acc += v * 5 + 2
        }
        return acc
    }

    /** No method annotation; class is @NoTrace → must NOT be traced. */
    public fun alsoSilent(values: List<Int>): Int = values.size + 1
}
