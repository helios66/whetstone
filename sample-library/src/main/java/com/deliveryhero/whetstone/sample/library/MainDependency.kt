package com.deliveryhero.whetstone.sample.library

import com.unpopulardev.mundus.runtime.TraceArg
import javax.inject.Inject

public class MainDependency @Inject constructor() {

    // 0.6.0: @TraceArg captures the argument value as metadata on the getMessage trace slice.
    public fun getMessage(@TraceArg title: String): String = "$title message!"
}
