package com.unpopulardev.whetstone.sample.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Base ViewModel that supplies an app-owned [viewModelScope] backed by
 * `Dispatchers.IO + SupervisorJob()` — a failing child won't cancel its
 * siblings — replacing the lifecycle library's Main-dispatched default.
 *
 * The scope is cancelled automatically in [onCleared], so subclasses get the
 * same lifecycle auto-cancel without managing a scope themselves.
 */
public abstract class ScopedViewModel : ViewModel() {

    protected val viewModelScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }
}
