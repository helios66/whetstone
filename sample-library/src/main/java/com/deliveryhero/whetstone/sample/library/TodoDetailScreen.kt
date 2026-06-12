package com.deliveryhero.whetstone.sample.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Detail/edit screen for a single todo: text, note, category, priority, done. */
@Composable
public fun TodoDetailScreen(viewModel: TodoViewModel, todoId: Int) {
    val todo = viewModel.todoById(todoId)
    if (todo == null) {
        // Deleted out from under us — pop back.
        LaunchedEffect(todoId) { viewModel.navigateBack() }
        return
    }

    var text by remember(todo.id) { mutableStateOf(todo.text) }
    var note by remember(todo.id) { mutableStateOf(todo.note) }
    var categoryId by remember(todo.id) { mutableStateOf(todo.categoryId) }
    var priority by remember(todo.id) { mutableStateOf(todo.priority) }
    var done by remember(todo.id) { mutableStateOf(todo.done) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Text") },
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
        )

        FieldLabel("Category")
        val onCategory: (Int?) -> Unit = remember { { id -> if (id != null) categoryId = id } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.categories.forEach { category ->
                CategoryChip(
                    label = category.name,
                    value = category.id,
                    selected = categoryId == category.id,
                    onSelect = onCategory,
                )
            }
        }

        FieldLabel("Priority")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { p ->
                PriorityChip(priority = p, selected = priority == p, onSelect = { priority = p })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = { done = it })
            Text("Done")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                viewModel.editTodo(todo.id, text, note, categoryId, priority)
                if (done != todo.done) viewModel.toggle(todo.id)
                viewModel.navigateBack()
            }) { Text("Save") }
            OutlinedButton(onClick = {
                viewModel.delete(todo.id)
                viewModel.navigateBack()
            }) { Text("Delete") }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PriorityChip(priority: Priority, selected: Boolean, onSelect: () -> Unit) {
    val label = priority.name.lowercase().replaceFirstChar { it.uppercase() }
    if (selected) {
        Button(onClick = onSelect) { Text(label) }
    } else {
        OutlinedButton(onClick = onSelect) { Text(label) }
    }
}
