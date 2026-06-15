package com.unpopulardev.whetstone.sample.library

import com.google.gson.Gson
import javax.inject.Inject
import okio.ByteString.Companion.encodeUtf8

/**
 * A SECOND Whetstone-injected dependency (alongside [MainDependency]), added so the trace harness
 * can prove Mundus captures MULTIPLE distinct DI dependencies — not just one — under R8 full mode.
 *
 * Deliberately carries no Mundus annotations: it is traced purely because it lives in an
 * `includePackages` package (`com.unpopulardev.whetstone`). That makes it a clean demonstration that
 * a vanilla `@Inject` dependency with a non-trivial body gets a trace slice in a minified release
 * build, with nothing opt-in on the class itself.
 */
public class StatsAuditor @Inject constructor() {

    /**
     * Folds a label + score into a checksum. The loop body is what Mundus's heuristics latch onto,
     * so this method emits a `StatsAuditor.audit` slice. Called on the e2e stats path.
     */
    public fun audit(label: String, score: Int): Int {
        var checksum = score
        for (ch in label) {
            checksum = checksum * 31 + ch.code
        }
        // Call into two external libraries so Mundus's traceCalleePackages can wrap the call sites.
        // okio: encode the label to a ByteString (callee package `okio`).
        checksum = checksum xor label.encodeUtf8().size
        // gson: serialize a small payload (callee package `com.google.gson`).
        checksum = checksum xor Gson().toJson(intArrayOf(score, label.length)).length
        return checksum and 0xFFFF
    }
}
