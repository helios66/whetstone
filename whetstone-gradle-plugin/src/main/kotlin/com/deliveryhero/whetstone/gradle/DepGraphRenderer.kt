package com.deliveryhero.whetstone.gradle

/**
 * Pure rendering logic for the Whetstone dependency graph: parse the JSON fragments emitted by
 * `WhetstoneSymbolProcessor` and render a Mermaid `flowchart`. Kept free of Gradle types so it is
 * unit-testable in isolation (see `DepGraphRendererTest`).
 */
internal object DepGraphRenderer {

    /** One contributed class: its fq name, target scope, kind, and (for instance bindings) base. */
    internal class Record(
        val fqName: String,
        val scope: String,
        val kind: String,
        val base: String?,
    )

    /** Parse one controlled, flat array-of-objects JSON fragment (no nesting / escapes). */
    fun parse(json: String): List<Record> =
        OBJECT_REGEX.findAll(json).mapNotNull { match ->
            val fields = FIELD_REGEX.findAll(match.value)
                .associate { it.groupValues[1] to it.groupValues[2] }
            val fqName = fields["fqName"] ?: return@mapNotNull null
            Record(
                fqName = fqName,
                scope = fields["scope"].orEmpty(),
                kind = fields["kind"].orEmpty(),
                base = fields["base"],
            )
        }.toList()

    /** De-dupe + sort records across fragments into a stable order for rendering. */
    fun normalize(records: List<Record>): List<Record> =
        records
            .distinctBy { "${it.fqName}@${it.scope}" }
            .sortedWith(compareBy({ scopeRank(it.scope) }, { it.fqName }))

    /** Render the normalized records as a Mermaid `flowchart TD` string. */
    fun render(records: List<Record>): String {
        val byScope = records.groupBy { it.scope }
        val presentScopes = (SCOPE_ORDER + byScope.keys).distinct().filter { byScope.containsKey(it) }

        val sb = StringBuilder("flowchart TD\n")
        for (scope in presentScopes) {
            sb.append("  subgraph ").append(scope).append("[\"").append(scope).append("\"]\n")
            for (record in byScope.getValue(scope)) {
                val label = simpleName(record.fqName) +
                    if (record.base != null) "<br/>(${record.base})" else ""
                sb.append("    ").append(nodeId(record.fqName))
                    .append("[\"").append(label).append("\"]\n")
            }
            sb.append("  end\n")
        }
        for (scope in presentScopes) {
            val parent = SCOPE_PARENT[scope]
            if (parent != null && parent in presentScopes) {
                sb.append("  ").append(parent).append(" --> ").append(scope).append('\n')
            }
        }
        if (records.isEmpty()) sb.append("  empty[\"No Whetstone contributions found\"]\n")
        return sb.toString()
    }

    private fun nodeId(fqName: String): String = "n_" + fqName.replace(NON_ID_REGEX, "_")

    private fun simpleName(fqName: String): String = fqName.substringAfterLast('.')

    private fun scopeRank(scope: String): Int =
        SCOPE_ORDER.indexOf(scope).let { if (it < 0) SCOPE_ORDER.size else it }

    private val OBJECT_REGEX = Regex("""\{[^}]*}""")
    private val FIELD_REGEX = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
    private val NON_ID_REGEX = Regex("[^A-Za-z0-9]")

    /** child scope -> parent scope (the fixed Whetstone component hierarchy). */
    private val SCOPE_PARENT = mapOf(
        "ActivityScope" to "ApplicationScope",
        "FragmentScope" to "ActivityScope",
        "ViewScope" to "ActivityScope",
        "ServiceScope" to "ApplicationScope",
        "ViewModelScope" to "ApplicationScope",
        "WorkerScope" to "ApplicationScope",
    )

    private val SCOPE_ORDER = listOf(
        "ApplicationScope", "ActivityScope", "FragmentScope", "ViewScope",
        "ServiceScope", "ViewModelScope", "WorkerScope",
    )
}
