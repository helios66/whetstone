package com.deliveryhero.whetstone.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class WhetstoneSymbolProcessorTest {

    /** Stub copies of the Whetstone framework types the processor matches by fully-qualified name. */
    private val framework = listOf(
        SourceFile.kotlin(
            "Framework.kt",
            """
            package com.deliveryhero.whetstone.meta
            import kotlin.reflect.KClass
            @Target(AnnotationTarget.ANNOTATION_CLASS)
            annotation class AutoInjectorBinding(val scope: KClass<*>)
            @Target(AnnotationTarget.ANNOTATION_CLASS)
            annotation class AutoInstanceBinding(val base: KClass<*>, val scope: KClass<*>)
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "ContributesInjector.kt",
            """
            package com.deliveryhero.whetstone.injector
            import kotlin.reflect.KClass
            annotation class ContributesInjector(val scope: KClass<*>)
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "App.kt",
            """
            package com.deliveryhero.whetstone.app
            class ApplicationScope private constructor()
            interface ApplicationComponent
            @OptIn(kotlin.ExperimentalStdlibApi::class)
            @com.deliveryhero.whetstone.meta.AutoInjectorBinding(ApplicationScope::class)
            annotation class ContributesAppInjector(val generateAppComponent: Boolean = false)
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Android.kt",
            """
            package android.app
            open class Application
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "Fixtures.kt",
            """
            package test
            import com.deliveryhero.whetstone.meta.AutoInjectorBinding
            import com.deliveryhero.whetstone.meta.AutoInstanceBinding

            class AScope private constructor()
            class BScope private constructor()
            open class BaseModel

            @AutoInstanceBinding(base = BaseModel::class, scope = AScope::class)
            annotation class ContributesModel

            @AutoInjectorBinding(scope = AScope::class)
            annotation class ContributesAInjector

            @AutoInjectorBinding(scope = BScope::class)
            annotation class ContributesBInjector
            """.trimIndent(),
        ),
    )

    private fun compile(vararg sources: SourceFile): Map<String, String> {
        val compilation = KotlinCompilation().apply {
            this.sources = framework + sources
            inheritClassPath = true
            messageOutputStream = System.out
            useKsp2()
            configureKsp {
                symbolProcessorProviders += WhetstoneSymbolProcessorProvider()
                withCompilation = true
            }
        }
        val result = compilation.compile()
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "Compilation (incl. generated sources) should succeed",
        )
        return compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to it.readText() }
    }

    @Test
    fun `instance binding contributes the type into the scope multibinding`() {
        val generated = compile(
            SourceFile.kotlin(
                "FooModel.kt",
                """
                package example
                import test.ContributesModel
                import test.BaseModel
                @ContributesModel
                class FooModel : BaseModel()
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("FooModel_WhetstoneModule.kt")
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("@ClassKey(FooModel::class)" in module, module)
        assertTrue("val FooModel.bindFooModel: BaseModel" in module, module)
    }

    @Test
    fun `injector binding contributes a MembersInjector into the scope`() {
        val generated = compile(
            SourceFile.kotlin(
                "FooScreen.kt",
                """
                package example
                import test.ContributesAInjector
                @ContributesAInjector
                class FooScreen
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("FooScreen_WhetstoneModule.kt")
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("@ClassKey(FooScreen::class)" in module, module)
        assertTrue("MembersInjector<FooScreen>" in module, module)
        assertTrue("MembersInjector<*>" in module, module)
    }

    @Test
    fun `explicit ContributesInjector contributes a MembersInjector`() {
        val generated = compile(
            SourceFile.kotlin(
                "Custom.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesInjector
                import test.AScope
                @ContributesInjector(AScope::class)
                class Custom
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("Custom_WhetstoneModule.kt")
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("MembersInjector<Custom>" in module, module)
    }

    /** Regression test: a class with two triggers must produce ONE file, not collide. */
    @Test
    fun `class with two triggers produces a single file with one interface per scope`() {
        val generated = compile(
            SourceFile.kotlin(
                "MultiTrigger.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesInjector
                import test.AScope
                import test.ContributesBInjector
                @ContributesInjector(AScope::class)
                @ContributesBInjector
                class MultiTrigger
                """.trimIndent(),
            ),
        )
        // Exactly one generated module file for the class — no FileAlreadyExistsException, no second file.
        val files = generated.keys.filter { it.startsWith("MultiTrigger") }
        assertEquals(listOf("MultiTrigger_WhetstoneModule.kt"), files, "expected exactly one file, got $files")

        val module = generated.getValue("MultiTrigger_WhetstoneModule.kt")
        // Two contributing interfaces, one per scope.
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("@ContributesTo(BScope::class)" in module, module)
        assertTrue("MembersInjector<MultiTrigger>" in module, module)
    }

    @Test
    fun `ContributesAppInjector with generateAppComponent emits a Metro DependencyGraph`() {
        val generated = compile(
            SourceFile.kotlin(
                "MyApp.kt",
                """
                package example
                import com.deliveryhero.whetstone.app.ContributesAppInjector
                import android.app.Application
                @ContributesAppInjector(generateAppComponent = true)
                class MyApp : Application()
                """.trimIndent(),
            ),
        )
        // Member-injector module for the app itself…
        assertTrue(generated.containsKey("MyApp_WhetstoneModule.kt"), generated.keys.toString())
        // …plus the generated root graph.
        val graph = generated.getValue("GeneratedApplicationComponent.kt")
        assertTrue("@DependencyGraph(scope = ApplicationScope::class)" in graph, graph)
        assertTrue(": ApplicationComponent" in graph, graph)
        assertTrue("public fun interface Factory" in graph, graph)
        assertTrue("application: Application" in graph, graph)
    }

    @Test
    fun `class without whetstone annotations generates nothing`() {
        val generated = compile(
            SourceFile.kotlin(
                "Plain.kt",
                """
                package example
                class Plain
                """.trimIndent(),
            ),
        )
        assertTrue(generated.none { it.key.startsWith("Plain") }, generated.keys.toString())
    }
}
