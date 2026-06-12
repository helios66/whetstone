package com.deliveryhero.whetstone.sample.library

import androidx.compose.runtime.Immutable

/** Screens the sample can navigate between (hand-rolled nav, no nav library). */
@Immutable
public sealed interface TodoDestination {
    public val title: String

    public data object List : TodoDestination {
        override val title: String get() = "Todo"
    }

    public data class Detail(val todoId: Int) : TodoDestination {
        override val title: String get() = "Edit todo"
    }

    public data object Stats : TodoDestination {
        override val title: String get() = "Stats"
    }
}
