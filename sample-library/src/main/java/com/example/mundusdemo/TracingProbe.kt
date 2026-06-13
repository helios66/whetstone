package com.example.mundusdemo

import com.unpopulardev.mundus.runtime.AutoTrace
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Probe fixture for Mundus "Part B" feature-list items — exercises diverse shapes so we can see,
 * via bytecode + the live trace, which the compiler now traces: inline functions, structured
 * concurrency (async children), Flow operators, lambda bodies, and exception metadata.
 */
@AutoTrace
public class TracingProbe {

    /** T1: inline fn (NOT traced as of 0.9.0 — inlined into the caller). */
    public inline fun inlined(block: () -> Int): Int = block() + 1

    /** T2#4: higher-order fn + the lambda body it runs. */
    public fun higherOrder(block: () -> Int): Int = block() * 2

    /** T2#3: a collected Flow with operators. */
    public suspend fun flowConsumer(): Int {
        var sum = 0
        flowOf(1, 2, 3).map { it * 2 }.collect { sum += it }
        return sum
    }

    /** T2#2: structured concurrency — two async children under a coroutineScope. */
    public suspend fun parallel(): Int = coroutineScope {
        val a = async { leaf(1) }
        val b = async { leaf(2) }
        a.await() + b.await()
    }

    private suspend fun leaf(n: Int): Int {
        delay(1)
        return n * 3
    }

    /** T3#6: a traced fn that throws — exception should propagate out and produce an error slice. */
    public fun throwingTraced(): Int = error("probe-boom")
}
