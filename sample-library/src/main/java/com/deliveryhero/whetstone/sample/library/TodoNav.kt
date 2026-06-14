package com.deliveryhero.whetstone.sample.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hand-rolled navigation host: switches the visible screen based on the
 * ViewModel's back-stack. No nav library — the router `when` and every screen
 * composable live in this module.
 */
@Composable
public fun TodoRoot(viewModel: TodoViewModel) {
    // System back pops the hand-rolled nav stack (so the e2e test's `back` returns to the list
    // instead of exiting the activity).
    BackHandler(enabled = viewModel.canNavigateBack) { viewModel.navigateBack() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val destination = viewModel.destination
            TodoTopBar(
                title = destination.title,
                canNavigateBack = viewModel.canNavigateBack,
                showStatsAction = destination is TodoDestination.List,
                onBack = viewModel::navigateBack,
                onStats = { viewModel.navigateTo(TodoDestination.Stats) },
            )
            // Weighted content region so a screen's own weight()/fill works under the bar.
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (destination) {
                    is TodoDestination.List -> TodoListScreen(viewModel)
                    is TodoDestination.Detail -> TodoDetailScreen(viewModel, destination.todoId)
                    is TodoDestination.Stats -> StatsScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun TodoTopBar(
    title: String,
    canNavigateBack: Boolean,
    showStatsAction: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = if (canNavigateBack) {
            { IconButton(onClick = onBack) { Text("←") } }
        } else {
            null
        },
        actions = {
            if (showStatsAction) {
                TextButton(onClick = onStats) { Text("Stats") }
            }
        },
    )
}
