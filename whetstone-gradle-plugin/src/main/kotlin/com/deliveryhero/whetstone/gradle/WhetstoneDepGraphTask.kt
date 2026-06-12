package com.deliveryhero.whetstone.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Renders a [Mermaid](https://mermaid.js.org/) diagram of the Whetstone DI graph from the per-module
 * JSON fragments emitted by `WhetstoneSymbolProcessor` during KSP. Each contributed class becomes a
 * node inside its scope subgraph, and the fixed Whetstone scope hierarchy is drawn as edges.
 *
 * Outputs `dep-graph.md` (```mermaid fenced — renders on GitHub) and `dep-graph.mmd` (raw, for mmdc).
 */
public abstract class WhetstoneDepGraphTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val graphFragments: ConfigurableFileCollection

    @get:OutputDirectory
    public abstract val reportDir: DirectoryProperty

    @TaskAction
    public fun render() {
        val records = graphFragments.files
            .filter { it.isFile && it.extension == "json" }
            .flatMap(::parse)
            .distinctBy { "${it.fqName}@${it.scope}" }
            .sortedWith(compareBy({ scopeRank(it.scope) }, { it.fqName }))

        val dir = reportDir.get().asFile.also { it.mkdirs() }
        val mermaid = renderMermaid(records)
        File(dir, "dep-graph.mmd").writeText(mermaid)
        File(dir, "dep-graph.md").writeText(
            "# Whetstone dependency graph\n\n" +
                "_Generated from ${records.size} contribution(s). Do not edit._\n\n" +
                "```mermaid\n$mermaid```\n"
        )
        logger.lifecycle(
            "Whetstone dep-graph: ${records.size} contribution(s) -> ${File(dir, "dep-graph.md")}"
        )
    }

    private class Record(val fqName: String, val scope: String, val kind: String, val base: String?)

    /** Minimal parser for our own controlled, flat array-of-objects JSON (no nesting/escapes). */
    private fun parse(file: File): List<Record> {
        val text = file.readText()
        return OBJECT_REGEX.findAll(text).mapNotNull { match ->
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
    }

    private fun renderMermaid(records: List<Record>): String {
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

    private companion object {
        val OBJECT_REGEX = Regex("""\{[^}]*}""")
        val FIELD_REGEX = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
        val NON_ID_REGEX = Regex("[^A-Za-z0-9]")

        /** child scope -> parent scope (the fixed Whetstone component hierarchy). */
        val SCOPE_PARENT = mapOf(
            "ActivityScope" to "ApplicationScope",
            "FragmentScope" to "ActivityScope",
            "ViewScope" to "ActivityScope",
            "ServiceScope" to "ApplicationScope",
            "ViewModelScope" to "ApplicationScope",
            "WorkerScope" to "ApplicationScope",
        )

        val SCOPE_ORDER = listOf(
            "ApplicationScope", "ActivityScope", "FragmentScope", "ViewScope",
            "ServiceScope", "ViewModelScope", "WorkerScope",
        )
    }
}
