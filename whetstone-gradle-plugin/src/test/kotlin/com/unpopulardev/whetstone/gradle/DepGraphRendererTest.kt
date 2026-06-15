package com.unpopulardev.whetstone.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DepGraphRendererTest {

    private fun render(vararg fragments: String): String =
        DepGraphRenderer.render(
            DepGraphRenderer.normalize(fragments.flatMap { DepGraphRenderer.parse(it) })
        )

    @Test
    fun parse_reads_all_fields_including_optional_base() {
        val records = DepGraphRenderer.parse(
            """
            [
              {"fqName":"com.x.MainActivity","scope":"ActivityScope","kind":"injector"},
              {"fqName":"com.x.MainWorker","scope":"WorkerScope","kind":"instance","base":"ListenableWorker"}
            ]
            """.trimIndent()
        )
        assertEquals(2, records.size)
        assertEquals(null, records[0].base)
        assertEquals("ListenableWorker", records[1].base)
    }

    @Test
    fun instance_node_shows_base_type_label() {
        val mermaid = render(
            """[{"fqName":"com.x.MainWorker","scope":"WorkerScope","kind":"instance","base":"ListenableWorker"}]"""
        )
        assertTrue("MainWorker<br/>(ListenableWorker)" in mermaid, mermaid)
        assertTrue("subgraph WorkerScope" in mermaid, mermaid)
    }

    @Test
    fun draws_hierarchy_edges_only_between_present_scopes() {
        val mermaid = render(
            """[
              {"fqName":"com.x.App","scope":"ApplicationScope","kind":"injector"},
              {"fqName":"com.x.Act","scope":"ActivityScope","kind":"injector"},
              {"fqName":"com.x.Frag","scope":"FragmentScope","kind":"instance","base":"Fragment"}
            ]"""
        )
        assertTrue("ApplicationScope --> ActivityScope" in mermaid, mermaid)
        assertTrue("ActivityScope --> FragmentScope" in mermaid, mermaid)
        // ViewScope absent -> no edge to it
        assertFalse("--> ViewScope" in mermaid, mermaid)
        // ServiceScope absent -> no edge to it
        assertFalse("--> ServiceScope" in mermaid, mermaid)
    }

    @Test
    fun merges_and_dedupes_fragments_across_modules() {
        val app =
            """[{"fqName":"com.x.MainActivity","scope":"ActivityScope","kind":"injector"}]"""
        val lib =
            """[{"fqName":"com.lib.MyViewModel","scope":"ViewModelScope","kind":"instance","base":"ViewModel"}]"""
        // duplicate of the app record from a second variant fragment
        val dup =
            """[{"fqName":"com.x.MainActivity","scope":"ActivityScope","kind":"injector"}]"""
        val mermaid = render(app, lib, dup)
        assertEquals(1, Regex("n_com_x_MainActivity\\[").findAll(mermaid).count(), mermaid)
        assertTrue("subgraph ViewModelScope" in mermaid, mermaid)
        // ApplicationScope absent in this set -> no hierarchy edge to ViewModelScope
        assertFalse("--> ViewModelScope" in mermaid, mermaid)
    }

    @Test
    fun scopes_render_in_canonical_order() {
        val mermaid = render(
            """[
              {"fqName":"com.x.W","scope":"WorkerScope","kind":"instance","base":"ListenableWorker"},
              {"fqName":"com.x.A","scope":"ApplicationScope","kind":"injector"}
            ]"""
        )
        val appIdx = mermaid.indexOf("subgraph ApplicationScope")
        val workerIdx = mermaid.indexOf("subgraph WorkerScope")
        assertTrue(appIdx in 0 until workerIdx, "ApplicationScope should precede WorkerScope")
    }

    @Test
    fun empty_input_renders_placeholder_not_a_crash() {
        val mermaid = render("[]")
        assertTrue(mermaid.startsWith("flowchart TD"), mermaid)
        assertTrue("No Whetstone contributions found" in mermaid, mermaid)
    }
}
