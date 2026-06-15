package com.unpopulardev.whetstone.sample.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stats screen: overall completion, a background-computed score, per-category breakdown. */
@Composable
public fun StatsScreen(viewModel: TodoViewModel) {
    // Triggers suspend-function work on the ViewModel's background dispatcher.
    LaunchedEffect(Unit) { viewModel.refreshStats() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverallCard(total = viewModel.totalCount, donePercent = viewModel.donePercent)
        ScoreCard(score = viewModel.statsScore, loading = viewModel.statsLoading)
        viewModel.categories.forEach { category ->
            CategoryStatRow(
                name = category.name,
                total = viewModel.countInCategory(category.id),
                done = viewModel.doneInCategory(category.id),
            )
        }
    }
}

@Composable
private fun OverallCard(total: Int, donePercent: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "$total todos · $donePercent% done", style = MaterialTheme.typography.h6)
            LinearProgressIndicator(progress = donePercent / 100f, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScoreCard(score: Int, loading: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Priority score", style = MaterialTheme.typography.subtitle2)
            Text(
                text = if (loading) "computing…" else "$score",
                style = MaterialTheme.typography.h6,
            )
        }
    }
}

@Composable
private fun CategoryStatRow(name: String, total: Int, done: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(text = name, modifier = Modifier.weight(1f))
        Text(text = "$done / $total")
    }
}
