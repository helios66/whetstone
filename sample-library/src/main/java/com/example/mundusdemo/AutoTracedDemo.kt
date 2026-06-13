package com.example.mundusdemo

import com.unpopulardev.mundus.runtime.AutoTrace
import com.unpopulardev.mundus.runtime.NoTrace
import kotlinx.coroutines.delay

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
