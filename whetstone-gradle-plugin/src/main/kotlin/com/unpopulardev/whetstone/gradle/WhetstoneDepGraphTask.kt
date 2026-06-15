package com.unpopulardev.whetstone.gradle

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
 * The rendering itself lives in [DepGraphRenderer] (Gradle-free, unit-tested).
 */
public abstract class WhetstoneDepGraphTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val graphFragments: ConfigurableFileCollection

    @get:OutputDirectory
    public abstract val reportDir: DirectoryProperty

    @TaskAction
    public fun render() {
        val records = DepGraphRenderer.normalize(
            graphFragments.files
                .filter { it.isFile && it.extension == "json" }
                .flatMap { DepGraphRenderer.parse(it.readText()) }
        )
        val mermaid = DepGraphRenderer.render(records)

        val dir = reportDir.get().asFile.also { it.mkdirs() }
        File(dir, "dep-graph.mmd").writeText(mermaid)
        File(dir, "dep-graph.md").writeText(
            "# Whetstone dependency graph\n\n" +
                "_Generated from ${records.size} contribution(s). Do not edit._\n\n" +
                "```mermaid\n$mermaid```\n"
        )
        // Self-contained HTML that renders the graph to SVG (Mermaid CDN) — open or serve to view.
        File(dir, "dep-graph.html").writeText(DepGraphRenderer.renderHtml(mermaid))
        logger.lifecycle(
            "Whetstone dep-graph: ${records.size} contribution(s) -> ${File(dir, "dep-graph.html")}"
        )
    }
}
