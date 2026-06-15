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
            @Target(AnnotationTarget.CLASS)
            annotation class ContributesTo(val scope: KClass<*>, val replaces: Array<KClass<*>> = [])
            @Target(AnnotationTarget.CLASS)
            annotation class ContributesBinding(
                val scope: KClass<*>,
                val boundType: KClass<*> = Unit::class,
                val replaces: Array<KClass<*>> = [],
            )
            @Target(AnnotationTarget.CLASS)
            annotation class ContributesMultibinding(
                val scope: KClass<*>,
                val boundType: KClass<*> = Unit::class,
                val replaces: Array<KClass<*>> = [],
            )
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
                import dev.zacsweers.metro.Inject
                class Dep
                @ContributesAInjector
                class FooScreen {
                    @Inject lateinit var dep: Dep
                }
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
                import dev.zacsweers.metro.Inject
                class Dep
                @ContributesInjector(AScope::class)
                class Custom {
                    @Inject lateinit var dep: Dep
                }
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
                import dev.zacsweers.metro.Inject
                class Dep
                @ContributesInjector(AScope::class)
                @ContributesBInjector
                class MultiTrigger {
                    @Inject lateinit var dep: Dep
                }
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
    fun `ContributesAppInjector with generateAppComponent emits graph plus injector module when it has Inject members`() {
        val generated = compile(
            SourceFile.kotlin(
                "MyApp.kt",
                """
                package example
                import com.deliveryhero.whetstone.app.ContributesAppInjector
                import android.app.Application
                import dev.zacsweers.metro.Inject
                class Dep
                @ContributesAppInjector(generateAppComponent = true)
                class MyApp : Application() {
                    @Inject lateinit var dep: Dep
                }
                """.trimIndent(),
            ),
        )
        // Member-injector module for the app (it has an @Inject member)…
        assertTrue(generated.containsKey("MyApp_WhetstoneModule.kt"), generated.keys.toString())
        // …plus the generated root graph.
        val graph = generated.getValue("GeneratedApplicationComponent.kt")
        assertTrue("@DependencyGraph(scope = ApplicationScope::class)" in graph, graph)
        assertTrue(": ApplicationComponent" in graph, graph)
        assertTrue("public fun interface Factory" in graph, graph)
        assertTrue("application: Application" in graph, graph)
    }

    /**
     * Regression: an injector-style class with NO `@Inject` members must NOT get a `MembersInjector`
     * binding (Metro can't provide one), but `@ContributesAppInjector(generateAppComponent=true)`
     * must still emit the graph.
     */
    @Test
    fun `injector class without Inject members emits no member-injector binding`() {
        val generated = compile(
            SourceFile.kotlin(
                "BareApp.kt",
                """
                package example
                import com.deliveryhero.whetstone.app.ContributesAppInjector
                import android.app.Application
                @ContributesAppInjector(generateAppComponent = true)
                class BareApp : Application()
                """.trimIndent(),
            ),
        )
        // No injector module — there is nothing to inject.
        assertTrue(
            generated.none { it.key.startsWith("BareApp_WhetstoneModule") },
            "no injector module expected; got ${generated.keys}",
        )
        // But the root graph is still generated.
        assertTrue(generated.containsKey("GeneratedApplicationComponent.kt"), generated.keys.toString())
    }

    @Test
    fun `injector ContributesBinding generates a Binds module bound to boundType`() {
        val generated = compile(
            SourceFile.kotlin(
                "Binding.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesBinding
                import test.AScope
                interface Api
                @ContributesBinding(AScope::class, boundType = Api::class)
                class RealApi : Api
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("RealApi_WhetstoneContribution.kt")
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("@Binds" in module, module)
        assertTrue("val RealApi.bindRealApi: Api" in module, module)
    }

    @Test
    fun `injector ContributesBinding infers boundType from the single supertype`() {
        val generated = compile(
            SourceFile.kotlin(
                "Inferred.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesBinding
                import test.AScope
                interface Greeter
                @ContributesBinding(AScope::class)
                class RealGreeter : Greeter
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("RealGreeter_WhetstoneContribution.kt")
        assertTrue("val RealGreeter.bindRealGreeter: Greeter" in module, module)
    }

    @Test
    fun `injector ContributesBinding replaces maps to the replaced class generated module`() {
        val generated = compile(
            SourceFile.kotlin(
                "Replaces.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesBinding
                import test.AScope
                interface Api
                @ContributesBinding(AScope::class, boundType = Api::class)
                class DefaultApi : Api
                @ContributesBinding(AScope::class, boundType = Api::class, replaces = [DefaultApi::class])
                class FakeApi : Api
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("FakeApi_WhetstoneContribution.kt")
        assertTrue("replaces = [DefaultApi_WhetstoneContribution::class]" in module, module)
    }

    @Test
    fun `injector ContributesMultibinding without a map-key generates an IntoSet binding`() {
        val generated = compile(
            SourceFile.kotlin(
                "SetElement.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesMultibinding
                import test.AScope
                interface Interceptor
                @ContributesMultibinding(AScope::class)
                class LoggingInterceptor : Interceptor
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("LoggingInterceptor_WhetstoneContribution.kt")
        assertTrue("@IntoSet" in module, module)
        assertTrue("@IntoMap" !in module, module)
        assertTrue("val LoggingInterceptor.bindLoggingInterceptor: Interceptor" in module, module)
    }

    @Test
    fun `injector ContributesMultibinding with a custom map-key generates an IntoMap binding carrying the key`() {
        val generated = compile(
            SourceFile.kotlin(
                "MapElement.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesMultibinding
                import dev.zacsweers.metro.MapKey
                import test.AScope
                @MapKey
                annotation class DestinationKey(val value: String)
                interface Destination
                @ContributesMultibinding(AScope::class)
                @DestinationKey("home")
                class HomeDestination : Destination
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("HomeDestination_WhetstoneContribution.kt")
        assertTrue("@IntoMap" in module, module)
        assertTrue("@IntoSet" !in module, module)
        assertTrue("DestinationKey" in module, module)
        assertTrue("val HomeDestination.bindHomeDestination: Destination" in module, module)
    }

    @Test
    fun `injector ContributesTo aggregates the annotated interface into the scope`() {
        val generated = compile(
            SourceFile.kotlin(
                "Aggregating.kt",
                """
                package example
                import com.deliveryhero.whetstone.injector.ContributesTo
                import test.AScope
                @ContributesTo(AScope::class)
                interface FeatureAccessors
                """.trimIndent(),
            ),
        )
        val module = generated.getValue("FeatureAccessors_WhetstoneContribution.kt")
        assertTrue("@ContributesTo(AScope::class)" in module, module)
        assertTrue("interface FeatureAccessors_WhetstoneContribution : FeatureAccessors" in module, module)
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
