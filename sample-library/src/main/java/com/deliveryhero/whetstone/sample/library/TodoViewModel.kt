package com.deliveryhero.whetstone.sample.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deliveryhero.whetstone.viewmodel.ContributesViewModel
import com.example.mundusdemo.TracingFixtures
import com.unpopulardev.mundus.runtime.Mundus
import com.unpopulardev.mundus.runtime.TraceArg
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory todo store with categories, notes, priority, a stats view and a
 * hand-rolled navigation back-stack. Whetstone-injected (exercises the DI graph)
 * and Compose-observable (mutations recompose the UI).
 */
@ContributesViewModel
public class TodoViewModel @Inject constructor(
    private val dependency: MainDependency,
    // Whetstone-injected (was a hand-rolled `TracingFixtures()`): routes the tracing-coverage
    // fixtures + their collaborators through the DI graph instead of constructing them directly.
    private val tracingFixtures: TracingFixtures,
) : ScopedViewModel() {

    public val categories: List<Category> = listOf(
        Category(id = 0, name = "Work"),
        Category(id = 1, name = "Home"),
        Category(id = 2, name = "Shopping"),
    )

    // Precomputed O(1) lookup — avoids a linear scan per row, per recomposition.
    private val categoryNames: Map<Int, String> = categories.associate { it.id to it.name }

    // Seed a couple of todos so the app has content to render/score the moment it opens — the UI is
    // now driven entirely by the e2e test (Maestro), not by any in-app scripted flow. Seeded text
    // goes through getMessage() so the @TraceArg path is exercised at startup.
    private val _todos = mutableStateListOf(
        Todo(id = 0, text = dependency.getMessage("Email the client"), categoryId = 0),
        Todo(id = 1, text = dependency.getMessage("Buy groceries"), categoryId = 2),
    )
    public val todos: List<Todo> get() = _todos

    /** Result of the last background stats computation. */
    public var statsScore: Int by mutableStateOf(0)
        private set

    public var statsLoading: Boolean by mutableStateOf(false)
        private set

    // --- navigation back-stack ---
    private val backStack = mutableStateListOf<TodoDestination>(TodoDestination.List)
    public val destination: TodoDestination get() = backStack.last()
    public val canNavigateBack: Boolean get() = backStack.size > 1

    public fun navigateTo(target: TodoDestination) {
        backStack.add(target)
    }

    public fun navigateBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    // --- list state ---
    public var filterCategoryId: Int? by mutableStateOf(null)
        private set

    public var draft: String by mutableStateOf("")
        private set

    public var draftCategoryId: Int by mutableStateOf(0)
        private set

    private var nextId = 2

    public val visibleTodos: List<Todo>
        get() = filterCategoryId?.let { id -> _todos.filter { it.categoryId == id } } ?: _todos

    public val activeCount: Int get() = visibleTodos.count { !it.done }
    public val completedCount: Int get() = visibleTodos.count { it.done }

    public fun categoryName(id: Int): String = categoryNames[id] ?: "?"

    public fun todoById(id: Int): Todo? = _todos.firstOrNull { it.id == id }

    // --- stats ---
    public fun countInCategory(categoryId: Int): Int = _todos.count { it.categoryId == categoryId }
    public fun doneInCategory(categoryId: Int): Int = _todos.count { it.categoryId == categoryId && it.done }
    public val totalCount: Int get() = _todos.size
    public val donePercent: Int
        get() = if (_todos.isEmpty()) 0 else (_todos.count { it.done } * 100) / _todos.size

    // --- mutations ---
    public fun onDraftChange(value: String) {
        draft = value
    }

    public fun onDraftCategoryChange(id: Int) {
        draftCategoryId = id
    }

    public fun selectFilter(id: Int?) {
        filterCategoryId = id
    }

    public fun add(text: String, categoryId: Int) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _todos.add(Todo(id = nextId++, text = dependency.getMessage(trimmed), categoryId = categoryId))
        draft = ""
    }

    public fun addDraft() {
        add(draft, draftCategoryId)
    }

    public fun toggle(id: Int) {
        update(id) { it.copy(done = !it.done) }
    }

    /** Full edit from the detail screen. */
    public fun editTodo(id: Int, text: String, note: String, categoryId: Int, priority: Priority) {
        update(id) {
            it.copy(text = text.trim(), note = note.trim(), categoryId = categoryId, priority = priority)
        }
    }

    public fun delete(id: Int) {
        _todos.removeAll { it.id == id }
    }

    public fun clearCompleted() {
        _todos.removeAll { it.done }
    }

    private inline fun update(id: Int, transform: (Todo) -> Todo) {
        val i = _todos.indexOfFirst { it.id == id }
        if (i >= 0) _todos[i] = transform(_todos[i])
    }

    // --- background coroutine work (suspend functions, off the main thread) ---

    /**
     * Kick off the stats computation on the base [ScopedViewModel.viewModelScope]
     * (IO + SupervisorJob, auto-cancelled in onCleared). The launch runs on IO; the
     * heavy loop hops to Dispatchers.Default via [computeStatsScore].
     */
    public fun refreshStats() {
        statsLoading = true
        viewModelScope.launch {
            val score = computeStatsScore()
            statsScore = score
            statsLoading = false
        }
        // Also exercise the cancellation path on every stats refresh (the StatsScreen triggers this),
        // so the e2e trace covers it without any in-app scripted flow.
        tracingFixtures.startAndCancel(viewModelScope)
    }

    /** Aggregate a weighted score across all todos on a background dispatcher. */
    private suspend fun computeStatsScore(): Int = withContext(Dispatchers.Default) {
        // 0.6.0 manual API: a hand-rolled span carrying key/value metadata.
        val token = Mundus.beginTokenWith("TodoViewModel.statsBatch") {
            put("todoCount", _todos.size.toLong())
            put("filter", filterCategoryId?.let { categoryName(it) } ?: "all")
        }
        try {
            var score = 0
            for (todo in _todos.toList()) {
                score += scoreFor(todo)
            }
            // Exercise the Mundus tracing-coverage fixtures (out-of-package @AutoTrace/@NoTrace,
            // precedence, the Part B probe). silentHelper tests @NoTrace on an in-includePackages
            // method; its result is threaded through so neither it nor the negative cases are DCE'd.
            tracingFixtures.exerciseAll(_todos.map { it.id }, dependency.silentHelper(_todos.size))
            score
        } finally {
            Mundus.endToken(token)
        }
    }

    /** Per-todo score; suspends to simulate IO so the slice spans a real await. */
    private suspend fun scoreFor(@TraceArg todo: Todo): Int {
        delay(3)
        val weight = when (todo.priority) {
            Priority.HIGH -> 5
            Priority.NORMAL -> 3
            Priority.LOW -> 1
        }
        return if (todo.done) 0 else weight * (todo.text.length % 7 + 1)
    }
}
