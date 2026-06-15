package com.unpopulardev.whetstone.sample.library

import androidx.compose.runtime.Immutable

/** A todo category (e.g. Work, Home, Shopping). */
@Immutable
public data class Category(
    val id: Int,
    val name: String,
)

/** Priority of a todo. */
public enum class Priority { LOW, NORMAL, HIGH }

/** A single todo item, belonging to a category, optionally carrying a note. */
@Immutable
public data class Todo(
    val id: Int,
    val text: String,
    val categoryId: Int,
    val done: Boolean = false,
    val note: String = "",
    val priority: Priority = Priority.NORMAL,
)
