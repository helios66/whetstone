package com.deliveryhero.whetstone.sample.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** List screen: filter bar, add bar, and the todo list. Tapping a row opens its detail. */
@Composable
public fun TodoListScreen(viewModel: TodoViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TodoCounts(activeCount = viewModel.activeCount, completedCount = viewModel.completedCount)
        CategoryBar(
            categories = viewModel.categories,
            selectedId = viewModel.filterCategoryId,
            onSelect = viewModel::selectFilter,
        )
        TodoInputBar(
            draft = viewModel.draft,
            draftCategoryId = viewModel.draftCategoryId,
            categories = viewModel.categories,
            onDraftChange = viewModel::onDraftChange,
            onDraftCategoryChange = viewModel::onDraftCategoryChange,
            onAdd = viewModel::addDraft,
        )
        val todos = viewModel.visibleTodos
        if (todos.isEmpty()) {
            EmptyState()
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                todos.forEach { todo ->
                    TodoRow(
                        todo = todo,
                        categoryName = viewModel.categoryName(todo.categoryId),
                        onToggle = viewModel::toggle,
                        onOpen = { viewModel.navigateTo(TodoDestination.Detail(todo.id)) },
                    )
                }
            }
        }
        TodoFooter(completedCount = viewModel.completedCount)
    }
}

@Composable
private fun TodoCounts(activeCount: Int, completedCount: Int) {
    Text(
        text = "$activeCount active · $completedCount done",
        style = MaterialTheme.typography.caption,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun CategoryBar(categories: List<Category>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryChip(label = "All", value = null, selected = selectedId == null, onSelect = onSelect)
        categories.forEach { category ->
            CategoryChip(
                label = category.name,
                value = category.id,
                selected = selectedId == category.id,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * Skippable: all params (label/value/selected) are stable and [onSelect] is a
 * hoisted reference, so Compose skips this when the selection is unchanged.
 */
@Composable
internal fun CategoryChip(label: String, value: Int?, selected: Boolean, onSelect: (Int?) -> Unit) {
    if (selected) {
        Button(onClick = { onSelect(value) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onSelect(value) }) { Text(label) }
    }
}

@Composable
private fun TodoInputBar(
    draft: String,
    draftCategoryId: Int,
    categories: List<Category>,
    onDraftChange: (String) -> Unit,
    onDraftCategoryChange: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New todo") },
            )
            Button(onClick = onAdd) { Text("Add") }
        }
        // Stable adapter so the chips below stay skippable across recompositions.
        val onPick: (Int?) -> Unit = remember(onDraftCategoryChange) {
            { id -> if (id != null) onDraftCategoryChange(id) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                CategoryChip(
                    label = category.name,
                    value = category.id,
                    selected = draftCategoryId == category.id,
                    onSelect = onPick,
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    categoryName: String,
    onToggle: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle(todo.id) })
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = todo.text,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(
                    text = "$categoryName · ${todo.priority.name.lowercase()}",
                    style = MaterialTheme.typography.caption,
                )
                if (todo.note.isNotEmpty()) {
                    Text(text = "📝 ${todo.note}", style = MaterialTheme.typography.body2)
                }
            }
            TextButton(onClick = onOpen) { Text("Open") }
        }
    }
}

@Composable
private fun TodoFooter(completedCount: Int) {
    if (completedCount == 0) return
    Text(
        text = "$completedCount completed",
        style = MaterialTheme.typography.caption,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing to do 🎉")
    }
}
