package com.unpopulardev.whetstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.unpopulardev.whetstone.SingleIn
import com.unpopulardev.whetstone.app.ApplicationScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(ApplicationScope::class)
@SingleIn(ApplicationScope::class)
@Inject
public class MultibindingViewModelFactory(
    private val viewModelComponentFactory: ViewModelComponent.Factory,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    public override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle = extras.createSavedStateHandle()
        val viewModelComponent = viewModelComponentFactory.create(handle)
        val viewModelMap = viewModelComponent.viewModelMap

        val viewModelProvider = viewModelMap.getOrElse(modelClass.kotlin) {
            error(
                "${modelClass.name} could not be instantiated. Did you forget to contribute it? Ensure the " +
                        "view model class is annotated with '${ContributesViewModel::class.java.name}' " +
                        "and has an '@Inject constructor'"
            )
        }
        return viewModelProvider.invoke() as T
    }
}
