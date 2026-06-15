package com.unpopulardev.whetstone.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.unpopulardev.whetstone.app.ApplicationScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * A Metro graph extension that has the lifetime of the [androidx.lifecycle.ViewModel].
 */
@GraphExtension(ViewModelScope::class)
public interface ViewModelComponent {

    @Multibinds(allowEmpty = true)
    public val viewModelMap: Map<KClass<*>, () -> ViewModel>

    /**
     * Interface for creating a [ViewModelComponent]. Contributed to [ApplicationScope] so the
     * application graph exposes it.
     */
    @GraphExtension.Factory
    @ContributesTo(ApplicationScope::class)
    public interface Factory {
        public fun create(@Provides savedStateHandle: SavedStateHandle): ViewModelComponent
    }
}
