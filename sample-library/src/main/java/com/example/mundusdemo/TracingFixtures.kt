package com.example.mundusdemo

import com.unpopulardev.mundus.runtime.Mundus
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single home for the Mundus tracing-coverage fixtures.
 *
 * Keeps the ~dozen fixture invocations (and the four fixture instances) out of [TodoViewModel],
 * which should only own todo/stats logic. The fixture methods exist purely to be traced; the app's
 * stats score must NOT be polluted with their return values. To stop R8 from dead-code-eliminating
 * the un-traced negative-case calls (whose results would otherwise be unused), [exerciseAll] folds a
 * checksum into a Mundus span — an opaque side-effect R8 cannot drop.
 *
 * Whetstone-injected: the four fixture collaborators arrive via the DI graph (each is an
 * `@Inject constructor` class), so this fixture set is itself a real consumer of the graph rather
 * than a hand-rolled `new`. [TodoViewModel] receives this via its own injected constructor.
 */
public class TracingFixtures @Inject constructor(
    private val autoTraced: AutoTracedDemo,
    private val partlyTraced: PartlyTracedDemo,
    private val conflictDemo: ConflictDemo,
    private val probe: TracingProbe,
) {

    /**
     * Exercise every annotation/probe fixture for its tracing side-effects.
     *
     * @param ids the current todo ids, passed to the fixtures as workload.
     * @param noTraceChecksum a value from an in-includePackages @NoTrace method (e.g.
     *   MainDependency.silentHelper) threaded through so that call isn't DCE'd either.
     */
    public suspend fun exerciseAll(ids: List<Int>, noTraceChecksum: Int) {
        var acc = noTraceChecksum
        // class-level @AutoTrace (non-coroutine, coroutine) + @NoTrace opt-out
        acc += autoTraced.weigh(ids)
        acc += autoTraced.weighAsync(ids)
        autoTraced.untraced(ids)
        // function-level @AutoTrace (tracedOne traces, plainTwo does not)
        acc += partlyTraced.tracedOne(ids)
        acc += partlyTraced.plainTwo(ids)
        // precedence: @NoTrace class wins over an @AutoTrace method
        acc += conflictDemo.contested(ids)
        acc += conflictDemo.alsoSilent(ids)
        // Part B probe: inline (warns) / higher-order(lambda) / Flow / parallel-async / throwing fn
        acc += probe.inlined { ids.size }
        acc += probe.higherOrder { var x = 0; for (i in 0..ids.size) x += i; x }
        acc += probe.flowConsumer()
        acc += probe.parallel()
        try { acc += probe.throwingTraced() } catch (e: IllegalStateException) { acc += 1 }
        // Keep the un-traced negative-case calls alive past R8 without polluting the app's score.
        val token = Mundus.beginTokenWith("TracingFixtures.checksum") { put("acc", acc.toLong()) }
        Mundus.endToken(token)
    }

    /** Launch the slow cancellable fixture and cancel it mid-flight (span-closure-on-cancel test). */
    public fun startAndCancel(scope: CoroutineScope) {
        val job = scope.launch { probe.cancellable() }
        scope.launch {
            delay(60)
            job.cancel()
        }
    }
}
