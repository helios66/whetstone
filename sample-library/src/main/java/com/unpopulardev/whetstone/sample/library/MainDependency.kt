package com.unpopulardev.whetstone.sample.library

import com.unpopulardev.mundus.runtime.NoTrace
import com.unpopulardev.mundus.runtime.TraceArg
import javax.inject.Inject

public class MainDependency @Inject constructor() {

    // 0.6.0: @TraceArg captures the argument value as metadata on the getMessage trace slice.
    public fun getMessage(@TraceArg title: String): String = "$title message!"

    // Function-level @NoTrace on a class that IS in includePackages: this method must stay untraced
    // even though the package is included and getMessage() above is traced (opt-out at fn level).
    @NoTrace
    public fun silentHelper(n: Int): Int {
        var acc = 0
        for (i in 0..n) {
            acc += i
        }
        return acc
    }
}
